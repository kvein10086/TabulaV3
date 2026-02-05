package com.tabula.v3.di

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors

/**
 * Coil 图片加载器极致优化配置
 *
 * 针对 120Hz 高速滑动场景优化：
 * 1. 内存缓存：可用 RAM 的 25%（从 30% 降低）
 * 2. 限制并发解码数量（最多 3 个）
 * 3. 磁盘缓存 150MB
 * 4. 禁用网络（纯本地应用）
 * 
 * 性能优化关键点：
 * - 限制并发解码可以减少内存峰值和 CPU 竞争
 * - 使用固定大小的线程池避免线程爆炸
 */
object CoilSetup {

    @Volatile
    private var imageLoader: ImageLoader? = null
    
    // 限制并发解码数量的调度器（最多 3 个并发解码）
    // 这可以防止大量图片同时解码导致的内存压力和 CPU 竞争
    private val limitedDispatcher: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    }

    /**
     * 获取单例 ImageLoader
     *
     * 双重检查锁定确保线程安全
     */
    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: createImageLoader(context.applicationContext).also {
                imageLoader = it
            }
        }
    }

    /**
     * 创建高性能 ImageLoader
     */
    private fun createImageLoader(context: Context): ImageLoader {
        val availableMemory = getAvailableMemory(context)
        // 使用可用内存的 25% 作为图片缓存（从 30% 降低以减少内存压力）
        val memoryCacheSize = (availableMemory * 0.25).toLong().coerceIn(
            minimumValue = 32L * 1024 * 1024,  // 最小 32MB（从 48MB 降低）
            maximumValue = 256L * 1024 * 1024  // 最大 256MB（从 384MB 降低）
        )

        return ImageLoader.Builder(context)
            // ========== 并发限制 ==========
            // 使用限制并发数的调度器，避免同时解码太多图片
            .dispatcher(limitedDispatcher)
            .fetcherDispatcher(limitedDispatcher)
            .decoderDispatcher(limitedDispatcher)
            // ========== 内存缓存配置 ==========
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(memoryCacheSize.toInt())
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            // ========== 磁盘缓存配置 ==========
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150MB（从 200MB 降低）
                    .build()
            }
            // ========== 缓存策略 ==========
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // ========== Bitmap 配置 ==========
            // 使用 ARGB_8888 以确保高质量和正确的透明度支持
            // 虽然耗损更多内存 (32位)，但对于动图透明背景是必须的
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            // 允许 RGB_565 用于不需要透明度的场景（请求时可单独指定）
            .allowRgb565(true)
            // ========== 性能优化 ==========
            // 允许硬件位图（Android 8.0+）- 更快的渲染
            .allowHardware(true)
            // 减少交叉淡入时间 - 更快显示
            .crossfade(80)
            // 尊重缓存头
            .respectCacheHeaders(false)
            // ========== 禁用网络 + 动画支持 ==========
            .components {
                // 添加动画 GIF/WebP 支持
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    /**
     * 获取设备可用内存（字节）
     */
    private fun getAvailableMemory(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // 使用较保守的估算：取 availMem 和 memoryClass 的较小值
        val memoryClass = activityManager.memoryClass * 1024L * 1024L
        return minOf(memoryInfo.availMem, memoryClass)
    }

    /**
     * 清除所有缓存
     */
    fun clearCache(context: Context) {
        getImageLoader(context).apply {
            memoryCache?.clear()
            diskCache?.clear()
        }
    }

    /**
     * 获取缓存大小信息（用于调试）
     */
    fun getCacheStats(context: Context): CacheStats {
        val loader = getImageLoader(context)
        return CacheStats(
            memoryCacheSize = loader.memoryCache?.size ?: 0,
            memoryCacheMaxSize = loader.memoryCache?.maxSize ?: 0,
            diskCacheSize = loader.diskCache?.size ?: 0,
            diskCacheMaxSize = loader.diskCache?.maxSize ?: 0
        )
    }

    data class CacheStats(
        val memoryCacheSize: Int,
        val memoryCacheMaxSize: Int,
        val diskCacheSize: Long,
        val diskCacheMaxSize: Long
    )
}
