package com.tabula.v3.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.AlbumActionType
import com.tabula.v3.data.model.AlbumMapping
import com.tabula.v3.data.model.PendingAlbumAction
import com.tabula.v3.data.model.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 相册管理器
 *
 * 负责相册的 CRUD 操作、图片归类管理、以及撤销功能。
 * 数据持久化到本地 JSON 文件。
 */
class AlbumManager(private val context: Context) {

    companion object {
        private const val TAG = "AlbumManager"
        private const val ALBUMS_FILE = "albums.json"
        private const val MAPPINGS_FILE = "album_mappings.json"
        private const val MAX_UNDO_ACTIONS = 10

        @Volatile
        private var instance: AlbumManager? = null

        fun getInstance(context: Context): AlbumManager {
            return instance ?: synchronized(this) {
                instance ?: AlbumManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val albumsFile: File
        get() = File(context.filesDir, ALBUMS_FILE)

    private val mappingsFile: File
        get() = File(context.filesDir, MAPPINGS_FILE)

    // 内存缓存
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _mappings = MutableStateFlow<List<AlbumMapping>>(emptyList())
    val mappings: StateFlow<List<AlbumMapping>> = _mappings.asStateFlow()

    // 撤销队列
    private val undoQueue = mutableListOf<PendingAlbumAction>()
    
    // 延迟同步相关
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingSyncAlbums = mutableSetOf<String>()  // 待同步的图集 ID
    private var syncJob: Job? = null
    private val syncDelayMs = 2000L  // 延迟 2 秒同步

    // ========== 相册 CRUD ==========

    /**
     * 创建新相册
     * 自动在系统相册中创建对应的相册文件夹
     */
    suspend fun createAlbum(
        name: String,
        color: Long? = null,
        emoji: String? = null
    ): Album = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val maxOrder = currentAlbums.maxOfOrNull { it.order } ?: 0

        // 在系统相册中创建对应的文件夹
        val systemPath = try {
            syncManager.createSystemAlbum(name)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create system album for $name", e)
            null
        }

        val newAlbum = Album(
            name = name,
            color = color,
            emoji = emoji,
            order = maxOrder + 1,
            isSyncEnabled = true,  // 默认开启自动同步
            systemAlbumPath = systemPath
        )

        val updatedAlbums = currentAlbums + newAlbum
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums

        Log.d(TAG, "Created album '$name' with system path: $systemPath")
        newAlbum
    }

    /**
     * 更新相册信息
     */
    suspend fun updateAlbum(album: Album) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.map {
            if (it.id == album.id) album else it
        }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums
    }

    /**
     * 删除相册（同时清理相关映射）
     */
    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        // 删除相册
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.filter { it.id != albumId }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums

        // 清理映射关系
        val currentMappings = loadMappings()
        val updatedMappings = currentMappings.mapNotNull { mapping ->
            val newAlbumIds = mapping.albumIds.filter { it != albumId }
            if (newAlbumIds.isEmpty()) null
            else mapping.copy(albumIds = newAlbumIds)
        }
        saveMappings(updatedMappings)
        _mappings.value = updatedMappings
    }

