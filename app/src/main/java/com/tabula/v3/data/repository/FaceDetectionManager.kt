package com.tabula.v3.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 人像检测管理器
 *
 * 使用 Google ML Kit Face Detection（Bundled 模型）检测图片中是否包含人脸。
 * 检测结果缓存到 SharedPreferences，避免重复检测。
 *
 * 设计原则：
 * 1. 按需检测：只在需要时检测，不做全量扫描
 * 2. 缓存优先：检测结果持久化缓存，重启后无需重新检测
 * 3. 失败安全（fail-open）：检测失败时不屏蔽图片，避免误伤
 * 4. 并发控制：限制同时进行的检测数量，避免内存压力
 * 5. 低内存占用：使用降采样的小图片进行检测
 * 6. 兼容性：bundled 模型不依赖 Google Play Services，支持所有 Android 12+ 设备
 */
class FaceDetectionManager(context: Context) {

    companion object {
        private const val TAG = "FaceDetectionManager"
        private const val PREFS_NAME = "tabula_face_detection_cache"
        private const val PREFIX_RESULT = "face_"

        // 检测结果常量
        private const val RESULT_NO_FACE = 0
        private const val RESULT_HAS_FACE = 1
        private const val RESULT_FAILED = -1

        // 检测用图片最大尺寸（较小的尺寸加快检测速度、减少内存）
        // 480px 足够 ML Kit 检测大多数人脸，同时内存占用极低
        private const val DETECTION_MAX_SIZE = 480

        // 并发控制：最多同时进行 2 个检测，避免内存压力
        private const val MAX_CONCURRENT_DETECTIONS = 2

        // 连续失败阈值：超过此数量视为 ML Kit 不可用，自动降级
        private const val CONSECUTIVE_FAILURE_THRESHOLD = 5

        @Volatile
        private var instance: FaceDetectionManager? = null

        fun getInstance(context: Context): FaceDetectionManager {
            return instance ?: synchronized(this) {
                instance ?: FaceDetectionManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // 并发控制信号量
    private val detectionSemaphore = java.util.concurrent.Semaphore(MAX_CONCURRENT_DETECTIONS)

    // ========== 运行时健康状态跟踪 ==========
    // 连续失败计数（用于检测 ML Kit 是否在当前设备上不可用）
    @Volatile
    private var consecutiveFailures = 0

    // ML Kit 是否被判定为不可用（连续失败过多后自动降级）
    @Volatile
    private var isDetectorUnavailable = false

    // 最后一次错误信息（用于 UI 展示）
    @Volatile
    var lastError: String? = null
        private set

    // 懒加载人脸检测器（线程安全，只初始化一次）
    // 使用 PERFORMANCE_MODE_FAST 以牺牲一定准确率换取速度
    // 关闭 landmark / classification / contour 减少计算量
    private val faceDetector: FaceDetector? by lazy {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.1f)  // 检测 >= 10% 图片大小的人脸，覆盖远景人像
                .build()
            FaceDetection.getClient(options)
        } catch (e: Exception) {
            // ML Kit 初始化失败（可能是设备不支持或内存不足）
            Log.e(TAG, "ML Kit Face Detection initialization failed", e)
            isDetectorUnavailable = true
            lastError = "人脸检测引擎初始化失败: ${e.message}"
            null
        }
    }

    /**
     * 检查人脸检测功能是否可用
     * 如果 ML Kit 初始化失败或连续失败过多，返回 false
     */
    fun isAvailable(): Boolean {
        if (isDetectorUnavailable) return false
        // 触发懒加载检查
        return faceDetector != null
    }

    /**
     * 重置不可用状态（用户手动重试时调用）
     */
    fun resetAvailability() {
        isDetectorUnavailable = false
        consecutiveFailures = 0
        lastError = null
    }

    /**
     * 检测单张图片是否包含人脸
     *
     * 流程：可用性检查 -> 缓存查询 -> 解码小图 -> ML Kit 检测 -> 缓存结果
     *
     * @param imageId 图片 ID（用于缓存键）
     * @param uri 图片内容 URI
     * @return true 如果检测到人脸，false 如果未检测到或检测失败或不可用
     */
    suspend fun hasFace(imageId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        // 0. 检查是否可用（ML Kit 初始化失败或连续失败过多则跳过）
        if (isDetectorUnavailable) return@withContext false

        // 1. 查缓存
        val cached = getCachedResult(imageId)
        if (cached != null) {
            // 有缓存命中说明检测能正常工作，重置连续失败计数
            if (consecutiveFailures > 0) consecutiveFailures = 0
            return@withContext cached
        }

        // 2. 执行检测
        val result = detectFace(uri)

        // 3. 跟踪检测健康状态
        if (result == RESULT_FAILED) {
            consecutiveFailures++
            if (consecutiveFailures >= CONSECUTIVE_FAILURE_THRESHOLD) {
                Log.e(TAG, "Face detection failed $consecutiveFailures times consecutively, marking as unavailable")
                isDetectorUnavailable = true
                lastError = "人脸检测连续失败 $consecutiveFailures 次，已自动暂停"
            }
        } else {
            // 成功检测，重置失败计数
            consecutiveFailures = 0
        }

        // 4. 缓存结果（失败结果也缓存，下次当作"未缓存"重试）
        saveResult(imageId, result)

        return@withContext result == RESULT_HAS_FACE
    }

    /**
     * 过滤掉包含人脸的图片
     *
     * 对每张图片执行人脸检测（有缓存则直接读取），返回不包含人脸的图片列表。
     * 检测失败的图片保留（fail-open），避免误伤。
     * 如果检测引擎不可用，直接返回原始列表（自动降级）。
     *
     * @param images 要检查的图片列表
     * @return 不包含人脸的图片列表；引擎不可用时返回原始列表
     */
    suspend fun filterImagesWithoutFaces(images: List<ImageFile>): List<ImageFile> =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext emptyList()

            // 检测引擎不可用时自动降级：直接返回原始列表
            if (isDetectorUnavailable) {
                Log.w(TAG, "Face detector unavailable, skipping filter (returning all images)")
                return@withContext images
            }

            val result = mutableListOf<ImageFile>()
            val processedIds = mutableSetOf<Long>()

            for (image in images) {
                // 如果检测过程中被标记为不可用（连续失败），立即停止过滤
                // 将剩余未检测的图片全部保留（fail-open）
                if (isDetectorUnavailable) {
                    Log.w(TAG, "Face detector became unavailable during filtering, keeping remaining ${images.size - processedIds.size} images")
                    for (remaining in images) {
                        if (remaining.id !in processedIds) {
                            result.add(remaining)
                        }
                    }
                    break
                }

                processedIds.add(image.id)

                try {
                    val hasFaceResult = hasFace(image.id, image.uri)
                    if (!hasFaceResult) {
                        result.add(image)
                    }
                } catch (e: Exception) {
                    // 检测失败，fail-open：保留图片，不屏蔽
                    Log.w(TAG, "Face detection failed for ${image.displayName}, keeping image", e)
                    result.add(image)
                }
            }

            return@withContext result
        }

