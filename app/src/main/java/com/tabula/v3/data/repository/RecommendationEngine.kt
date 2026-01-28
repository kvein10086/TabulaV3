package com.tabula.v3.data.repository

import android.content.Context
import com.tabula.v3.data.model.ImageFile
import com.tabula.v3.data.preferences.AppPreferences
import com.tabula.v3.data.preferences.RecommendMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 照片推荐引擎
 * 
 * 支持两种推荐模式：
 * 1. 随机漫步模式：真正随机抽取照片，抽取过的照片会进入冷却期，短期内不会再被抽中
 * 2. 相似推荐模式：优先推荐与当前照片相似的照片，帮助用户清理相似照片
 */
class RecommendationEngine(
    private val context: Context
) {
    private val preferences = AppPreferences(context)
    
    /**
     * 根据当前推荐模式获取一批照片
     * 
     * @param allImages 所有可用照片
     * @param batchSize 批次大小
     * @param anchorImage 锚点照片（用于相似推荐模式）
     * @return 推荐的照片列表
     */
    suspend fun getRecommendedBatch(
        allImages: List<ImageFile>,
        batchSize: Int,
        anchorImage: ImageFile? = null
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        if (allImages.isEmpty()) return@withContext emptyList()
        
        // 清理过期的抽取记录
        preferences.cleanupExpiredPickRecords()
        
        when (preferences.recommendMode) {
            RecommendMode.RANDOM_WALK -> getRandomWalkBatch(allImages, batchSize)
            RecommendMode.SIMILAR -> getSimilarBatch(allImages, batchSize, anchorImage)
        }
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
        // 一次性获取所有冷却中的图片ID（优化：避免为每张图片单独查询）
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
        
        // 批量记录这批照片被抽取（优化：使用批量写入）
        preferences.recordImagesPicked(result.map { it.id })
        
        return result
    }
    
    /**
     * 相似推荐模式
     * 
     * 使用多种启发式方法找出相似照片：
     * 1. 时间相近（连拍照片通常时间很近）
     * 2. 文件大小相近（相似照片通常大小相近）
     * 3. 尺寸相同（相同场景拍摄的照片尺寸通常相同）
     * 4. 来自同一相册
     * 
     * 优先返回相似度最高的图片，确保用户看到的确实是相似的照片
     */
    private suspend fun getSimilarBatch(
        allImages: List<ImageFile>,
        batchSize: Int,
        anchorImage: ImageFile?
    ): List<ImageFile> {
        if (allImages.isEmpty()) return emptyList()
        
        // 相似度阈值 - 低于此分数认为不相似
        val similarityThreshold = 30f
        
        // 如果没有锚点图片，随机选择一个作为起点
        // 优先从最近拍摄的照片中选择（更可能有连拍和相似照片）
        val anchor = anchorImage ?: run {
            // 按时间排序，从最近的 100 张照片中随机选择一个
            val recentImages = allImages.sortedByDescending { it.dateModified }.take(100)
            recentImages.randomOrNull() ?: allImages.randomOrNull() ?: return emptyList()
        }
        // 为了性能，只对时间上接近锚点的图片计算相似度
        // 先按时间排序，找到锚点位置，然后取前后各 500 张
        val sortedByTime = allImages.sortedBy { it.dateModified }
        val anchorIndex = sortedByTime.indexOfFirst { it.id == anchor.id }
        val startIndex = maxOf(0, anchorIndex - 500)
        val endIndex = minOf(sortedByTime.size, anchorIndex + 500)
        val candidateImages = sortedByTime.subList(startIndex, endIndex)
            .filter { it.id != anchor.id }
        
        // 计算候选照片与锚点的相似度分数
        val scoredImages = candidateImages
            .map { image ->
                val score = calculateSimilarityScore(anchor, image)
                image to score
            }
            .sortedByDescending { it.second }
        
        val result = mutableListOf(anchor)
        
        // 首先添加高相似度的图片（分数 >= 阈值）
        val highSimilarityImages = scoredImages.filter { it.second >= similarityThreshold }
        
        // 直接按相似度顺序添加（最相似的排在前面）
        highSimilarityImages.take(batchSize - 1).forEach { (image, _) ->
            result.add(image)
        }
        
        // 如果高相似度图片不够，从剩余图片中补充
        if (result.size < batchSize) {
            val remaining = scoredImages
                .map { it.first }
                .filter { it !in result }
                .take(batchSize - result.size)
            result.addAll(remaining)
        }
        
        return result.take(batchSize)
    }
    
    /**
     * 计算两张照片的相似度分数（0-100）
     */
    private fun calculateSimilarityScore(anchor: ImageFile, candidate: ImageFile): Float {
        var score = 0f
        
        // 1. 时间相近度（权重：40分）
        // 连拍照片通常在几秒内拍摄
        val timeDiffSeconds = abs(anchor.dateModified - candidate.dateModified) / 1000
        val timeScore = when {
            timeDiffSeconds < 5 -> 40f      // 5秒内：满分
            timeDiffSeconds < 30 -> 35f     // 30秒内：高分
            timeDiffSeconds < 60 -> 25f     // 1分钟内
            timeDiffSeconds < 300 -> 15f    // 5分钟内
            timeDiffSeconds < 3600 -> 8f    // 1小时内
            timeDiffSeconds < 86400 -> 3f   // 1天内
            else -> 0f
        }
        score += timeScore
        
        // 2. 尺寸相同（权重：25分）
        // 相同设备、相同模式拍摄的照片尺寸通常相同
        if (anchor.width == candidate.width && anchor.height == candidate.height) {
            score += 25f
        } else {
            // 尺寸相近也给部分分
            val aspectDiff = abs(anchor.aspectRatio - candidate.aspectRatio)
            if (aspectDiff < 0.05f) score += 15f
            else if (aspectDiff < 0.1f) score += 8f
        }
        
        // 3. 文件大小相近（权重：20分）
        // 相似场景的照片压缩后大小通常相近
        val sizeDiffPercent = abs(anchor.size - candidate.size).toFloat() / anchor.size.coerceAtLeast(1)
        val sizeScore = when {
            sizeDiffPercent < 0.05f -> 20f  // 差异小于5%
            sizeDiffPercent < 0.1f -> 15f   // 差异小于10%
            sizeDiffPercent < 0.2f -> 10f   // 差异小于20%
            sizeDiffPercent < 0.3f -> 5f    // 差异小于30%
            else -> 0f
        }
        score += sizeScore
        
        // 4. 同一相册（权重：15分）
        if (anchor.bucketDisplayName != null && 
            anchor.bucketDisplayName == candidate.bucketDisplayName) {
            score += 15f
        }
        
        return score
    }
    
    companion object {
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
