package com.tabula.v3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.Album
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 相册快速归类 Chips 组件
 *
 * 显示在卡片底部，支持横向滚动。
 * 点击相册 Chip 可将当前图片快速归类到对应相册。
 *
 * 设计特点：
 * - 透明背景，融入页面
 * - 精简的尺寸
 * - 流畅的点击动画
 * - 支持深色/浅色主题
 */
@Composable
fun AlbumChips(
    albums: List<Album>,
    selectedAlbumIds: Set<String>,
    onAlbumClick: (Album) -> Unit,
    onAddAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // 流式布局容器
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 4.dp), // 增加两侧留白
        horizontalArrangement = Arrangement.spacedBy(12.dp), // 增加 Chip 间距
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 相册 Chips
        albums.forEach { album ->
            AlbumChip(
                album = album,
                isSelected = album.id in selectedAlbumIds,
                onClick = { onAlbumClick(album) }
            )
        }

        // 添加相册按钮
        AddAlbumChip(onClick = onAddAlbumClick)
        
        // 末尾留白
        Spacer(modifier = Modifier.width(12.dp))
    }
}

/**
 * 单个相册 Chip - 极简艺术风格
 * 
 * 设计理念：
 * - 移除 Emoji，专注于文字本身
 * - 高对比度：选中状态使用纯黑/纯白，突出力度
 * - 留白：适度的内边距
 */
@Composable
private fun AlbumChip(
    album: Album,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chip_scale"
    )

    // 颜色系统 - 高级感配色
    // 选中：纯黑色背景 (Dark Mode 下为纯白)，文字反色
    // 未选中：极浅灰背景，深灰文字
    val targetBackgroundColor = when {
        isSelected -> if (isDarkTheme) Color.White else Color.Black
        isDarkTheme -> Color(0xFF1C1C1E)
        else -> Color(0xFFF2F2F7)
    }
    
    val targetContentColor = when {
        isSelected -> if (isDarkTheme) Color.Black else Color.White
        isDarkTheme -> Color(0xFF98989D)
        else -> Color(0xFF636366) // System Gray 2
    }

    val backgroundColor by animateColorAsState(targetBackgroundColor, label = "bg")
    val contentColor by animateColorAsState(targetContentColor, label = "content")

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape) // 保持圆润，现代感
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                HapticFeedback.lightTap(context)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp), // 更加宽敞的内边距
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = album.name,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.5.sp, // 微小的字间距，提升精致感
            maxLines = 1
        )
    }
}

/**
 * 添加相册按钮 - 极简风格
 */
@Composable
private fun AddAlbumChip(
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "add_scale"
    )

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val contentColor = if (isDarkTheme) Color(0xFF98989D) else Color(0xFF636366)

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                HapticFeedback.lightTap(context)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "新建",
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "新建",
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * 空状态提示
 */
@Composable
fun AlbumChipsEmpty(
    onAddAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    Row(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "创建相册来整理照片",
            color = textColor,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        AddAlbumChip(onClick = onAddAlbumClick)
    }
}
