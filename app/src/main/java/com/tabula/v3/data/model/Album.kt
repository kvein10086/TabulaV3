package com.tabula.v3.data.model

import java.util.UUID

/**
 * 同步模式枚举
 */
enum class SyncMode {
    /** 复制：在系统相册创建副本，保留原图位置 */
    COPY,
    /** 移动：将图片移动到系统相册，原位置不再存在 */
    MOVE
}

/**
 * 自定义相册（图集）数据模型
 *
 * 用于在 Tabula 中对照片进行快速归类整理。
 * 每个相册可以包含多张照片，一张照片也可以属于多个相册。
 *
 * @param id 唯一标识符（UUID）
 * @param name 相册名称
 * @param coverImageId 封面图片的 MediaStore ID（可选，无图片时为 null）
 * @param color 相册主题色/标签背景色（ARGB Long，可选）
 * @param textColor 标签文字颜色（ARGB Long，可选，默认自动根据背景色计算）
 * @param emoji 相册图标 Emoji（可选，如 "🌅"）- 已弃用
 * @param order 排序权重（越小越靠前）
 * @param createdAt 创建时间戳（毫秒）
 * @param imageCount 相册内图片数量（缓存值，便于显示）
 * @param systemAlbumPath 对应系统相册的路径（如已同步到系统相册）
 * @param isSyncEnabled 是否启用同步到系统相册
 * @param syncMode 同步模式：COPY（复制）或 MOVE（移动）
 */
data class Album(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coverImageId: Long? = null,
    val color: Long? = null,
    val textColor: Long? = null,
    val emoji: String? = null,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val imageCount: Int = 0,
    val systemAlbumPath: String? = null,
    val isSyncEnabled: Boolean = false,
    val syncMode: SyncMode = SyncMode.MOVE  // 默认移动模式
) {
    companion object {
        /**
         * 预设的相册颜色选项（Material Design 3 风格）
         */
        val PRESET_COLORS = listOf(
            0xFFE57373, // Red 300
            0xFFFF8A65, // Deep Orange 300
            0xFFFFD54F, // Amber 300
            0xFF81C784, // Green 300
            0xFF4FC3F7, // Light Blue 300
            0xFF7986CB, // Indigo 300
            0xFFBA68C8, // Purple 300
            0xFFF06292, // Pink 300
            0xFF90A4AE, // Blue Grey 300
            0xFFA1887F  // Brown 300
        )

        /**
         * 预设的相册 Emoji 选项
         */
        val PRESET_EMOJIS = listOf(
            "📷", "🌅", "🏠", "👨‍👩‍👧‍👦", "🎉",
            "✈️", "🍔", "🐱", "🌸", "⭐",
            "💼", "🎮", "📚", "🎵", "💝"
        )
    }
}