    /**
     * 获取缓存的检测结果
     *
     * @return true=有人脸, false=无人脸, null=未缓存
     */
    fun getCachedResult(imageId: Long): Boolean? {
        val key = "$PREFIX_RESULT$imageId"
        if (!prefs.contains(key)) return null
        return when (prefs.getInt(key, RESULT_FAILED)) {
            RESULT_HAS_FACE -> true
            RESULT_NO_FACE -> false
            else -> null  // 失败的结果视为未缓存，下次重试
        }
    }

    /**
     * 批量获取缓存结果（性能优化：一次读取所有条目）
     *
     * @param imageIds 图片 ID 列表
     * @return Map<imageId, hasFace>，未缓存的图片不包含在结果中
     */
    fun getCachedResultsBatch(imageIds: List<Long>): Map<Long, Boolean> {
        if (imageIds.isEmpty()) return emptyMap()

        val allEntries = prefs.all
        val result = mutableMapOf<Long, Boolean>()

        for (imageId in imageIds) {
            val key = "$PREFIX_RESULT$imageId"
            val value = allEntries[key] as? Int
            if (value != null) {
                when (value) {
                    RESULT_HAS_FACE -> result[imageId] = true
                    RESULT_NO_FACE -> result[imageId] = false
                    // RESULT_FAILED: 不加入结果，视为未缓存，下次重试
                }
            }
        }

        return result
    }

    // ==================== 内部实现 ====================

