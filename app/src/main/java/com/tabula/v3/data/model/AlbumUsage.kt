package com.tabula.v3.data.model

/**
 * 图集使用记录
 * 
 * 记录用户每次将照片归类到图集的操作，用于计算图集使用频率。
 * 
 * @param albumId 目标图集ID（即图集名称）
 * @param timestamp 操作时间戳（毫秒）
 * @param sessionId 会话标识，用于检测连续归类操作（可选）
 */
data class AlbumUsageRecord(
    val albumId: String,
    val timestamp: Long,
    val sessionId: String? = null
)

/**
 * 图集使用统计（计算后的缓存结果）
 * 
 * @param albumId 图集ID
 * @param weightedScore 加权得分（综合考虑频率和时间衰减）
 * @param lastUsedAt 最后使用时间戳
 * @param totalCount 窗口期内总使用次数
 */
data class AlbumUsageStats(
    val albumId: String,
    val weightedScore: Double,
    val lastUsedAt: Long,
    val totalCount: Int = 0
)