    /**
     * 获取所有相册（按 order 排序）
     */
    suspend fun getAllAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = loadAlbums().sortedBy { it.order }
        _albums.value = albums
        albums
    }

    /**
     * 重新排序相册
     */
    suspend fun reorderAlbums(albumIds: List<String>) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.map { album ->
            val newOrder = albumIds.indexOf(album.id)
            if (newOrder >= 0) album.copy(order = newOrder)
            else album
        }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums.sortedBy { it.order }
    }

    // ========== 图片归类 ==========

    /**
     * 将图片添加到相册
     */
    suspend fun addImageToAlbum(
        imageId: Long,
        imageUri: String,
        albumId: String,
        recordUndo: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val currentMappings = loadMappings()
        val existingMapping = currentMappings.find { it.imageId == imageId }

        val updatedMappings = if (existingMapping != null) {
            if (existingMapping.albumIds.contains(albumId)) {
                // 已经在该相册中
                currentMappings
            } else {
                // 添加到新相册
                currentMappings.map {
                    if (it.imageId == imageId) {
                        it.copy(
                            albumIds = it.albumIds + albumId,
                            addedAt = System.currentTimeMillis()
                        )
                    } else it
                }
            }
        } else {
            // 新建映射，同时保存原图位置
            currentMappings + AlbumMapping(
                imageId = imageId,
                imageUri = imageUri,
                originalUri = imageUri,  // 初始时 originalUri 等于 imageUri
                albumIds = listOf(albumId)
            )
        }

        saveMappings(updatedMappings)
        _mappings.value = updatedMappings

        // 更新相册图片数量和封面
        updateAlbumStats(albumId)

        // 延迟自动同步到系统相册
        scheduleSyncForAlbum(albumId)

        // 记录撤销操作
        if (recordUndo) {
            addUndoAction(
                PendingAlbumAction(
                    id = UUID.randomUUID().toString(),
                    type = AlbumActionType.ADD,
                    imageId = imageId,
                    albumId = albumId
                )
            )
        }
    }

    /**
     * 从相册移除图片
     */
    suspend fun removeImageFromAlbum(
        imageId: Long,
        albumId: String,
        recordUndo: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val currentMappings = loadMappings()
        val updatedMappings = currentMappings.mapNotNull { mapping ->
            if (mapping.imageId == imageId) {
                val newAlbumIds = mapping.albumIds.filter { it != albumId }
                if (newAlbumIds.isEmpty()) null
                else mapping.copy(albumIds = newAlbumIds)
            } else mapping
        }

        saveMappings(updatedMappings)
        _mappings.value = updatedMappings

        // 更新相册统计
        updateAlbumStats(albumId)

        // 记录撤销操作
        if (recordUndo) {
            addUndoAction(
                PendingAlbumAction(
                    id = UUID.randomUUID().toString(),
                    type = AlbumActionType.REMOVE,
                    imageId = imageId,
                    albumId = albumId
                )
            )
        }
    }

    /**
     * 获取图片所属的相册列表
     */
    suspend fun getAlbumsForImage(imageId: Long): List<Album> = withContext(Dispatchers.IO) {
        val mapping = loadMappings().find { it.imageId == imageId }
        if (mapping == null) return@withContext emptyList()

        val allAlbums = loadAlbums()
        allAlbums.filter { it.id in mapping.albumIds }
    }

    /**
     * 获取相册内的所有图片 ID
     */
    suspend fun getImageIdsForAlbum(albumId: String): List<Long> = withContext(Dispatchers.IO) {
        loadMappings()
            .filter { mapping -> albumId in mapping.albumIds }
            .map { it.imageId }
    }

    /**
     * 获取相册内的所有图片映射信息
     * 返回 imageId 和 imageUri 的配对列表，用于图片加载
     */
    suspend fun getImageMappingsForAlbum(albumId: String): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        loadMappings()
            .filter { mapping -> albumId in mapping.albumIds }
            .map { Pair(it.imageId, it.imageUri) }
    }

    /**
     * 获取图片所属的相册 ID 集合
     */
    suspend fun getAlbumIdsForImage(imageId: Long): Set<String> = withContext(Dispatchers.IO) {
        loadMappings()
            .find { it.imageId == imageId }
            ?.albumIds
            ?.toSet()
            ?: emptySet()
    }

    /**
     * 根据图片 ID 列表获取对应的当前 URI 列表
     */
    suspend fun getImageUrisForIds(imageIds: List<Long>): List<Uri> = withContext(Dispatchers.IO) {
        val mappings = loadMappings()
        imageIds.mapNotNull { imageId ->
            mappings.find { it.imageId == imageId }?.imageUri?.let { Uri.parse(it) }
        }
    }

    /**
     * 检查 URI 是否仍然有效（文件存在）
     */
    private fun isUriValid(uri: Uri, contentResolver: android.content.ContentResolver): Boolean {
        return try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.Images.Media._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取图集中可清理的旧图 URI 列表
     * 
     * 分桶返回：
     * - sourceUris: 来自 originalUri（最初原图位置）
     * - orphanUris: 来自 cleanupUris（图集间移动产生的中间残留）
     * 
     * 只返回文件仍然存在且不被任何 imageUri 引用的 URI。
     * 
     * @param albumId 图集 ID
     * @return CleanableUrisResult 分桶结果
     */
    suspend fun getCleanableUrisForAlbum(albumId: String): CleanableUrisResult = withContext(Dispatchers.IO) {
        val mappings = loadMappings()
        val contentResolver = context.contentResolver
        
        // 获取该图集的映射
        val albumMappings = mappings.filter { albumId in it.albumIds }
        
        // 收集 originalUri（去重）
        val allOriginalUris = albumMappings
            .mapNotNull { it.originalUri }
            .distinct()
        
        // 收集 cleanupUris（去重）
        val allCleanupUris = albumMappings
            .flatMap { it.cleanupUris }
            .distinct()
        
        // 过滤：文件存在 且 不被任何 imageUri 引用
        val validSourceUris = allOriginalUris
            .filter { uri -> 
                !isUriReferencedAsImageUri(uri, mappings) && isUriValid(Uri.parse(uri), contentResolver)
            }
            .map { Uri.parse(it) }
        
        val validOrphanUris = allCleanupUris
            .filter { uri -> 
                !isUriReferencedAsImageUri(uri, mappings) && isUriValid(Uri.parse(uri), contentResolver)
            }
            .map { Uri.parse(it) }
        
        Log.d(TAG, "getCleanableUrisForAlbum($albumId): source=${validSourceUris.size}/${allOriginalUris.size}, orphan=${validOrphanUris.size}/${allCleanupUris.size}")
        
        CleanableUrisResult(
            sourceUris = validSourceUris,
            orphanUris = validOrphanUris,
            sourceTotalCount = allOriginalUris.size,
            orphanTotalCount = allCleanupUris.size
        )
    }

    /**
     * 获取所有图集中可清理的旧图 URI 列表
     * 
     * 分桶返回：
     * - sourceUris: 来自 originalUri（最初原图位置）
     * - orphanUris: 来自 cleanupUris（图集间移动产生的中间残留）
     * 
     * 只返回文件仍然存在且不被任何 imageUri 引用的 URI（全局去重）。
     * 
     * @return CleanableUrisResult 分桶结果
     */
    suspend fun getCleanableUrisForAllAlbums(): CleanableUrisResult = withContext(Dispatchers.IO) {
        val mappings = loadMappings()
        val contentResolver = context.contentResolver
        
        // 只处理有 albumIds 的映射
        val activeMappings = mappings.filter { it.albumIds.isNotEmpty() }
        
        // 收集所有 originalUri（全局去重）
        val allOriginalUris = activeMappings
            .mapNotNull { it.originalUri }
            .distinct()
        
        // 收集所有 cleanupUris（全局去重）
        val allCleanupUris = activeMappings
            .flatMap { it.cleanupUris }
            .distinct()
        
        // 过滤：文件存在 且 不被任何 imageUri 引用
        val validSourceUris = allOriginalUris
            .filter { uri -> 
                !isUriReferencedAsImageUri(uri, mappings) && isUriValid(Uri.parse(uri), contentResolver)
            }
            .map { Uri.parse(it) }
        
        val validOrphanUris = allCleanupUris
            .filter { uri -> 
                !isUriReferencedAsImageUri(uri, mappings) && isUriValid(Uri.parse(uri), contentResolver)
            }
            .map { Uri.parse(it) }
        
        Log.d(TAG, "getCleanableUrisForAllAlbums: source=${validSourceUris.size}/${allOriginalUris.size}, orphan=${validOrphanUris.size}/${allCleanupUris.size}")
        
        CleanableUrisResult(
            sourceUris = validSourceUris,
            orphanUris = validOrphanUris,
            sourceTotalCount = allOriginalUris.size,
            orphanTotalCount = allCleanupUris.size
        )
    }

    /**
     * 检查 URI 是否被任何 mapping 的 imageUri 引用
     * 
     * 只有不被 imageUri 引用的 URI 才可以安全删除。
     * originalUri 和 cleanupUris 的引用不阻止删除（它们本身就是待清理目标）。
     * 
     * @param uri 要检查的 URI 字符串
     * @param mappings 当前所有 mapping（避免重复加载）
     * @return 如果被任何 imageUri 引用则返回 true
     */
    private fun isUriReferencedAsImageUri(uri: String, mappings: List<AlbumMapping>): Boolean {
        val normalizedUri = Uri.parse(uri).toString()
        return mappings.any { Uri.parse(it.imageUri).toString() == normalizedUri }
    }

    /**
     * 清除已成功删除的旧图 URI
     * 
     * 遍历所有 mapping，同时清理：
     * - 匹配的 originalUri 置为 null
     * - 匹配的 cleanupUris 中移除对应条目
     * 
     * 重要：使用规范化后的 URI 字符串进行匹配，避免字符串表示差异。
     * 
     * @param deletedUris 成功删除的 URI 字符串集合
     */
    suspend fun clearDeletedUris(deletedUris: Set<String>) = withContext(Dispatchers.IO) {
        if (deletedUris.isEmpty()) {
            Log.d(TAG, "No URIs to clear")
            return@withContext
        }
        
        // 规范化 deletedUris（统一使用 Uri.parse().toString() 格式）
        val normalizedDeletedUris = deletedUris.map { Uri.parse(it).toString() }.toSet()
        
        val currentMappings = loadMappings()
        var clearedOriginalCount = 0
        var clearedCleanupCount = 0
        
        val updatedMappings = currentMappings.map { mapping ->
            var updated = mapping
            
            // 清理 originalUri
            if (mapping.originalUri != null) {
                val normalizedOriginalUri = Uri.parse(mapping.originalUri).toString()
                if (normalizedOriginalUri in normalizedDeletedUris) {
                    clearedOriginalCount++
                    updated = updated.copy(originalUri = null)
                }
            }
            
            // 清理 cleanupUris
            if (mapping.cleanupUris.isNotEmpty()) {
                val remainingCleanupUris = mapping.cleanupUris.filter { uri ->
                    val normalized = Uri.parse(uri).toString()
                    normalized !in normalizedDeletedUris
                }.toSet()
                
                val removedCount = mapping.cleanupUris.size - remainingCleanupUris.size
                if (removedCount > 0) {
                    clearedCleanupCount += removedCount
                    updated = updated.copy(cleanupUris = remainingCleanupUris)
                }
            }
            
            updated
        }
        
        if (clearedOriginalCount > 0 || clearedCleanupCount > 0) {
            saveMappings(updatedMappings)
            _mappings.value = updatedMappings
            Log.d(TAG, "Cleared URIs: $clearedOriginalCount from originalUri, $clearedCleanupCount from cleanupUris")
        } else {
            Log.d(TAG, "No matching URIs found to clear")
        }
    }
    
    /**
     * @deprecated 使用 clearDeletedUris 代替
     */
    suspend fun clearOriginalUris(deletedUris: Set<String>) = clearDeletedUris(deletedUris)

    /**
     * 准备图集清理数据（在删除前调用）
     * 
     * 必须在删除原图之前调用，因为需要通过原 URI 查询文件名来找到新 URI。
     * 
     * @param albumId 图集 ID
     * @return 新旧 URI 映射 Map<原URI字符串, 新URI>
     */
    suspend fun prepareCleanupUriMapping(albumId: String): Map<String, Uri> = withContext(Dispatchers.IO) {
        val album = loadAlbums().find { it.id == albumId }
        if (album == null) {
            Log.w(TAG, "Album not found for URI mapping: $albumId")
            return@withContext emptyMap()
        }
        
        val currentMappings = loadMappings()
        val albumMappings = currentMappings.filter { albumId in it.albumIds }
        
        if (albumMappings.isEmpty()) {
            Log.d(TAG, "No mappings to prepare for album: ${album.name}")
            return@withContext emptyMap()
        }
        
        // 在删除前获取新 URI 映射
        val originalUris = albumMappings.map { Uri.parse(it.imageUri) }
        val newUriMap = syncManager.findNewUrisInSystemAlbum(originalUris, album.name)
        
        Log.d(TAG, "Prepared ${newUriMap.size}/${originalUris.size} URI mappings for album: ${album.name}")
        newUriMap
    }

    /**
     * 准备所有图集的清理数据（在删除前调用）
     * 
     * @return 新旧 URI 映射 Map<原URI字符串, 新URI>
     */
    suspend fun prepareAllCleanupUriMapping(): Map<String, Uri> = withContext(Dispatchers.IO) {
        val allAlbums = loadAlbums()
        val combinedMap = mutableMapOf<String, Uri>()
        
        for (album in allAlbums) {
            val currentMappings = loadMappings()
            val albumMappings = currentMappings.filter { album.id in it.albumIds }
            
            if (albumMappings.isEmpty()) continue
            
            val originalUris = albumMappings.map { Uri.parse(it.imageUri) }
            val newUriMap = syncManager.findNewUrisInSystemAlbum(originalUris, album.name)
            combinedMap.putAll(newUriMap)
        }
        
        Log.d(TAG, "Prepared ${combinedMap.size} URI mappings for all albums")
        combinedMap
    }

    /**
     * 应用预计算的 URI 映射（在删除后调用）
     * 
     * 同时更新：
     * 1. AlbumMapping 中的 imageUri 和 imageId
     * 2. Album 中的 coverImageId（如果封面图片被更新）
     * 
     * @param uriMapping 预计算的新旧 URI 映射
     */
    suspend fun applyUriMapping(uriMapping: Map<String, Uri>) = withContext(Dispatchers.IO) {
        if (uriMapping.isEmpty()) {
            Log.d(TAG, "No URI mappings to apply")
            return@withContext
        }
        
        val currentMappings = loadMappings()
        
        // 建立 旧imageId -> 新imageId 的映射（用于更新封面）
        val oldToNewIdMap = mutableMapOf<Long, Long>()
        
        val updatedMappings = currentMappings.map { mapping ->
            val newUri = uriMapping[mapping.imageUri]
            if (newUri != null) {
                // 从新 URI 中提取新的 imageId
                val newId = newUri.lastPathSegment?.toLongOrNull() ?: mapping.imageId
                oldToNewIdMap[mapping.imageId] = newId
                // 只更新 imageUri 和 imageId，保留 originalUri 不变
                // originalUri 用于清理旧图时定位最初原图文件
                mapping.copy(
                    imageId = newId,
                    imageUri = newUri.toString()
                )
            } else {
                mapping
            }
        }
        
        saveMappings(updatedMappings)
        _mappings.value = updatedMappings
        
        // 更新图集封面 ID
        if (oldToNewIdMap.isNotEmpty()) {
            val currentAlbums = loadAlbums()
            var albumsUpdated = 0
            val updatedAlbums = currentAlbums.map { album ->
                val oldCoverId = album.coverImageId
                if (oldCoverId != null && oldToNewIdMap.containsKey(oldCoverId)) {
                    val newCoverId = oldToNewIdMap[oldCoverId]
                    albumsUpdated++
                    Log.d(TAG, "Updated cover for album '${album.name}': $oldCoverId -> $newCoverId")
                    album.copy(coverImageId = newCoverId)
                } else {
                    album
                }
            }
            
            if (albumsUpdated > 0) {
                saveAlbums(updatedAlbums)
                _albums.value = updatedAlbums
                Log.d(TAG, "Updated $albumsUpdated album covers")
            }
        }
        
        Log.d(TAG, "Applied ${uriMapping.size} URI updates, ${oldToNewIdMap.size} ID mappings")
    }

    /**
     * 批量将图片移动到另一个图集
     * 
     * 会同时从当前图集移除并添加到目标图集。
     * 物理上将图片复制到目标图集的系统文件夹，旧位置加入 cleanupUris 待用户清理。
     */
    suspend fun moveImagesToAlbum(
        imageIds: List<Long>,
        fromAlbumId: String,
        toAlbumId: String
    ) = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) return@withContext
        
        val targetAlbum = loadAlbums().find { it.id == toAlbumId }
        if (targetAlbum == null) {
            Log.e(TAG, "Target album not found: $toAlbumId")
            return@withContext
        }
        
        // 确保目标图集的系统文件夹存在
        syncManager.createSystemAlbum(targetAlbum.name)
        
        val currentMappings = loadMappings()
        val updatedMappings = currentMappings.map { mapping ->
            if (mapping.imageId in imageIds) {
                val oldImageUri = mapping.imageUri
                
                // 复制图片到目标图集的系统文件夹
                val newUri = syncManager.addImageToSystemAlbum(
                    Uri.parse(oldImageUri),
                    targetAlbum.name
                )
                
                if (newUri != null) {
                    // 从新 URI 中提取新的 imageId
                    val newImageId = newUri.lastPathSegment?.toLongOrNull() ?: mapping.imageId
                    // 从源图集移除，添加到目标图集
                    val newAlbumIds = (mapping.albumIds - fromAlbumId + toAlbumId).distinct()
                    // 旧位置加入 cleanupUris
                    val newCleanupUris = mapping.cleanupUris + oldImageUri
                    
                    mapping.copy(
                        imageId = newImageId,
                        imageUri = newUri.toString(),
                        cleanupUris = newCleanupUris,
                        albumIds = newAlbumIds,
                        addedAt = System.currentTimeMillis()
                    )
                } else {
                    // 复制失败，仅更新 albumIds（保持原位置）
                    Log.w(TAG, "Failed to copy image to target album, only updating albumIds")
                    val newAlbumIds = (mapping.albumIds - fromAlbumId + toAlbumId).distinct()
                    mapping.copy(
                        albumIds = newAlbumIds,
                        addedAt = System.currentTimeMillis()
                    )
                }
            } else {
                mapping
            }
        }
        
        saveMappings(updatedMappings)
        _mappings.value = updatedMappings
        
        // 更新两个图集的统计信息
        updateAlbumStats(fromAlbumId)
        updateAlbumStats(toAlbumId)
        
        Log.d(TAG, "Moved ${imageIds.size} images from $fromAlbumId to $toAlbumId")
    }
    
    /**
     * 批量将图片复制到另一个图集
     * 
     * 不会从当前图集移除，为目标图集创建独立的 mapping。
     * 物理上将图片复制到目标图集的系统文件夹。
     * 新 mapping 继承源 mapping 的 originalUri（最初原图位置）。
     */
    suspend fun copyImagesToAlbum(
        imageIds: List<Long>,
        toAlbumId: String
    ) = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) return@withContext
        
        val targetAlbum = loadAlbums().find { it.id == toAlbumId }
        if (targetAlbum == null) {
            Log.e(TAG, "Target album not found: $toAlbumId")
            return@withContext
        }
        
        // 确保目标图集的系统文件夹存在
        syncManager.createSystemAlbum(targetAlbum.name)
        
        val currentMappings = loadMappings()
        val newMappings = mutableListOf<AlbumMapping>()
        
        // 找出要复制的源 mapping
        val sourceMappings = currentMappings.filter { it.imageId in imageIds }
        
        for (sourceMapping in sourceMappings) {
            // 复制图片到目标图集的系统文件夹
            val newUri = syncManager.addImageToSystemAlbum(
                Uri.parse(sourceMapping.imageUri),
                targetAlbum.name
            )
            
            if (newUri != null) {
                // 从新 URI 中提取新的 imageId
                val newImageId = newUri.lastPathSegment?.toLongOrNull() ?: sourceMapping.imageId
                
                // 创建新的独立 mapping（目标图集专属）
                // 继承 originalUri（最初原图位置）
                val newMapping = AlbumMapping(
                    imageId = newImageId,
                    imageUri = newUri.toString(),
                    originalUri = sourceMapping.originalUri,  // 继承最初原图位置
                    cleanupUris = emptySet(),
                    albumIds = listOf(toAlbumId),
                    addedAt = System.currentTimeMillis()
                )
                newMappings.add(newMapping)
                Log.d(TAG, "Created new mapping for album $toAlbumId: imageId=$newImageId")
            } else {
                Log.w(TAG, "Failed to copy image ${sourceMapping.imageId} to target album")
            }
        }
        
        // 添加新创建的 mapping（不修改源 mapping）
        val updatedMappings = currentMappings + newMappings
        
        saveMappings(updatedMappings)
        _mappings.value = updatedMappings
        
        // 更新目标图集的统计信息
        updateAlbumStats(toAlbumId)
        
        Log.d(TAG, "Copied ${newMappings.size}/${imageIds.size} images to $toAlbumId")
    }

    // ========== 撤销功能 ==========

    /**
     * 撤销上一个操作
     */
    suspend fun undoLastAction(): PendingAlbumAction? = withContext(Dispatchers.IO) {
        if (undoQueue.isEmpty()) return@withContext null

        val lastAction = undoQueue.removeAt(undoQueue.lastIndex)

        when (lastAction.type) {
            AlbumActionType.ADD -> {
                // 撤销添加 = 移除
                val mapping = loadMappings().find { it.imageId == lastAction.imageId }
                if (mapping != null) {
                    removeImageFromAlbum(lastAction.imageId, lastAction.albumId, recordUndo = false)
                }
            }
            AlbumActionType.REMOVE -> {
                // 撤销移除 = 添加回去
                val mapping = loadMappings().find { it.imageId == lastAction.imageId }
                val uri = mapping?.imageUri ?: "content://media/external/images/media/${lastAction.imageId}"
                addImageToAlbum(lastAction.imageId, uri, lastAction.albumId, recordUndo = false)
            }
            else -> { /* 批量操作暂不支持撤销 */ }
        }

        lastAction
    }

    /**
     * 获取最后一个可撤销的操作
     */
    fun getLastAction(): PendingAlbumAction? = undoQueue.lastOrNull()

    /**
     * 清空撤销队列
     */
    fun clearUndoQueue() {
        undoQueue.clear()
    }

    private fun addUndoAction(action: PendingAlbumAction) {
        undoQueue.add(action)
        // 限制队列长度
        while (undoQueue.size > MAX_UNDO_ACTIONS) {
            undoQueue.removeAt(0)
        }
    }

    // ========== 回收站协同 ==========

    /**
     * 清理已永久删除图片的映射关系
     */
    suspend fun cleanupMappingsForDeletedImages(imageIds: List<Long>) = withContext(Dispatchers.IO) {
        val currentMappings = loadMappings()
        val idsToRemove = imageIds.toSet()
        val updatedMappings = currentMappings.filter { it.imageId !in idsToRemove }

        if (updatedMappings.size != currentMappings.size) {
            saveMappings(updatedMappings)
            _mappings.value = updatedMappings

            // 更新所有相册的统计
            loadAlbums().forEach { album ->
                updateAlbumStats(album.id)
            }
        }
    }

    // ========== 系统相册同步 ==========

    private val syncManager: SystemAlbumSyncManager by lazy {
        SystemAlbumSyncManager.getInstance(context)
    }

    /**
     * 切换相册的系统同步状态
     */
    suspend fun toggleSyncEnabled(albumId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val album = currentAlbums.find { it.id == albumId } ?: return@withContext

        if (enabled) {
            // 启用同步：创建系统相册文件夹
            val systemPath = syncManager.createSystemAlbum(album.name)
            val updatedAlbum = album.copy(
                isSyncEnabled = true,
                systemAlbumPath = systemPath
            )
            updateAlbum(updatedAlbum)

            // 同步现有图片
            if (systemPath != null) {
                syncAlbumImagesToSystem(album.id)
            }
        } else {
            // 禁用同步
            val updatedAlbum = album.copy(
                isSyncEnabled = false
            )
            updateAlbum(updatedAlbum)
            // 注意：不删除系统相册中已复制的图片
        }
    }

    /**
     * 修改相册的同步模式
     */
    suspend fun changeSyncMode(albumId: String, mode: SyncMode) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val album = currentAlbums.find { it.id == albumId } ?: return@withContext

        val updatedAlbum = album.copy(syncMode = mode)
        updateAlbum(updatedAlbum)
        
        Log.d(TAG, "Changed sync mode for album '${album.name}' to $mode")
    }

    /**
     * 安排延迟同步图集
     * 使用防抖动机制，在 2 秒内的多次调用只会触发一次同步
     */
    private fun scheduleSyncForAlbum(albumId: String) {
        synchronized(pendingSyncAlbums) {
            pendingSyncAlbums.add(albumId)
        }
        
        // 取消之前的同步任务，重新开始计时
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(syncDelayMs)
            
            // 获取并清空待同步列表
            val albumsToSync: Set<String>
            synchronized(pendingSyncAlbums) {
                albumsToSync = pendingSyncAlbums.toSet()
                pendingSyncAlbums.clear()
            }
            
            // 执行同步
            for (id in albumsToSync) {
                try {
                    syncAlbumImagesToSystem(id)
                    Log.d(TAG, "Auto-synced album $id to system")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-sync album $id", e)
                }
            }
        }
    }

    /**
     * 同步相册中的图片到系统相册
     * 
     * 同步完成后会自动更新 AlbumMapping 中的 imageId 和 imageUri，
     * 确保它们指向系统相册中的新图片。
     */
    suspend fun syncAlbumImagesToSystem(
        albumId: String,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val album = loadAlbums().find { it.id == albumId } ?: return@withContext 0
        // 不再检查 isSyncEnabled，因为所有图集都自动同步
        // if (!album.isSyncEnabled) return@withContext 0

        val imageIds = getImageIdsForAlbum(albumId)
        val mappings = loadMappings()

        val imageUris = imageIds.mapNotNull { imageId ->
            mappings.find { it.imageId == imageId }?.imageUri?.let { Uri.parse(it) }
        }

        if (imageUris.isEmpty()) return@withContext 0

        Log.d(TAG, "Syncing ${imageUris.size} images to system album: ${album.name} (mode: ${album.syncMode})")
        val result = syncManager.syncAlbumToSystem(album.name, imageUris, album.syncMode, onProgress)
        
        // 同步完成后，更新 AlbumMapping 中的 imageId 和 imageUri
        if (result.uriMapping.isNotEmpty()) {
            Log.d(TAG, "Updating ${result.uriMapping.size} mappings after sync for album: ${album.name}")
            applyUriMapping(result.uriMapping)
        }
        
        result.newlySynced  // 返回新同步的图片数量
    }

    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val successCount: Int,
        val totalAlbums: Int,
        val newlySyncedImages: Int,    // 新同步的图片总数
        val skippedImages: Int,         // 已存在跳过的图片总数
        val albumResults: List<AlbumSyncResult>
    ) {
        // 兼容旧的 syncedImages 属性
        val syncedImages get() = newlySyncedImages
    }

    data class AlbumSyncResult(
        val albumName: String,
        val newlySynced: Int,    // 新同步的图片数
        val skipped: Int,        // 已存在跳过的图片数
        val success: Boolean
    ) {
        // 兼容旧的 imagesSynced 属性
        val imagesSynced get() = newlySynced
    }

    /**
     * 可清理的旧图 URI 结果
     * 
     * 分桶返回，便于 UI 展示和日志排错：
     * - sourceUris: 来自 originalUri（最初原图位置）
     * - orphanUris: 来自 cleanupUris（图集间移动产生的中间残留）
     */
    data class CleanableUrisResult(
        val sourceUris: List<Uri>,      // 来自 originalUri
        val orphanUris: List<Uri>,      // 来自 cleanupUris
        val sourceTotalCount: Int,      // 有 originalUri 的 mapping 总数
        val orphanTotalCount: Int       // 有 cleanupUris 的 mapping 总数
    ) {
        val allUris: List<Uri> get() = sourceUris + orphanUris
        val isEmpty: Boolean get() = sourceUris.isEmpty() && orphanUris.isEmpty()
        val totalValidCount: Int get() = sourceUris.size + orphanUris.size
    }

    /**
     * 同步所有有图片的图集到系统相册
     * 注意：现在同步所有有图片的图集，不再依赖 isSyncEnabled 标志
     * isSyncEnabled 标志改为控制"实时自动同步"（添加图片时自动同步）
     * 
     * @param onProgress 进度回调，参数为 (当前图集索引, 总图集数, 当前图集已同步图片数, 当前图集总图片数)
     * @return 同步结果
     */
    suspend fun syncAllEnabledAlbumsToSystem(
        onProgress: ((albumIndex: Int, totalAlbums: Int, currentImages: Int, totalImages: Int) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val allAlbums = loadAlbums()
        // 同步所有有图片的图集（不再限制 isSyncEnabled）
        val albumsWithImages = allAlbums.filter { it.imageCount > 0 }
        
        // #region agent log
        Log.d("DEBUG_SYNC", "[A1,A2,B1] syncAllEnabledAlbumsToSystem:entry | albumsWithImagesCount=${albumsWithImages.size} | albumNames=${albumsWithImages.map { it.name }} | albumCoverIds=${albumsWithImages.map { "${it.name}:${it.coverImageId}" }}")
        // #endregion
        
        if (albumsWithImages.isEmpty()) {
            Log.d(TAG, "No albums with images to sync")
            return@withContext SyncResult(0, 0, 0, 0, emptyList())
        }

        Log.d(TAG, "Starting sync for ${albumsWithImages.size} albums")
        
        val albumResults = mutableListOf<AlbumSyncResult>()
        var totalSyncedImages = 0
        var totalSkippedImages = 0
        var successCount = 0

        albumsWithImages.forEachIndexed { index, album ->
            try {
                val imageIds = getImageIdsForAlbum(album.id)
                val mappings = loadMappings()
                
                // 确保封面图片排在最后同步，这样它的 DATE_MODIFIED 最新
                // 系统相册按 DATE_MODIFIED DESC 选择封面，所以最后同步的图片会成为封面
                val sortedImageIds = if (album.coverImageId != null && imageIds.contains(album.coverImageId)) {
                    imageIds.filter { it != album.coverImageId } + listOf(album.coverImageId)
                } else {
                    imageIds
                }
                
                val imageUris = sortedImageIds.mapNotNull { imageId ->
                    mappings.find { it.imageId == imageId }?.imageUri?.let { Uri.parse(it) }
                }

                // #region agent log
                Log.d("DEBUG_SYNC", "[A2,A3,B1] Processing album | name=${album.name} | coverImageId=${album.coverImageId} | imageIds=$sortedImageIds | imageUrisCount=${imageUris.size} | firstImageUri=${imageUris.firstOrNull()}")
                // #endregion

                if (imageUris.isEmpty()) {
                    Log.d(TAG, "Album '${album.name}' has no images to sync")
                    albumResults.add(AlbumSyncResult(album.name, 0, 0, true))
                    successCount++
                    onProgress?.invoke(index + 1, albumsWithImages.size, 0, 0)
                    return@forEachIndexed
                }

                // 确保系统相册文件夹存在（为所有图集创建，不限于 isSyncEnabled）
                // 注意：不再自动设置 isSyncEnabled = true，避免后续添加图片时自动同步
                if (album.systemAlbumPath.isNullOrBlank()) {
                    val systemPath = syncManager.createSystemAlbum(album.name)
                    if (systemPath != null) {
                        val updatedAlbum = album.copy(systemAlbumPath = systemPath)
                        updateAlbum(updatedAlbum)
                    }
                }

                Log.d(TAG, "Syncing album '${album.name}' with ${imageUris.size} images")
                
                val syncResult = syncManager.syncAlbumToSystem(
                    album.name, 
                    imageUris, 
                    album.syncMode
                ) { current, total ->
                    onProgress?.invoke(index + 1, albumsWithImages.size, current, total)
                }
                
                // #region agent log
                Log.d("DEBUG_SYNC", "[B1,B2] Album sync completed | albumName=${album.name} | newlySynced=${syncResult.newlySynced} | alreadyExists=${syncResult.alreadyExists} | mappings=${syncResult.uriMapping.size} | totalImages=${imageUris.size}")
                // #endregion
                
                // 同步后更新 AlbumMapping（重要：确保 imageId 和 imageUri 指向新位置）
                if (syncResult.uriMapping.isNotEmpty()) {
                    applyUriMapping(syncResult.uriMapping)
                    Log.d(TAG, "Updated ${syncResult.uriMapping.size} mappings after sync for album: ${album.name}")
                }
                
                totalSyncedImages += syncResult.newlySynced
                totalSkippedImages += syncResult.alreadyExists
                albumResults.add(AlbumSyncResult(album.name, syncResult.newlySynced, syncResult.alreadyExists, true))
                successCount++
                
                Log.d(TAG, "Successfully synced album '${album.name}': ${syncResult.newlySynced} new, ${syncResult.alreadyExists} skipped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync album '${album.name}'", e)
                albumResults.add(AlbumSyncResult(album.name, 0, 0, false))
            }
        }

        Log.d(TAG, "Sync completed: $successCount/${albumsWithImages.size} albums, $totalSyncedImages new, $totalSkippedImages skipped")
        SyncResult(successCount, albumsWithImages.size, totalSyncedImages, totalSkippedImages, albumResults)
    }

    /**
     * 将单张图片同步到系统相册（当开启同步时）
     */
    private suspend fun syncImageToSystemIfEnabled(imageUri: String, albumId: String) {
        val album = loadAlbums().find { it.id == albumId } ?: return
        if (!album.isSyncEnabled) return

        try {
            val uri = Uri.parse(imageUri)
            syncManager.syncImage(uri, album.name, album.syncMode)
            Log.d(TAG, "Synced image to system album: ${album.name} (mode: ${album.syncMode})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync image to system album", e)
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 更新相册的图片数量和封面
     */
    private suspend fun updateAlbumStats(albumId: String) {
        val imageIds = getImageIdsForAlbum(albumId)
        val currentAlbums = loadAlbums()
        val updatedAlbums = currentAlbums.map { album ->
            if (album.id == albumId) {
                album.copy(
                    imageCount = imageIds.size,
                    coverImageId = imageIds.firstOrNull() ?: album.coverImageId
                )
            } else album
        }
        saveAlbums(updatedAlbums)
        _albums.value = updatedAlbums.sortedBy { it.order }
    }

    /**
     * 初始化（加载数据到内存）
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        _albums.value = loadAlbums().sortedBy { it.order }
        _mappings.value = loadMappings()
    }

    /**
     * 验证并修复图集封面
     * 
     * 检查每个图集的 coverImageId 是否指向当前存在的有效图片。
     * 如果封面图片已不存在，则从图集中选择一个有效的图片作为新封面。
     * 
     * 注意：imageCount 始终基于 mappings 的实际数量，不会被错误重置。
     * 
     * @param validImageIds 当前设备上所有有效图片的 ID 集合
     */
    suspend fun validateAndFixCoverImages(validImageIds: Set<Long>) = withContext(Dispatchers.IO) {
        val currentAlbums = loadAlbums()
        val currentMappings = loadMappings()
        var hasChanges = false
        
        val updatedAlbums = currentAlbums.map { album ->
            // 获取图集实际的图片数量（基于 mappings，而不是存储的 imageCount）
            val albumMappings = currentMappings.filter { album.id in it.albumIds }
            val actualImageCount = albumMappings.size
            val albumImageIds = albumMappings.map { it.imageId }
            
            // 检查当前封面是否有效
            val isCoverValid = album.coverImageId != null && album.coverImageId in validImageIds
            
            // 修正 imageCount（如果不一致）
            val needsCountFix = album.imageCount != actualImageCount
            
            if (!isCoverValid && actualImageCount > 0) {
                // 封面无效但图集有图片，尝试找一个有效的封面
                // 优先选择在 validImageIds 中存在的
                val newCoverId = albumImageIds.firstOrNull { it in validImageIds }
                    ?: albumImageIds.firstOrNull()  // 如果都不在 validImageIds 中，使用第一个
                
                if (newCoverId != null && (newCoverId != album.coverImageId || needsCountFix)) {
                    Log.d(TAG, "Fixed album '${album.name}': cover=${album.coverImageId}->${newCoverId}, count=${album.imageCount}->$actualImageCount")
                    hasChanges = true
                    album.copy(coverImageId = newCoverId, imageCount = actualImageCount)
                } else if (needsCountFix) {
                    Log.d(TAG, "Fixed count for album '${album.name}': ${album.imageCount} -> $actualImageCount")
                    hasChanges = true
                    album.copy(imageCount = actualImageCount)
                } else {
                    album
                }
            } else if (needsCountFix) {
                // 封面有效但数量不一致
                Log.d(TAG, "Fixed count for album '${album.name}': ${album.imageCount} -> $actualImageCount")
                hasChanges = true
                album.copy(imageCount = actualImageCount)
            } else if (actualImageCount == 0 && (album.coverImageId != null || album.imageCount != 0)) {
                // 实际没有图片，重置
                Log.d(TAG, "Reset empty album '${album.name}'")
                hasChanges = true
                album.copy(coverImageId = null, imageCount = 0)
            } else {
                album
            }
        }
        
        if (hasChanges) {
            saveAlbums(updatedAlbums)
            _albums.value = updatedAlbums.sortedBy { it.order }
            Log.d(TAG, "Album covers validated and fixed")
        }
    }

    // ========== JSON 持久化 ==========

    private fun loadAlbums(): List<Album> {
        try {
            if (!albumsFile.exists()) return emptyList()

            val jsonString = albumsFile.readText()
            if (jsonString.isBlank()) return emptyList()

            val jsonArray = JSONArray(jsonString)
            val albums = mutableListOf<Album>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val syncModeStr = obj.optString("syncMode", "MOVE")
                val syncMode = try { SyncMode.valueOf(syncModeStr) } catch (e: Exception) { SyncMode.MOVE }
                
                albums.add(
                    Album(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        coverImageId = obj.optLong("coverImageId", -1).takeIf { it != -1L },
                        color = obj.optLong("color", -1).takeIf { it != -1L },
                        textColor = obj.optLong("textColor", -1).takeIf { it != -1L },
                        emoji = obj.optString("emoji", "").takeIf { it.isNotBlank() },
                        order = obj.optInt("order", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        imageCount = obj.optInt("imageCount", 0),
                        systemAlbumPath = obj.optString("systemAlbumPath", "").takeIf { it.isNotBlank() },
                        isSyncEnabled = obj.optBoolean("isSyncEnabled", false),
                        syncMode = syncMode
                    )
                )
            }

            return albums
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun saveAlbums(albums: List<Album>) {
        try {
            val jsonArray = JSONArray()
            albums.forEach { album ->
                val obj = JSONObject().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("coverImageId", album.coverImageId ?: -1)
                    put("color", album.color ?: -1)
                    put("textColor", album.textColor ?: -1)
                    put("emoji", album.emoji ?: "")
                    put("order", album.order)
                    put("createdAt", album.createdAt)
                    put("imageCount", album.imageCount)
                    put("systemAlbumPath", album.systemAlbumPath ?: "")
                    put("isSyncEnabled", album.isSyncEnabled)
                    put("syncMode", album.syncMode.name)
                }
                jsonArray.put(obj)
            }
            albumsFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadMappings(): List<AlbumMapping> {
        try {
            if (!mappingsFile.exists()) return emptyList()

            val jsonString = mappingsFile.readText()
            if (jsonString.isBlank()) return emptyList()

            val jsonArray = JSONArray(jsonString)
            val mappings = mutableListOf<AlbumMapping>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val albumIdsArray = obj.getJSONArray("albumIds")
                val albumIds = mutableListOf<String>()
                for (j in 0 until albumIdsArray.length()) {
                    albumIds.add(albumIdsArray.getString(j))
                }

                // 兼容旧数据：cleanupUris 不存在时默认空集合
                val cleanupUris = if (obj.has("cleanupUris")) {
                    val cleanupArray = obj.getJSONArray("cleanupUris")
                    val uris = mutableSetOf<String>()
                    for (k in 0 until cleanupArray.length()) {
                        uris.add(cleanupArray.getString(k))
                    }
                    uris
                } else {
                    emptySet()
                }

                mappings.add(
                    AlbumMapping(
                        imageId = obj.getLong("imageId"),
                        imageUri = obj.getString("imageUri"),
                        // 兼容旧数据：originalUri 不存在时默认为 null
                        originalUri = if (obj.has("originalUri")) obj.optString("originalUri").takeIf { it.isNotEmpty() } else null,
                        cleanupUris = cleanupUris,
                        albumIds = albumIds,
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }

            return mappings
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun saveMappings(mappings: List<AlbumMapping>) {
        try {
            val jsonArray = JSONArray()
            mappings.forEach { mapping ->
                val albumIdsArray = JSONArray().apply {
                    mapping.albumIds.forEach { put(it) }
                }
                val obj = JSONObject().apply {
                    put("imageId", mapping.imageId)
                    put("imageUri", mapping.imageUri)
                    // originalUri 为 null 时不写入，节省空间并兼容旧版本
                    mapping.originalUri?.let { put("originalUri", it) }
                    // cleanupUris 为空时不写入，节省空间并兼容旧版本
                    if (mapping.cleanupUris.isNotEmpty()) {
                        val cleanupArray = JSONArray().apply {
                            mapping.cleanupUris.forEach { put(it) }
                        }
                        put("cleanupUris", cleanupArray)
                    }
                    put("albumIds", albumIdsArray)
                    put("addedAt", mapping.addedAt)
                }
                jsonArray.put(obj)
            }
            mappingsFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
