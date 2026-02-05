package com.tabula.v3.di

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import coil.size.Size
import com.tabula.v3.data.model.ImageFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 图片预加载管理器
 *
 * 针对卡片堆叠滑动场景优化：
 * - 预加载当前索引的前后各 N 张图片
 * - 使用较小的卡片尺寸（720px）加快解码速度
 * - 支持暂停/恢复预加载（滑动时暂停）
 * - 使用防抖避免频繁触发预加载
 *
 * @param context 应用上下文
 * @param preloadRange 预加载范围（前后各多少张），默认 1（更保守）
 */
class PreloadingManager(
    private val context: Context,
    private val preloadRange: Int = 1  // 从 2 降到 1，减少内存压力
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageLoader = CoilSetup.getImageLoader(context)

    // 记录已预加载的索引，避免重复加载（线程安全）
    private val preloadedIndices: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    
    // 保护并发操作的锁
    private val mutex = Mutex()

    // 上次预加载的中心索引
    @Volatile
    private var lastPreloadCenter: Int = -1
    
    // 预加载暂停状态（滑动时暂停）
    private val isPaused = AtomicBoolean(false)
    
    // 防抖任务
    private var debounceJob: Job? = null
    
    // 防抖延迟（毫秒）
    private val debounceDelay = 100L

    /**
     * 暂停预加载（在快速滑动时调用）
     */
    fun pause() {
        isPaused.set(true)
        debounceJob?.cancel()
    }
    
    /**
     * 恢复预加载
     */
    fun resume() {
        isPaused.set(false)
    }

    /**
     * 根据当前索引预加载相邻图片（带防抖）
     *
     * @param images 完整图片列表
     * @param currentIndex 当前显示的索引
     * @param cardSize 卡片尺寸（宽 x 高），用于精确加载
     */
    fun preloadAround(
        images: List<ImageFile>,
        currentIndex: Int,
        cardSize: IntSize
    ) {
        // 如果中心索引没变，跳过
        if (currentIndex == lastPreloadCenter) return
        lastPreloadCenter = currentIndex
        
        // 取消之前的防抖任务
        debounceJob?.cancel()
        
        // 使用防抖延迟执行预加载，避免快速滑动时触发大量加载
        debounceJob = scope.launch {
            delay(debounceDelay)
            
            // 如果被暂停，跳过
            if (isPaused.get()) return@launch
            
            mutex.withLock {
                // 计算需要预加载的索引范围
                val startIndex = (currentIndex - preloadRange).coerceAtLeast(0)
                val endIndex = (currentIndex + preloadRange).coerceAtMost(images.lastIndex)

                for (index in startIndex..endIndex) {
                    // 再次检查暂停状态
                    if (isPaused.get()) break
                    
                    // 跳过已预加载的
                    if (index in preloadedIndices) continue

                    val image = images.getOrNull(index) ?: continue
                    preloadImage(image, cardSize)
                    preloadedIndices.add(index)
                }

                // 清理过期的预加载记录（保留最近的范围）
                val keepRange = (currentIndex - preloadRange * 2)..(currentIndex + preloadRange * 2)
                val toRemove = preloadedIndices.filter { it !in keepRange }
                preloadedIndices.removeAll(toRemove.toSet())
            }
        }
    }

    /**
     * 预加载单张图片
     *
     * @param image 要预加载的图片
     * @param targetSize 目标尺寸（未使用，改为使用固定尺寸以匹配 ImageCard）
     */
    private suspend fun preloadImage(image: ImageFile, targetSize: IntSize) {
        // 如果被暂停，跳过
        if (isPaused.get()) return
        
        try {
            // 使用与 ImageCard 相同的缓存键格式
            val cacheKey = "card_${image.id}"
            
            // 优化：使用更小的尺寸（720px 而非 1080px）
            val loadSize = if (!image.hasDimensionInfo) {
                Size(540, 720)  // 从 810x1080 降到 540x720
            } else {
                val maxDimension = 720  // 从 1080 降到 720
                if (image.aspectRatio < 1f) {
                    val height = maxDimension
                    val width = (height * image.aspectRatio).toInt().coerceAtLeast(270)
                    Size(width, height)
                } else {
                    val width = maxDimension
                    val height = (width / image.aspectRatio).toInt().coerceAtLeast(270)
                    Size(width, height)
                }
            }
            
            val request = ImageRequest.Builder(context)
                .data(image.uri)
                .size(loadSize)
                // 使用与 ImageCard 相同的缓存键
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                // 禁用硬件位图，与 ImageCard 保持一致
                .allowHardware(false)
                .build()

            imageLoader.enqueue(request)

            Log.d(TAG, "预加载图片: ${image.displayName}")
        } catch (e: Exception) {
            Log.w(TAG, "预加载失败: ${image.displayName}", e)
        }
    }

    /**
     * 立即预加载指定索引的图片（优先级更高）
     *
     * @param images 图片列表
     * @param indices 要预加载的索引列表
     * @param cardSize 卡片尺寸
     */
    fun preloadImmediate(
        images: List<ImageFile>,
        indices: List<Int>,
        cardSize: IntSize
    ) {
        scope.launch {
            mutex.withLock {
                indices.forEach { index ->
                    val image = images.getOrNull(index) ?: return@forEach
                    preloadImage(image, cardSize)
                    preloadedIndices.add(index)
                }
            }
        }
    }

    /**
     * 重置预加载状态（例如切换相册时）
     */
    fun reset() {
        debounceJob?.cancel()
        scope.launch {
            mutex.withLock {
                preloadedIndices.clear()
                lastPreloadCenter = -1
            }
        }
    }

    companion object {
        private const val TAG = "PreloadingManager"
    }
}
