package com.tabula.v3.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.tabula.v3.data.model.AlbumStorageInfo
import com.tabula.v3.data.model.StorageScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 存储统计管理器
 * 
 * 负责扫描设备存储空间和图片占用情况，并持久化扫描结果
 */
class StorageStatsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StorageStatsManager"
        private const val SCAN_RESULT_FILE = "storage_scan_result.json"
        
        @Volatile
        private var instance: StorageStatsManager? = null
        
        fun getInstance(context: Context): StorageStatsManager {
            return instance ?: synchronized(this) {
                instance ?: StorageStatsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        /**
         * 格式化字节大小为可读字符串
         */
        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
                else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            }
        }
        
        /**
         * 格式化字节大小（简短版本）
         */
        fun formatBytesShort(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format("%.0f KB", bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
                else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
            }
        }
    }
    
    private val imageRepository: LocalImageRepository by lazy {
        LocalImageRepository(context)
    }
    
    private val scanResultFile: File
        get() = File(context.filesDir, SCAN_RESULT_FILE)
    
    // 扫描结果状态
    private val _scanResult = MutableStateFlow(StorageScanResult.EMPTY)
    val scanResult: StateFlow<StorageScanResult> = _scanResult.asStateFlow()
    
    // 扫描进行中状态
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    /**
     * 初始化：加载上次的扫描结果
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val savedResult = loadScanResult()
        if (savedResult != null) {
            _scanResult.value = savedResult
            Log.d(TAG, "Loaded previous scan result from ${savedResult.scanTimestamp}")
        }
    }
    
    /**
     * 执行存储扫描
     * 
     * @param onProgress 进度回调 (0.0 ~ 1.0)，在主线程调用
     * @return 扫描结果
     */
    suspend fun performScan(
        onProgress: ((Float) -> Unit)? = null
    ): StorageScanResult = withContext(Dispatchers.IO) {
        // 防止重复扫描
        if (_isScanning.value) {
            Log.w(TAG, "Scan already in progress, skipping")
            return@withContext _scanResult.value
        }
        
        _isScanning.value = true
        val startTime = System.currentTimeMillis()
        
        // 最小展示时间（毫秒），确保动画有足够时间流畅展示
        val minDisplayTime = 2000L
        
        // 安全的进度回调（切换到主线程）
        val safeProgress: suspend (Float) -> Unit = { progress ->
            MainScope().launch {
                onProgress?.invoke(progress)
            }
        }
        
        // 带延迟的进度更新，让动画更流畅
        suspend fun updateProgress(progress: Float, minDelayMs: Long = 0) {
            safeProgress(progress)
            if (minDelayMs > 0) {
                kotlinx.coroutines.delay(minDelayMs)
            }
        }
        
        try {
            // 阶段性进度更新，每个阶段有适当延迟
            updateProgress(0.1f, 150)
            
            // 1. 获取手机存储空间信息
            val (totalStorage, usedStorage) = getPhoneStorageInfo()
            updateProgress(0.2f, 150)
            
            // 2. 获取所有图片
            val allImages = imageRepository.getAllImages()
            updateProgress(0.4f, 200)
            
            // 3. 计算图片总大小
            val totalImageSize = allImages.sumOf { it.size }
            updateProgress(0.55f, 150)
            
            // 4. 按 bucket 分组计算各图集大小
            val bucketGroups = allImages.groupBy { it.bucketDisplayName ?: "未知" }
            val totalBuckets = bucketGroups.size.coerceAtLeast(1) // 防止除以零
            
            // 获取上次扫描结果用于对比
            val previousResult = _scanResult.value
            val previousAlbumMap = previousResult.albumStorageList.associateBy { it.bucketName }
            
            var processedBuckets = 0
            val albumStorageList = bucketGroups.map { (bucketName, images) ->
                val storageSize = images.sumOf { it.size }
                val previousInfo = previousAlbumMap[bucketName]
                
                processedBuckets++
                // 分散更新进度，但不是每个都更新（减少更新频率）
                if (processedBuckets % 5 == 0 || processedBuckets == totalBuckets) {
                    updateProgress(0.55f + 0.35f * processedBuckets / totalBuckets, 50)
                }
                
                AlbumStorageInfo(
                    bucketName = bucketName,
                    storageSize = storageSize,
                    imageCount = images.size,
                    previousStorageSize = previousInfo?.storageSize
                )
            }.sortedByDescending { it.storageSize }
            
            updateProgress(0.92f, 100)
            
            // 5. 构建扫描结果
            val result = StorageScanResult(
                totalPhoneStorage = totalStorage,
                usedPhoneStorage = usedStorage,
                totalImageStorage = totalImageSize,
                albumStorageList = albumStorageList,
                scanTimestamp = System.currentTimeMillis(),
                previousTotalImageStorage = if (previousResult.scanTimestamp > 0) {
                    previousResult.totalImageStorage
                } else null
            )
            
            // 6. 保存扫描结果
            saveScanResult(result)
            
            // 确保达到最小展示时间，让动画流畅完成
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < minDisplayTime) {
                val remaining = minDisplayTime - elapsed
                // 在剩余时间内平滑推进到 100%
                updateProgress(0.96f, remaining / 3)
                updateProgress(0.98f, remaining / 3)
                kotlinx.coroutines.delay(remaining / 3)
            }
            
            updateProgress(1.0f, 0)
            
            // 等待一小段时间让 100% 的动画展示
            kotlinx.coroutines.delay(200)
            
            _scanResult.value = result
            
            Log.d(TAG, "Scan completed: ${albumStorageList.size} albums, total ${formatBytes(totalImageSize)}")
            result
        } finally {
            _isScanning.value = false
        }
    }
    
    /**
     * 获取手机存储空间信息
     * 
     * @return Pair<总空间, 已用空间>（字节）
     */
    private fun getPhoneStorageInfo(): Pair<Long, Long> {
        return try {
            val statFs = StatFs(Environment.getExternalStorageDirectory().path)
            val totalBytes = statFs.totalBytes
            val availableBytes = statFs.availableBytes
            val usedBytes = totalBytes - availableBytes
            Pair(totalBytes, usedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage info", e)
            Pair(0L, 0L)
        }
    }
    
    /**
     * 保存扫描结果到文件
     */
    private fun saveScanResult(result: StorageScanResult) {
        try {
            val albumsArray = JSONArray()
            result.albumStorageList.forEach { album ->
                val albumObj = JSONObject().apply {
                    put("bucketName", album.bucketName)
                    put("storageSize", album.storageSize)
                    put("imageCount", album.imageCount)
                    album.previousStorageSize?.let { put("previousStorageSize", it) }
                }
                albumsArray.put(albumObj)
            }
            
            val jsonObject = JSONObject().apply {
                put("totalPhoneStorage", result.totalPhoneStorage)
                put("usedPhoneStorage", result.usedPhoneStorage)
                put("totalImageStorage", result.totalImageStorage)
                put("albumStorageList", albumsArray)
                put("scanTimestamp", result.scanTimestamp)
                result.previousTotalImageStorage?.let { put("previousTotalImageStorage", it) }
            }
            
            scanResultFile.writeText(jsonObject.toString())
            Log.d(TAG, "Saved scan result to file")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scan result", e)
        }
    }
    
    /**
     * 从文件加载扫描结果
     */
    private fun loadScanResult(): StorageScanResult? {
        return try {
            if (!scanResultFile.exists()) return null
            
            val jsonString = scanResultFile.readText()
            if (jsonString.isBlank()) return null
            
            val jsonObject = JSONObject(jsonString)
            
            val albumsArray = jsonObject.getJSONArray("albumStorageList")
            val albumStorageList = mutableListOf<AlbumStorageInfo>()
            
            for (i in 0 until albumsArray.length()) {
                val albumObj = albumsArray.getJSONObject(i)
                albumStorageList.add(
                    AlbumStorageInfo(
                        bucketName = albumObj.getString("bucketName"),
                        storageSize = albumObj.getLong("storageSize"),
                        imageCount = albumObj.getInt("imageCount"),
                        previousStorageSize = albumObj.optLong("previousStorageSize", -1)
                            .takeIf { it >= 0 }
                    )
                )
            }
            
            StorageScanResult(
                totalPhoneStorage = jsonObject.getLong("totalPhoneStorage"),
                usedPhoneStorage = jsonObject.getLong("usedPhoneStorage"),
                totalImageStorage = jsonObject.getLong("totalImageStorage"),
                albumStorageList = albumStorageList,
                scanTimestamp = jsonObject.getLong("scanTimestamp"),
                previousTotalImageStorage = jsonObject.optLong("previousTotalImageStorage", -1)
                    .takeIf { it >= 0 }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading scan result", e)
            null
        }
    }
    
    /**
     * 清除扫描结果
     */
    fun clearScanResult() {
        try {
            scanResultFile.delete()
            _scanResult.value = StorageScanResult.EMPTY
            Log.d(TAG, "Cleared scan result")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing scan result", e)
        }
    }
}
