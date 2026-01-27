package com.tabula.v3.data.model

/**
 * 图片与相册的映射关系
 *
 * 记录一张图片被归类到了哪些相册。
 * 支持一张图片属于多个相册的场景。
 *
 * @param imageId MediaStore 图片 ID
 * @param imageUri 图片 URI 字符串（备份用，防止 ID 失效）
 * @param albumIds 关联的相册 ID 列表
 * @param addedAt 最后一次归类的时间戳
 */
data class AlbumMapping(
    val imageId: Long,
    val imageUri: String,
    val albumIds: List<String>,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 待撤销的相册操作
 *
 * 用于实现归类后的"撤销"功能。
 *
 * @param id 操作 ID
 * @param type 操作类型
 * @param imageId 涉及的图片 ID
 * @param albumId 涉及的相册 ID
 * @param timestamp 操作时间戳
 */
data class PendingAlbumAction(
    val id: String,
    val type: AlbumActionType,
    val imageId: Long,
    val albumId: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 相册操作类型
 */
enum class AlbumActionType {
    ADD,          // 添加到相册
    REMOVE,       // 从相册移除
    BULK_ADD,     // 批量添加
    BULK_REMOVE   // 批量移除
}
