package com.tabula.v3.data.model

/**
 * 图集存储信息
 * 
 * @param bucketName 图集/相册名称
 * @param storageSize 占用空间大小（字节）
 * @param imageCount 图片数量
 * @param previousStorageSize 上次扫描的大小（字节），用于计算变化
 */
data class AlbumStorageInfo(
    val bucketName: String,
    val storageSize: Long,
    val imageCount: Int,
    val previousStorageSize: Long? = null
) {
    /**
     * 存储变化量（正数为增加，负数为减少）
     */
    val storageChange: Long?
        get() = previousStorageSize?.let { storageSize - it }
    
    /**
     * 是否有变化
     */
    val hasChange: Boolean
        get() = storageChange != null && storageChange != 0L
}

/**
 * 存储扫描结果
 * 
 * 包含手机存储空间和各图集的存储占用信息
 * 
 * @param totalPhoneStorage 手机总存储空间（字节）
 * @param usedPhoneStorage 手机已用存储空间（字节）
 * @param totalImageStorage 图片总占用空间（字节）
 * @param albumStorageList 各图集存储详情列表
 * @param scanTimestamp 扫描时间戳（毫秒）
 * @param previousTotalImageStorage 上次扫描的图片总大小（字节）
 */
data class StorageScanResult(
    val totalPhoneStorage: Long,
    val usedPhoneStorage: Long,
    val totalImageStorage: Long,
    val albumStorageList: List<AlbumStorageInfo>,
    val scanTimestamp: Long,
    val previousTotalImageStorage: Long? = null
) {
    /**
     * 图片占手机总存储的比例 (0.0 ~ 1.0)
     */
    val imageStorageRatio: Float
        get() = if (totalPhoneStorage > 0) {
            (totalImageStorage.toFloat() / totalPhoneStorage).coerceIn(0f, 1f)
        } else 0f
    
    /**
     * 图片存储变化量
     */
    val totalStorageChange: Long?
        get() = previousTotalImageStorage?.let { totalImageStorage - it }
    
    /**
     * 是否有总量变化
     */
    val hasTotalChange: Boolean
        get() = totalStorageChange != null && totalStorageChange != 0L
    
    companion object {
        /**
         * 空结果（未扫描状态）
         */
        val EMPTY = StorageScanResult(
            totalPhoneStorage = 0L,
            usedPhoneStorage = 0L,
            totalImageStorage = 0L,
            albumStorageList = emptyList(),
            scanTimestamp = 0L,
            previousTotalImageStorage = null
        )
    }
}
