package com.tabula.v3.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.tabula.v3.data.model.SyncMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 系统相册同步管理器
 *
 * 负责将 Tabula 相册同步到系统照片库。
 *
 * ## Android 相册机制
 *
 * Android 系统中没有真正的"相册"概念，MediaStore 使用 BUCKET_DISPLAY_NAME
 * 来分组图片，这个值取决于图片所在的文件夹名称。
 *
 * ## 同步策略
 *
 * 1. **创建相册文件夹**: 在 Pictures 目录下创建子文件夹
 * 2. **复制或移动图片**: 根据用户选择的模式操作
 * 3. **更新 MediaStore**: 通知系统扫描新文件
 *
 * ## 同步模式
 *
 * - **COPY**: 复制图片到系统相册，保留原图位置
 * - **MOVE**: 移动图片到系统相册，原位置不再存在
 */
class SystemAlbumSyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SystemAlbumSyncManager"
        private const val TABULA_ALBUMS_FOLDER = "Tabula"

        @Volatile
        private var instance: SystemAlbumSyncManager? = null

        fun getInstance(context: Context): SystemAlbumSyncManager {
            return instance ?: synchronized(this) {
                instance ?: SystemAlbumSyncManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * 获取 Tabula 相册根目录
     */
    private fun getTabulaAlbumsRoot(): File {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File(picturesDir, TABULA_ALBUMS_FOLDER)
    }

    /**
     * 获取指定相册的文件夹
     * 
     * @param albumName 相册名称
     * @param useExistingPath 如果存在同名系统相册，是否使用其路径
     */
    private fun getAlbumFolder(albumName: String, useExistingPath: Boolean = true): File {
        // 如果启用融合模式，先检查是否存在同名系统相册
        if (useExistingPath) {
            val existingPath = findExistingSystemBucketPath(albumName)
            if (existingPath != null) {
                Log.d(TAG, "Found existing system album path for '$albumName': $existingPath")
                return File(existingPath)
            }
        }
        
        // 不存在同名相册，使用默认 Tabula 目录
        val root = getTabulaAlbumsRoot()
        // 清理文件名中的非法字符
        val safeName = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return File(root, safeName)
    }
    
    /**
     * 查找系统中是否存在同名的相册，返回其路径
     * 
     * 当 App 图集名称与手机相册名称相同时，返回现有相册的路径，
     * 用于将图片同步到现有相册而不是创建新的 Tabula 子目录。
     * 
     * @param albumName 相册名称
     * @return 现有相册的绝对路径，不存在则返回 null
     */
    private fun findExistingSystemBucketPath(albumName: String): String? {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            
            val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(albumName)
            
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // 优先使用 DATA 字段获取完整路径
                    val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    if (dataIndex >= 0) {
                        val dataPath = cursor.getString(dataIndex)
                        if (!dataPath.isNullOrBlank()) {
                            val parentDir = File(dataPath).parentFile
                            if (parentDir != null && parentDir.exists()) {
                                // 检查是否是 Tabula 创建的目录，如果是则不视为"现有系统相册"
                                val tabulaRoot = getTabulaAlbumsRoot().absolutePath
                                if (!parentDir.absolutePath.startsWith(tabulaRoot)) {
                                    return parentDir.absolutePath
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding existing system bucket path for: $albumName", e)
        }
        return null
    }
    
    /**
     * 获取相册的相对路径（用于 MediaStore）
     * 
     * @param albumName 相册名称
     * @return 相对路径，如 "DCIM/Camera" 或 "Pictures/Tabula/MyAlbum"
     */
    private fun getAlbumRelativePath(albumName: String): String {
        val existingPath = findExistingSystemBucketPath(albumName)
        if (existingPath != null) {
            // 从绝对路径提取相对路径
            val externalStorage = Environment.getExternalStorageDirectory().absolutePath
            val relativePath = existingPath.removePrefix(externalStorage).removePrefix("/")
            Log.d(TAG, "Using existing relative path for '$albumName': $relativePath")
            return relativePath
        }
        
        // 使用默认 Tabula 路径
        val safeName = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "${Environment.DIRECTORY_PICTURES}/$TABULA_ALBUMS_FOLDER/$safeName"
    }

    /**
     * 创建系统相册（文件夹）
     *
     * 如果系统中已存在同名相册，则不创建新文件夹，直接返回现有路径。
     * 这样可以实现 App 图集与同名手机相册的融合。
     *
     * @param albumName 相册名称
     * @return 相册文件夹路径，失败返回 null
     */
    suspend fun createSystemAlbum(albumName: String): String? = withContext(Dispatchers.IO) {
        try {
            // 先检查是否存在同名系统相册
            val existingPath = findExistingSystemBucketPath(albumName)
            if (existingPath != null) {
                Log.d(TAG, "Using existing system album: $existingPath (album: $albumName)")
                return@withContext existingPath
            }
            
            // 不存在同名相册，创建新的 Tabula 子目录
            val albumFolder = getAlbumFolder(albumName, useExistingPath = false)
            if (!albumFolder.exists()) {
                val created = albumFolder.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create album folder: ${albumFolder.absolutePath}")
                    return@withContext null
                }
            }

            // 创建一个占位文件确保文件夹存在
            val placeholder = File(albumFolder, ".tabula_album")
            if (!placeholder.exists()) {
                placeholder.createNewFile()
            }

            Log.d(TAG, "Created system album: ${albumFolder.absolutePath}")
            albumFolder.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error creating system album: $albumName", e)
            null
        }
    }

    /**
     * 删除系统相册（文件夹）
     */
    suspend fun deleteSystemAlbum(albumName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val albumFolder = getAlbumFolder(albumName)
            if (albumFolder.exists()) {
                val placeholder = File(albumFolder, ".tabula_album")
                if (placeholder.exists()) {
                    placeholder.delete()
                }

                if (albumFolder.listFiles()?.isEmpty() == true) {
                    albumFolder.delete()
                    Log.d(TAG, "Deleted system album: ${albumFolder.absolutePath}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting system album: $albumName", e)
            false
        }
    }

    /**
     * 删除空的系统相册（根据相对路径）
     * 
     * 仅当文件夹为空时才删除，用于删除空图集功能。
     * 
     * @param relativePath 相对路径（如 "Pictures/Tabula/MyAlbum"）
     * @return true 删除成功，false 删除失败（文件夹不为空或不存在）
     */
    suspend fun deleteBucket(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            val albumFolder = File(externalStorage, relativePath)
            
            if (!albumFolder.exists()) {
                Log.w(TAG, "Bucket folder does not exist: $relativePath")
                return@withContext false
            }
            
            // 检查文件夹是否为空（忽略隐藏文件如 .nomedia）
            val files = albumFolder.listFiles() ?: emptyArray()
            val nonHiddenFiles = files.filter { !it.name.startsWith(".") }
            
            if (nonHiddenFiles.isNotEmpty()) {
                Log.w(TAG, "Cannot delete non-empty bucket: $relativePath (${nonHiddenFiles.size} files)")
                return@withContext false
            }
            
            // 删除隐藏文件
            files.forEach { it.delete() }
            
            // 删除文件夹
            val deleted = albumFolder.delete()
            if (deleted) {
                Log.d(TAG, "Deleted empty bucket: $relativePath")
            } else {
                Log.e(TAG, "Failed to delete bucket: $relativePath")
            }
            
            return@withContext deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting bucket: $relativePath", e)
            false
        }
    }

    /**
     * 重命名系统相册
     */
    suspend fun renameSystemAlbum(oldName: String, newName: String): String? = withContext(Dispatchers.IO) {
        try {
            val oldFolder = getAlbumFolder(oldName)
            val newFolder = getAlbumFolder(newName)

            if (oldFolder.exists() && !newFolder.exists()) {
                val renamed = oldFolder.renameTo(newFolder)
                if (renamed) {
                    scanMediaFolder(newFolder)
                    Log.d(TAG, "Renamed album: $oldName -> $newName")
                    return@withContext newFolder.absolutePath
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming system album: $oldName -> $newName", e)
            null
        }
    }

    /**
     * 将图片添加到系统相册（复制模式）
     *
     * @param imageUri 源图片 URI
     * @param albumName 目标相册名称
     * @param displayName 图片显示名称（可选）
     * @return 新图片的 URI，失败返回 null
     */
    suspend fun addImageToSystemAlbum(
        imageUri: Uri,
        albumName: String,
        displayName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val albumFolder = getAlbumFolder(albumName)
            if (!albumFolder.exists()) {
                albumFolder.mkdirs()
            }

            val fileName = displayName ?: getFileNameFromUri(imageUri) ?: "IMG_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return@withContext copyImageUsingMediaStore(imageUri, albumFolder.name, fileName)
            } else {
                return@withContext copyImageDirectly(imageUri, albumFolder, fileName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding image to system album", e)
            null
        }
    }

    /**
     * 将图片移动到系统相册（移动模式）
     *
     * 移动后原图将被删除。
     *
     * @param imageUri 源图片 URI
     * @param albumName 目标相册名称
     * @return 新图片的 URI，失败返回 null
     */
    suspend fun moveImageToSystemAlbum(
        imageUri: Uri,
        albumName: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val albumFolder = getAlbumFolder(albumName)
            if (!albumFolder.exists()) {
                albumFolder.mkdirs()
            }

            val fileName = getFileNameFromUri(imageUri) ?: "IMG_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore 移动
                return@withContext moveImageUsingMediaStore(imageUri, albumFolder.name, fileName)
            } else {
                // Android 9 及以下：先复制再删除原文件
                val newUri = copyImageDirectly(imageUri, albumFolder, fileName)
                if (newUri != null) {
                    // 删除原文件
                    deleteOriginalImage(imageUri)
                }
                return@withContext newUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving image to system album", e)
            null
        }
    }

    /**
     * 使用 MediaStore API 移动图片
     * 
     * 优先通过更新 RELATIVE_PATH 实现真正的移动，这样可以保留：
     * - 原始 _id（不会在"最近项目"中跳到最前）
     * - 所有时间戳（DATE_TAKEN, DATE_ADDED, DATE_MODIFIED）
     * - 所有元数据（EXIF 等）
     * 
     * @param sourceUri 源图片 URI
     * @param albumName 相册名称
     * @param fileName 文件名
     */
    private fun moveImageUsingMediaStore(
        sourceUri: Uri,
        albumName: String,
        fileName: String
    ): Uri? {
        try {
            // 获取目标相对路径
            val targetRelativePath = getAlbumRelativePath(albumName)
            
            // 方法1：通过更新 RELATIVE_PATH 来移动（真正的移动，保留所有元数据）
            val updateValues = ContentValues().apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
                // 不改变 DISPLAY_NAME，保持原始文件名
            }
            
            val updated = contentResolver.update(sourceUri, updateValues, null, null)
            if (updated > 0) {
                Log.d(TAG, "✓ Moved image via update: $fileName -> $targetRelativePath (preserved _id)")
                return sourceUri
            } else {
                Log.w(TAG, "✗ Failed to move via update (returned 0), falling back to copy+delete: $fileName")
            }

            // 方法2：如果更新失败，则复制后删除（会创建新的 _id）
            Log.d(TAG, "Falling back to copy+delete for: $fileName")
            val newUri = copyImageUsingMediaStore(sourceUri, albumName, fileName)
            if (newUri != null) {
                deleteOriginalImage(sourceUri)
                Log.d(TAG, "Moved image via copy+delete: $fileName -> $targetRelativePath (new _id)")
            }
            return newUri
        } catch (e: Exception) {
            Log.e(TAG, "Error moving image: $fileName", e)
            // 回退到复制+删除
            val newUri = copyImageUsingMediaStore(sourceUri, albumName, fileName)
            if (newUri != null) {
                deleteOriginalImage(sourceUri)
            }
            return newUri
        }
    }

    /**
     * 删除原始图片
     */
    private fun deleteOriginalImage(imageUri: Uri): Boolean {
        return try {
            val deleted = contentResolver.delete(imageUri, null, null)
            if (deleted > 0) {
                Log.d(TAG, "Deleted original image: $imageUri")
                true
            } else {
                Log.w(TAG, "Failed to delete original image: $imageUri")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting original image: $imageUri", e)
            false
        }
    }

    /**
     * 根据原始 URI 查找图片在系统相册中的新 URI
     * 
     * 当图片被同步（复制/移动）到系统相册后，原始 URI 可能已失效。
     * 此方法通过文件名在指定相册中查找同步后的新 URI。
     * 
     * @param originalUri 原始图片 URI
     * @param albumName 目标相册名称
     * @return 系统相册中的新 URI，未找到则返回 null
     */
    fun findNewUriInSystemAlbum(originalUri: Uri, albumName: String): Uri? {
        val fileName = getFileNameFromUri(originalUri)
        if (fileName == null) {
            Log.w(TAG, "Cannot get filename from URI: $originalUri")
            return null
        }
        return findExistingImageInAlbum(albumName, fileName)
    }

    /**
     * 批量查找图片在系统相册中的新 URI
     * 
     * @param originalUris 原始 URI 列表
     * @param albumName 目标相册名称
     * @return Map<原始URI字符串, 新URI>，只包含成功找到的映射
     */
    fun findNewUrisInSystemAlbum(originalUris: List<Uri>, albumName: String): Map<String, Uri> {
        val result = mutableMapOf<String, Uri>()
        var fileNameNotFound = 0
        var imageNotFound = 0
        
        for (uri in originalUris) {
            val fileName = getFileNameFromUri(uri)
            if (fileName == null) {
                fileNameNotFound++
                Log.w(TAG, "Cannot get filename from URI (might be deleted): $uri")
                continue
            }
            
            val newUri = findExistingImageInAlbum(albumName, fileName)
            if (newUri != null) {
                result[uri.toString()] = newUri
                Log.d(TAG, "Found new URI: $uri -> $newUri (file: $fileName)")
            } else {
                imageNotFound++
                Log.w(TAG, "Image not found in system album '$albumName': $fileName")
            }
        }
        
        Log.d(TAG, "findNewUrisInSystemAlbum summary for '$albumName': found=${result.size}, fileNameNotFound=$fileNameNotFound, imageNotFound=$imageNotFound, total=${originalUris.size}")
        return result
    }

    /**
     * 检查目标相册中是否已存在同名文件
     * 
     * 支持检查现有系统相册（如 DCIM/Camera）和 Tabula 创建的相册。
     * 同时检查文件名和文件大小，避免同步重复照片。
     * 
     * 注意：会同时查询原始相册名和清理后的相册名（safeName），
     * 因为 BUCKET_DISPLAY_NAME 是文件夹名称，可能与原始相册名不同。
     * 
     * @param albumName 相册名称
     * @param fileName 文件名
     * @param sourceSize 源文件大小（可选，用于更精确的重复检测）
     * @return 如果已存在，返回现有文件的 URI；否则返回 null
     */
    private fun findExistingImageInAlbum(
        albumName: String,
        fileName: String,
        sourceSize: Long? = null
    ): Uri? {
        try {
            // 清理相册名中的特殊字符，与 getAlbumFolder() 保持一致
            val safeName = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            // 需要查询的 BUCKET_DISPLAY_NAME 列表：
            // 1. 原始相册名（可能是现有系统相册）
            // 2. 清理后的相册名（Tabula 创建的相册）
            val bucketNames = if (safeName != albumName) {
                listOf(albumName, safeName)
            } else {
                listOf(albumName)
            }
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.SIZE
            )
            
            for (bucketName in bucketNames) {
                val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(bucketName, fileName)
                
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        
                        // 如果提供了源文件大小，进行额外验证
                        if (sourceSize != null) {
                            val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                            if (sizeIndex >= 0) {
                                val existingSize = cursor.getLong(sizeIndex)
                                // 文件大小相同，认为是同一张照片
                                if (existingSize == sourceSize) {
                                    Log.d(TAG, "Found existing image in bucket '$bucketName': $fileName")
                                    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                                }
                            }
                        } else {
                            // 没有提供大小，仅按文件名匹配
                            Log.d(TAG, "Found existing image in bucket '$bucketName': $fileName")
                            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        }
                    }
                }
            }
            
            // Android 9 及以下的额外检查：直接检查文件系统
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val albumFolder = getAlbumFolder(albumName)
                val targetFile = File(albumFolder, fileName)
                if (targetFile.exists()) {
                    if (sourceSize == null || targetFile.length() == sourceSize) {
                        return Uri.fromFile(targetFile)
                    }
                }
            }
            
            Log.d(TAG, "Image not found in album '$albumName' (also tried '$safeName'): $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding existing image in album: $albumName/$fileName", e)
        }
        return null
    }

    /**
     * 使用 MediaStore API 复制图片 (Android 10+)
     * 
     * 支持复制到现有系统相册（如 DCIM/Camera）或 Tabula 创建的相册。
     * 
     * @param sourceUri 源图片 URI
     * @param albumName 相册名称（用于查找现有路径或创建新路径）
     * @param fileName 目标文件名
     */
    private fun copyImageUsingMediaStore(
        sourceUri: Uri,
        albumName: String,
        fileName: String
    ): Uri? {
        try {
            // 获取目标相对路径（可能是现有系统相册路径或 Tabula 路径）
            val targetRelativePath = getAlbumRelativePath(albumName)
            
            // #region agent log
            Log.d("DEBUG_SYNC", "[B2] copyImageUsingMediaStore:entry | sourceUri=$sourceUri | albumName=$albumName | fileName=$fileName | targetPath=$targetRelativePath")
            // #endregion
            
            // 获取源文件大小用于重复检测
            var sourceSize: Long? = null
            contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.Images.Media.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    if (sizeIndex >= 0) {
                        sourceSize = cursor.getLong(sizeIndex)
                    }
                }
            }
            
            // 检查是否已存在同名同大小文件，避免重复复制
            val existingUri = findExistingImageInAlbum(albumName, fileName, sourceSize)
            if (existingUri != null) {
                // #region agent log
                Log.d("DEBUG_SYNC", "[B2] copyImageUsingMediaStore:skipped | fileName=$fileName already exists at $existingUri (size match)")
                // #endregion
                Log.d(TAG, "Image already exists in album, skipping: $fileName")
                return existingUri
            }
            
            // 获取源图片的时间元数据，保持原始时间戳
            var dateTaken: Long? = null
            var dateAdded: Long? = null
            var dateModified: Long? = null
            contentResolver.query(
                sourceUri,
                arrayOf(
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN).takeIf { it >= 0 }?.let {
                        dateTaken = cursor.getLong(it)
                    }
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED).takeIf { it >= 0 }?.let {
                        dateAdded = cursor.getLong(it)
                    }
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED).takeIf { it >= 0 }?.let {
                        dateModified = cursor.getLong(it)
                    }
                }
            }
            
            // 修复：当 DATE_TAKEN 无效（为 0 或 null）时，使用 DATE_MODIFIED 作为替代
            // 这样可以确保下载的照片、截屏等没有拍摄时间的图片也能保持正确的时间显示
            // 注意：DATE_TAKEN 是毫秒，DATE_MODIFIED 是秒，需要转换
            val effectiveDateTaken: Long = when {
                dateTaken != null && dateTaken!! > 0 -> dateTaken!!
                dateModified != null && dateModified!! > 0 -> dateModified!! * 1000  // 秒转毫秒
                else -> System.currentTimeMillis()  // 最后的备选
            }
            
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(fileName))
                // 保留原始时间戳，使用有效的 DATE_TAKEN
                put(MediaStore.Images.Media.DATE_TAKEN, effectiveDateTaken)
                dateAdded?.let { put(MediaStore.Images.Media.DATE_ADDED, it) }
                dateModified?.let { put(MediaStore.Images.Media.DATE_MODIFIED, it) }
                put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val newUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return null
            
            // #region agent log
            Log.d("DEBUG_SYNC", "[B2] copyImageUsingMediaStore:inserted | newUri=$newUri | fileName=$fileName | targetPath=$targetRelativePath")
            // #endregion

            contentResolver.openInputStream(sourceUri)?.use { input ->
                contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            // 完成 pending 状态，并再次设置原始时间戳（防止被系统覆盖）
            val updateValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
                // 再次设置时间戳，确保不被覆盖
                put(MediaStore.Images.Media.DATE_TAKEN, effectiveDateTaken)
                dateModified?.let { put(MediaStore.Images.Media.DATE_MODIFIED, it) }
            }
            contentResolver.update(newUri, updateValues, null, null)

            Log.d(TAG, "Copied image using MediaStore: $fileName -> $targetRelativePath")
            return newUri
        } catch (e: Exception) {
            Log.e(TAG, "Error copying image using MediaStore", e)
            return null
        }
    }

    /**
     * 直接复制图片文件 (Android 9 及以下)
     */
    private fun copyImageDirectly(
        sourceUri: Uri,
        targetFolder: File,
        fileName: String
    ): Uri? {
        try {
            val targetFile = File(targetFolder, fileName)
            
            // 检查文件是否已存在，避免重复复制
            if (targetFile.exists()) {
                Log.d(TAG, "Image already exists, skipping: ${targetFile.absolutePath}")
                return Uri.fromFile(targetFile)
            }

            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            scanMediaFile(targetFile)

            Log.d(TAG, "Copied image directly: ${targetFile.absolutePath}")
            return Uri.fromFile(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error copying image directly", e)
            return null
        }
    }

    /**
     * 根据文件名获取 MIME 类型
     */
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "jpg").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }
    }

    /**
     * 从 URI 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    /**
     * 扫描单个文件到媒体库
     */
    private fun scanMediaFile(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(file.name))
                put(MediaStore.Images.Media.SIZE, file.length())
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning file: ${file.absolutePath}", e)
        }
    }

    /**
     * 扫描文件夹到媒体库
     */
    private fun scanMediaFolder(folder: File) {
        folder.listFiles()?.forEach { file ->
            if (file.isFile && isImageFile(file)) {
                scanMediaFile(file)
            }
        }
    }

    /**
     * 判断是否为图片文件
     */
    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")
    }

    /**
     * 检查相册是否已同步到系统
     */
    suspend fun isAlbumSynced(albumName: String): Boolean = withContext(Dispatchers.IO) {
        val albumFolder = getAlbumFolder(albumName)
        albumFolder.exists() && albumFolder.isDirectory
    }

    /**
     * 获取系统相册中的图片数量
     */
    suspend fun getSystemAlbumImageCount(albumName: String): Int = withContext(Dispatchers.IO) {
        val albumFolder = getAlbumFolder(albumName)
        if (!albumFolder.exists()) return@withContext 0

        albumFolder.listFiles()?.count { file ->
            file.isFile && isImageFile(file)
        } ?: 0
    }

    /**
     * 同步结果详情
     */
    data class SyncResultDetail(
        val newlySynced: Int,      // 新同步的图片数
        val alreadyExists: Int,    // 已存在跳过的图片数
        val failed: Int,           // 失败的图片数
        val uriMapping: Map<String, Uri> = emptyMap()  // 原URI字符串 -> 新URI 映射
    ) {
        val total get() = newlySynced + alreadyExists + failed
        val success get() = newlySynced + alreadyExists
    }

    /**
     * 同步相册到系统
     *
     * @param albumName 相册名称
     * @param imageUris 图片 URI 列表
     * @param syncMode 同步模式：COPY 或 MOVE
     * @param onProgress 进度回调 (current, total)
     * @return 同步结果详情（包含 URI 映射，用于更新 AlbumMapping）
     */
    suspend fun syncAlbumToSystem(
        albumName: String,
        imageUris: List<Uri>,
        syncMode: SyncMode = SyncMode.MOVE,
        onProgress: ((Int, Int) -> Unit)? = null
    ): SyncResultDetail = withContext(Dispatchers.IO) {
        var newlySynced = 0
        var alreadyExists = 0
        var failed = 0
        val uriMapping = mutableMapOf<String, Uri>()  // 收集原URI -> 新URI 映射

        // 确保相册存在
        createSystemAlbum(albumName)
        
        // #region agent log
        Log.d("DEBUG_SYNC", "[B1,B2,B3] syncAlbumToSystem:entry | albumName=$albumName | imageCount=${imageUris.size} | syncMode=${syncMode.name} | firstUri=${imageUris.firstOrNull()}")
        // #endregion

        imageUris.forEachIndexed { index, uri ->
            onProgress?.invoke(index + 1, imageUris.size)
            
            val fileName = getFileNameFromUri(uri)
            
            // #region agent log
            Log.d("DEBUG_SYNC", "[B1,B2] Processing image | index=$index | fileName=$fileName | albumName=$albumName | uri=$uri")
            // #endregion

            // 先检查是否已存在
            val existingUri = fileName?.let { findExistingImageInAlbum(albumName, it) }
            
            if (existingUri != null) {
                // 已存在，记录映射（原URI -> 已存在的URI）
                alreadyExists++
                uriMapping[uri.toString()] = existingUri
                // #region agent log
                Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=skipped | existingUri=$existingUri")
                // #endregion
            } else {
                // 不存在，执行同步
                val result = when (syncMode) {
                    SyncMode.COPY -> addImageToSystemAlbum(uri, albumName)
                    SyncMode.MOVE -> moveImageToSystemAlbum(uri, albumName)
                }
                
                if (result != null) {
                    newlySynced++
                    uriMapping[uri.toString()] = result  // 记录映射（原URI -> 新URI）
                    // #region agent log
                    Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=synced | resultUri=$result")
                    // #endregion
                } else {
                    failed++
                    // #region agent log
                    Log.d("DEBUG_SYNC", "[B1,B2] Image sync result | index=$index | fileName=$fileName | status=failed")
                    // #endregion
                }
            }
        }

        val modeText = if (syncMode == SyncMode.MOVE) "moved" else "copied"
        Log.d(TAG, "Synced album '$albumName': newlySynced=$newlySynced, alreadyExists=$alreadyExists, failed=$failed, mappings=${uriMapping.size} ($modeText)")
        
        // 更新最后一张图片（封面）的 DATE_MODIFIED，确保它在系统相册中显示为封面
        // 系统相册按 DATE_MODIFIED DESC 选择封面
        if (imageUris.isNotEmpty()) {
            val lastFileName = getFileNameFromUri(imageUris.last())
            if (lastFileName != null) {
                updateImageDateModified(albumName, lastFileName)
            }
        }
        
        SyncResultDetail(newlySynced, alreadyExists, failed, uriMapping)
    }
    
    /**
     * 更新图片的 DATE_MODIFIED 为当前时间
     * 用于确保封面图片在系统相册中显示正确
     */
    private fun updateImageDateModified(albumName: String, fileName: String) {
        try {
            val existingUri = findExistingImageInAlbum(albumName, fileName)
            if (existingUri != null) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                }
                val updated = contentResolver.update(existingUri, updateValues, null, null)
                if (updated > 0) {
                    Log.d(TAG, "Updated DATE_MODIFIED for cover image: $fileName")
                }
            }
        } catch (e: Exception) {
            // 更新失败不影响同步结果，只记录日志
            Log.w(TAG, "Failed to update DATE_MODIFIED for cover image: $fileName", e)
        }
    }

    /**
     * 处理单张图片同步
     *
     * @param imageUri 图片 URI
     * @param albumName 相册名称
     * @param syncMode 同步模式
     * @return 新图片 URI，失败返回 null
     */
    suspend fun syncImage(
        imageUri: Uri,
        albumName: String,
        syncMode: SyncMode
    ): Uri? = when (syncMode) {
        SyncMode.COPY -> addImageToSystemAlbum(imageUri, albumName)
        SyncMode.MOVE -> moveImageToSystemAlbum(imageUri, albumName)
    }

    // ========== 直接操作系统相册（图库集成） ==========

    /**
     * 移动图片到指定的系统相册（bucket）
     * 
     * 这是图库与系统相册直接集成的核心方法。
     * 图片的 BUCKET_DISPLAY_NAME 由其所在文件夹决定，移动图片就是改变其所属相册。
     * 
     * @param imageUri 源图片 URI
     * @param targetBucketName 目标相册名称
     * @param targetRelativePath 目标相对路径（可选，如 "DCIM/Camera"，不提供则使用 Pictures/Tabula/{name}）
     * @return 移动后的新 URI，失败返回 null
     */
    suspend fun moveImageToBucket(
        imageUri: Uri,
        targetBucketName: String,
        targetRelativePath: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(imageUri) ?: "IMG_${System.currentTimeMillis()}.jpg"
            
            // 确定目标路径
            val finalRelativePath = targetRelativePath 
                ?: getAlbumRelativePath(targetBucketName)
            
            Log.d(TAG, "Moving image to bucket: $targetBucketName, path: $finalRelativePath")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：使用 MediaStore 更新 RELATIVE_PATH
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, finalRelativePath)
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                }
                
                val updated = contentResolver.update(imageUri, updateValues, null, null)
                if (updated > 0) {
                    Log.d(TAG, "Moved image using MediaStore update: $fileName -> $finalRelativePath")
                    // 返回更新后的 URI（同一个 URI，但路径已变）
                    return@withContext imageUri
                }
                
                // 更新失败，尝试复制后删除
                Log.w(TAG, "MediaStore update failed, trying copy+delete")
                val newUri = copyImageToPath(imageUri, finalRelativePath, fileName)
                if (newUri != null) {
                    deleteOriginalImage(imageUri)
                    return@withContext newUri
                }
            } else {
                // Android 9 及以下：直接文件操作
                val targetFolder = getAlbumFolder(targetBucketName)
                val newUri = copyImageDirectly(imageUri, targetFolder, fileName)
                if (newUri != null) {
                    deleteOriginalImage(imageUri)
                    return@withContext newUri
                }
            }
            
            Log.e(TAG, "Failed to move image to bucket: $targetBucketName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error moving image to bucket: $targetBucketName", e)
            null
        }
    }

    /**
     * 复制图片到指定路径
     */
    private fun copyImageToPath(
        sourceUri: Uri,
        targetRelativePath: String,
        fileName: String
    ): Uri? {
        try {
            // 获取源图片的时间元数据，保持原始时间戳
            var dateTaken: Long? = null
            var dateAdded: Long? = null
            var dateModified: Long? = null
            contentResolver.query(
                sourceUri,
                arrayOf(
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN).takeIf { it >= 0 }?.let {
                        dateTaken = cursor.getLong(it)
                    }
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED).takeIf { it >= 0 }?.let {
                        dateAdded = cursor.getLong(it)
                    }
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED).takeIf { it >= 0 }?.let {
                        dateModified = cursor.getLong(it)
                    }
                }
            }
            
            // 当 DATE_TAKEN 无效时，使用 DATE_MODIFIED 作为替代
            // DATE_TAKEN 是毫秒，DATE_MODIFIED 是秒
            val effectiveDateTaken: Long = when {
                dateTaken != null && dateTaken!! > 0 -> dateTaken!!
                dateModified != null && dateModified!! > 0 -> dateModified!! * 1000
                else -> System.currentTimeMillis()
            }
            
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(fileName))
                put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
                // 保留原始时间戳
                put(MediaStore.Images.Media.DATE_TAKEN, effectiveDateTaken)
                dateAdded?.let { put(MediaStore.Images.Media.DATE_ADDED, it) }
                dateModified?.let { put(MediaStore.Images.Media.DATE_MODIFIED, it) }
            }

            val newUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return null

            contentResolver.openInputStream(sourceUri)?.use { input ->
                contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            // 完成 pending 状态，并再次设置时间戳（防止被系统覆盖）
            val updateValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
                put(MediaStore.Images.Media.DATE_TAKEN, effectiveDateTaken)
                dateModified?.let { put(MediaStore.Images.Media.DATE_MODIFIED, it) }
            }
            contentResolver.update(newUri, updateValues, null, null)

            return newUri
        } catch (e: Exception) {
            Log.e(TAG, "Error copying image to path: $targetRelativePath", e)
            return null
        }
    }

    /**
     * 创建新的系统相册（文件夹）并返回其相对路径
     * 
     * 使用 MediaStore API 创建占位文件来间接创建文件夹。
     * Scoped Storage 限制下，无法直接使用 File API 在公共目录创建文件/文件夹。
     * 
     * @param albumName 相册名称
     * @return 创建的相册相对路径（如 "Pictures/Tabula/旅行"）
     */
    suspend fun createBucket(albumName: String): String? = withContext(Dispatchers.IO) {
        try {
            val relativePath = getAlbumRelativePath(albumName)
            
            // 使用 MediaStore API 创建占位文件来间接创建文件夹
            // 文件夹会在创建第一个文件时自动生成
            val placeholderName = ".tabula_placeholder_${System.currentTimeMillis()}.jpg"
            
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, placeholderName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            
            val placeholderUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
            
            if (placeholderUri != null) {
                // 写入最小的有效 JPEG 数据，防止 MediaStore 拒绝空文件
                contentResolver.openOutputStream(placeholderUri)?.use { output ->
                    // 最小的有效 JPEG 文件头
                    val minimalJpeg = byteArrayOf(
                        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                        0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                        0xFF.toByte(), 0xD9.toByte()
                    )
                    output.write(minimalJpeg)
                }
                
                // 完成 pending 状态
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(placeholderUri, updateValues, null, null)
                
                // 立即删除占位文件，文件夹会保留
                contentResolver.delete(placeholderUri, null, null)
                
                Log.d(TAG, "Created bucket: $albumName at $relativePath")
                return@withContext relativePath
            } else {
                Log.e(TAG, "Failed to create placeholder file for bucket: $albumName")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bucket: $albumName", e)
            null
        }
    }

    /**
     * 获取图片当前所属的 bucket 名称
     */
    fun getImageBucketName(imageUri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        contentResolver.query(imageUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val bucketColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                if (bucketColumn >= 0) {
                    return cursor.getString(bucketColumn)
                }
            }
        }
        return null
    }
}