    /**
     * 执行人脸检测
     *
     * @return RESULT_HAS_FACE, RESULT_NO_FACE, or RESULT_FAILED
     */
    private suspend fun detectFace(uri: Uri): Int {
        // 获取信号量许可，限制并发
        detectionSemaphore.acquire()

        return try {
            // 加载缩小的位图用于检测
            val bitmap = loadScaledBitmap(uri)
            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap for face detection")
                return RESULT_FAILED
            }

            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val faceCount = processFaceDetection(inputImage)
                when {
                    faceCount < 0 -> RESULT_FAILED   // 检测器不可用
                    faceCount > 0 -> RESULT_HAS_FACE
                    else -> RESULT_NO_FACE
                }
            } finally {
                bitmap.recycle()  // 立即回收，释放内存
            }
        } catch (e: Exception) {
            Log.w(TAG, "Face detection error: ${e.message}")
            RESULT_FAILED
        } finally {
            detectionSemaphore.release()
        }
    }

    /**
     * 加载缩小的位图用于人脸检测
     *
     * 使用 inSampleSize 降采样，将图片缩小到 DETECTION_MAX_SIZE 以内。
     * 这样一张 4000x3000 的照片只会占用约 480x360 * 4 bytes ≈ 0.7MB 内存。
     */
    private fun loadScaledBitmap(uri: Uri): Bitmap? {
        return try {
            // 第一步：只读取图片尺寸（不分配内存）
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            // 第二步：计算最佳采样率
            val sampleSize = calculateSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                DETECTION_MAX_SIZE
            )

            // 第三步：使用采样率解码
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            appContext.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bitmap: ${e.message}")
            null
        }
    }

    /**
     * 计算 inSampleSize（2 的幂次）
     *
     * 找到最小的采样率使图片最大维度 <= maxSize
     */
    private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / sampleSize > maxSize) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * 执行 ML Kit 人脸检测（将 Task API 转为 coroutine）
     *
     * @return 检测到的人脸数量，检测器不可用时返回 -1
     */
    private suspend fun processFaceDetection(image: InputImage): Int {
        val detector = faceDetector
        if (detector == null) {
            Log.e(TAG, "Face detector is null, cannot process")
            return -1
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                val task = detector.process(image)

                task.addOnSuccessListener { faces ->
                    if (continuation.isActive) {
                        continuation.resume(faces.size)
                    }
                }

                task.addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit face detection failed: ${e.message}")
                    if (continuation.isActive) {
                        // fail-open: 检测失败时返回 0（不视为有人脸）
                        continuation.resume(0)
                    }
                }

                // 支持协程取消
                continuation.invokeOnCancellation {
                    // ML Kit Task 不支持取消，但我们停止等待结果
                    Log.d(TAG, "Face detection cancelled")
                }
            } catch (e: Exception) {
                // 防御性处理：process() 本身抛异常（极少见，但可能在模型损坏时发生）
                Log.e(TAG, "ML Kit process() threw exception", e)
                if (continuation.isActive) {
                    continuation.resume(-1)
                }
            }
        }
    }

    /**
     * 保存检测结果到缓存
     */
    private fun saveResult(imageId: Long, result: Int) {
        prefs.edit()
            .putInt("$PREFIX_RESULT$imageId", result)
            .apply()
    }

    // ==================== 缓存管理 ====================

    /**
     * 清理已不存在的图片的缓存
     *
     * 建议在图片列表加载完成后调用，避免缓存无限增长。
     *
     * @param validImageIds 当前有效的图片 ID 集合
     */
    fun cleanupStaleCache(validImageIds: Set<Long>) {
        val allEntries = prefs.all
        val editor = prefs.edit()
        var cleanupCount = 0

        for (key in allEntries.keys) {
            if (key.startsWith(PREFIX_RESULT)) {
                val idStr = key.removePrefix(PREFIX_RESULT)
                val imageId = idStr.toLongOrNull()
                if (imageId != null && imageId !in validImageIds) {
                    editor.remove(key)
                    cleanupCount++
                }
            }
        }

        if (cleanupCount > 0) {
            editor.apply()
            Log.d(TAG, "Cleaned up $cleanupCount stale face detection cache entries")
        }
    }

    /**
     * 获取缓存统计信息（用于 Lab 界面显示）
     */
    fun getCacheStats(): CacheStats {
        val allEntries = prefs.all
        var faceCount = 0
        var noFaceCount = 0
        var failedCount = 0

        for ((key, value) in allEntries) {
            if (key.startsWith(PREFIX_RESULT)) {
                when (value as? Int) {
                    RESULT_HAS_FACE -> faceCount++
                    RESULT_NO_FACE -> noFaceCount++
                    RESULT_FAILED -> failedCount++
                }
            }
        }

        return CacheStats(
            totalScanned = faceCount + noFaceCount + failedCount,
            faceCount = faceCount,
            noFaceCount = noFaceCount,
            failedCount = failedCount
        )
    }

    /**
     * 清空所有缓存并重置检测状态
     * 同时重置不可用标记，允许重新尝试检测
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        resetAvailability()
        Log.d(TAG, "Face detection cache cleared and availability reset")
    }

    /**
     * 缓存统计信息
     *
     * @param totalScanned 已扫描总数
     * @param faceCount 检测到人脸的数量
     * @param noFaceCount 未检测到人脸的数量
     * @param failedCount 检测失败的数量
     */
    data class CacheStats(
        val totalScanned: Int,
        val faceCount: Int,
        val noFaceCount: Int,
        val failedCount: Int
    )
}
