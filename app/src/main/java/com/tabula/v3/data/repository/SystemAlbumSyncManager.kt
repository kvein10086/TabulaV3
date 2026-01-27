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
     */
    private fun getAlbumFolder(albumName: String): File {
        val root = getTabulaAlbumsRoot()
        // 清理文件名中的非法字符
        val safeName = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return File(root, safeName)
    }

    /**
     * 创建系统相册（文件夹）
     *
     * @param albumName 相册名称
     * @return 相册文件夹路径，失败返回 null
     */
    suspend fun createSystemAlbum(albumName: String): String? = withContext(Dispatchers.IO) {
        try {
            val albumFolder = getAlbumFolder(albumName)
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
     * 使用 MediaStore API 移动图片 (Android 10+)
     */
    private fun moveImageUsingMediaStore(
        sourceUri: Uri,
        relativePath: String,
        fileName: String
    ): Uri? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 方法1：尝试通过更新 RELATIVE_PATH 来移动
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, 
                        "${Environment.DIRECTORY_PICTURES}/$TABULA_ALBUMS_FOLDER/$relativePath")
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                }
                
                val updated = contentResolver.update(sourceUri, updateValues, null, null)
                if (updated > 0) {
                    Log.d(TAG, "Moved image using MediaStore update: $fileName")
                    return sourceUri
                }
            }

            // 方法2：如果更新失败，则复制后删除
            val newUri = copyImageUsingMediaStore(sourceUri, relativePath, fileName)
            if (newUri != null) {
                deleteOriginalImage(sourceUri)
            }
            return newUri
        } catch (e: Exception) {
            Log.e(TAG, "Error moving image using MediaStore", e)
            // 回退到复制+删除
            val newUri = copyImageUsingMediaStore(sourceUri, relativePath, fileName)
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
     * 使用 MediaStore API 复制图片 (Android 10+)
     */
    private fun copyImageUsingMediaStore(
        sourceUri: Uri,
        relativePath: String,
        fileName: String
    ): Uri? {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, getMimeType(fileName))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, 
                        "${Environment.DIRECTORY_PICTURES}/$TABULA_ALBUMS_FOLDER/$relativePath")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(newUri, updateValues, null, null)
            }

            Log.d(TAG, "Copied image using MediaStore: $fileName")
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
     * 同步相册到系统
     *
     * @param albumName 相册名称
     * @param imageUris 图片 URI 列表
     * @param syncMode 同步模式：COPY 或 MOVE
     * @param onProgress 进度回调 (current, total)
     * @return 成功同步的图片数量
     */
    suspend fun syncAlbumToSystem(
        albumName: String,
        imageUris: List<Uri>,
        syncMode: SyncMode = SyncMode.MOVE,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0

        // 确保相册存在
        createSystemAlbum(albumName)

        imageUris.forEachIndexed { index, uri ->
            onProgress?.invoke(index + 1, imageUris.size)

            val result = when (syncMode) {
                SyncMode.COPY -> addImageToSystemAlbum(uri, albumName)
                SyncMode.MOVE -> moveImageToSystemAlbum(uri, albumName)
            }
            
            if (result != null) {
                successCount++
            }
        }

        val modeText = if (syncMode == SyncMode.MOVE) "moved" else "copied"
        Log.d(TAG, "Synced album '$albumName': $successCount/${imageUris.size} images $modeText")
        successCount
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
}
