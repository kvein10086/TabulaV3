package com.tabula.v3.data.repository

import android.content.Context
import com.tabula.v3.data.cache.ImageHashCache
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.RecommendMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片推荐引擎
 * 
 * 支持两种推荐模式：
 * 1. 随机漫步模式：真正随机抽取照片，抽取过的照片会进入冷却期，短期内不会再被抽中
 * 2. 相似推荐模式：使用多维度聚类算法检测相似照片组，整组呈现给用户
 * 
 * 两种模式使用独立的冷却机制：
 * - 随机漫步：以单张照片为单位冷却
 * - 相似推荐：以相似组为单位冷却
 */
class RecommendationEngine(
    private val context: Context
) {
    private val preferences = AppPreferences(context)
    private val hashCache = ImageHashCache.getInstance(context)
    private val similarGroupDetector = SimilarGroupDetector(context, hashCache)
    
    // 缓存检测到的相似组（避免重复检测）
    private var cachedSimilarGroups: List<SimilarGroup>? = null
    private var cacheTimestamp: Long = 0L
    private val CACHE_VALIDITY_MS = 5 * 60 * 1000L  // 缓存有效期 5 分钟
    
    /**
     * 根据当前推荐模式获取一批照片
     * 
     * @param allImages 所有可用照片
     * @param batchSize 批次大小（仅随机模式使用，相似模式返回动态大小）
     * @param anchorImage 锚点照片（已废弃，保留参数兼容性）
     * @return 推荐的照片列表
     */
    suspend fun getRecommendedBatch(
        allImages: List<ImageFile>,
        batchSize: Int,
        anchorImage: ImageFile? = null
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        if (allImages.isEmpty()) return@withContext emptyList()
        
        when (preferences.recommendMode) {
            RecommendMode.RANDOM_WALK -> {
                // 清理过期的抽取记录
                preferences.cleanupExpiredPickRecords()
                getRandomWalkBatch(allImages, batchSize)
            }
            RecommendMode.SIMILAR -> {
                // 清理过期的相似组记录
                preferences.cleanupExpiredSimilarGroupRecords()
                getSimilarBatch(allImages)
            }
        }
    }
    
    /**
     * 获取下一个相似组
     * 
     * 相似推荐模式专用方法，返回完整的相似组对象。
     * 
     * @param allImages 所有可用照片
     * @return 下一个待处理的相似组，如果没有则返回 null
     */
    suspend fun getNextSimilarGroup(allImages: List<ImageFile>): SimilarGroup? = 
        withContext(Dispatchers.IO) {
            if (allImages.isEmpty()) return@withContext null
            
            // 清理过期记录
            preferences.cleanupExpiredSimilarGroupRecords()
            
            // 获取相似组（使用缓存）
            val groups = getOrDetectSimilarGroups(allImages)
            
            // 获取冷却中的组ID
            val cooldownGroupIds = preferences.getSimilarGroupCooldownIds()
            
            // 过滤掉冷却中的组
            val availableGroups = groups.filter { it.id !in cooldownGroupIds }
            
            // 返回优先级最高的组（按大小降序已排好）
            availableGroups.firstOrNull()
        }
    
    /**
     * 标记相似组已处理完成
     * 
     * 处理完一个相似组后调用此方法，使该组进入冷却期。
     * 
     * @param group 已处理的相似组
     */
    fun markSimilarGroupProcessed(group: SimilarGroup) {
        preferences.recordSimilarGroupProcessed(group.id)
    }
    
    /**
     * 清除相似组缓存
     * 
     * 当图片列表发生变化时调用（如删除图片后）
     */
    fun invalidateSimilarGroupCache() {
        cachedSimilarGroups = null
        cacheTimestamp = 0L
    }
    
    /**
     * 随机漫步模式
     * 
     * 真正随机抽取照片，但排除在冷却期内的照片。
     * 如果可用照片不足，则从冷却期内的照片中补充（按冷却期剩余时间排序）。
     */
    private fun getRandomWalkBatch(
        allImages: List<ImageFile>,
        batchSize: Int
    ): List<ImageFile> {
        // 一次性获取所有冷却中的图片ID
        val cooldownIds = preferences.getCooldownImageIds()
        
        // 分离可用照片和冷却中的照片
        val (cooldownImages, availableImages) = allImages.partition { 
            it.id in cooldownIds
        }
        
        val result = mutableListOf<ImageFile>()
        
        // 优先从可用照片中随机抽取
        val shuffledAvailable = availableImages.shuffled()
        result.addAll(shuffledAvailable.take(batchSize))
        
        // 如果可用照片不足，从冷却中的照片补充（优先选择冷却期即将结束的）
        if (result.size < batchSize && cooldownImages.isNotEmpty()) {
            val sortedCooldown = cooldownImages.sortedBy { 
                preferences.getImagePickedTimestamp(it.id) 
            }
            result.addAll(sortedCooldown.take(batchSize - result.size))
        }
        
        // 批量记录这批照片被抽取
        preferences.recordImagesPicked(result.map { it.id })
        
        return result
    }
    
    /**
     * 相似推荐模式（新算法）
     * 
     * 使用多维度聚类算法检测相似照片组：
     * 1. dHash 感知哈希 - 内容相似性
     * 2. 时间相近 - 连拍/多角度拍摄
     * 3. 尺寸/文件大小 - 同设备/同模式
     * 4. 同一相册
     * 
     * 返回完整的一组相似照片（动态大小），按时间顺序排列。
     */
    private suspend fun getSimilarBatch(allImages: List<ImageFile>): List<ImageFile> {
        // 获取下一个相似组
        val group = getNextSimilarGroup(allImages)
        
        if (group != null) {
            // 标记该组已处理（进入冷却）
            markSimilarGroupProcessed(group)
            android.util.Log.d(TAG, "Returning similar group: ${group.size} images, id=${group.id}")
            return group.images
        }
        
        // 如果没有可用的相似组，回退到随机模式
        android.util.Log.d(TAG, "No similar groups available, falling back to random")
        return getRandomWalkBatch(allImages, preferences.batchSize)
    }
    
    /**
     * 获取或检测相似组（带缓存）
     */
    private suspend fun getOrDetectSimilarGroups(allImages: List<ImageFile>): List<SimilarGroup> {
        val now = System.currentTimeMillis()
        
        // 检查缓存是否有效
        val cached = cachedSimilarGroups
        if (cached != null && now - cacheTimestamp < CACHE_VALIDITY_MS) {
            return cached
        }
        
        // 重新检测
        android.util.Log.d(TAG, "Detecting similar groups...")
        val groups = similarGroupDetector.detectSimilarGroups(allImages)
        
        // 更新缓存
        cachedSimilarGroups = groups
        cacheTimestamp = now
        
        android.util.Log.d(TAG, "Detected ${groups.size} similar groups")
        return groups
    }
    
    companion object {
        private const val TAG = "RecommendationEngine"
        
        @Volatile
        private var instance: RecommendationEngine? = null
        
        fun getInstance(context: Context): RecommendationEngine {
            return instance ?: synchronized(this) {
                instance ?: RecommendationEngine(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
}
