package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.data.model.Album
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 固定标签栏组件 - 用于"固定标签点击"模式
 *
 * 功能：
 * - 显示可点击的相册标签（双行水平滚动）
 * - 点击标签直接触发归类
 * - 固定显示在"x张待整理"下方
 * - 新建按钮在第一行第一个位置
 *
 * @param albums 相册列表
 * @param onAlbumClick 点击相册标签的回调
 * @param onCreateNewAlbumClick 点击新建按钮的回调
 * @param onTagPositionChanged 标签位置变化回调，用于 Genie 动画目标点
 * @param modifier 外部修饰符
 */
@Composable
fun FixedTagBar(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onCreateNewAlbumClick: () -> Unit,
    onTagPositionChanged: ((Int, TagPosition) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState1 = rememberScrollState()
    val scrollState2 = rememberScrollState()
    val isDarkTheme = LocalIsDarkTheme.current

    // 将相册按顺序分成两行：前半部分在第一行，后半部分在第二行
    val midPoint = (albums.size + 1) / 2  // 第一行多放一个（如果是奇数）
    val row1Albums = albums.take(midPoint)
    val row2Albums = albums.drop(midPoint)

    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第一行：新建按钮 + 前半部分相册
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState1)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 新建按钮 (索引 0)
            FixedTagChip(
                text = "新建",
                isCreateNew = true,
                onClick = onCreateNewAlbumClick,
                onPositioned = { coordinates ->
                    onTagPositionChanged?.invoke(0, coordinates)
                }
            )

            // 第一行相册标签（索引 0, 1, 2... 到 midPoint-1）
            row1Albums.forEachIndexed { index, album ->
                FixedTagChip(
                    text = album.name,
                    onClick = { onAlbumClick(album) },
                    onPositioned = { coordinates ->
                        onTagPositionChanged?.invoke(index + 1, coordinates)
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        // 第二行：后半部分相册（如果有的话）
        if (row2Albums.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState2)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 第二行相册标签（索引 midPoint, midPoint+1...）
                row2Albums.forEachIndexed { index, album ->
                    val originalIndex = midPoint + index
                    FixedTagChip(
                        text = album.name,
                        onClick = { onAlbumClick(album) },
                        onPositioned = { coordinates ->
                            onTagPositionChanged?.invoke(originalIndex + 1, coordinates)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * 固定标签 Chip - 玻璃拟态风格
 *
 * 设计特点：
 * - 点击有缩放动画
 * - 半透明玻璃效果
 * - 适配深色/浅色主题
 */
@Composable
private fun FixedTagChip(
    text: String,
    isCreateNew: Boolean = false,
    onClick: () -> Unit,
    onPositioned: ((TagPosition) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 缓存上一次的位置，只在位置真正变化时才触发回调，避免不必要的重组
    var lastBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_scale"
    )

    // Glassmorphism 颜色配置
    val fillColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.7f)
    }

    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    val chipTextColor = if (isDarkTheme) {
        Color.White
    } else {
        Color.Black
    }

    val cornerRadius = 14.dp

    // 使用 AdaptiveGlass 组件实现玻璃效果
    AdaptiveGlass(
        modifier = Modifier
            .scale(scale)
            .onGloballyPositioned { coordinates ->
                // 只在位置真正变化时才触发回调，避免不必要的状态更新
                val currentBounds = coordinates.boundsInRoot()
                if (lastBounds == null || lastBounds != currentBounds) {
                    lastBounds = currentBounds
                    onPositioned?.invoke(
                        TagPosition(
                            coordinates = coordinates,
                            updatedAt = android.os.SystemClock.uptimeMillis()
                        )
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                HapticFeedback.lightTap(context)
                onClick()
            },
        shape = RoundedCornerShape(cornerRadius),
        blurRadius = 20.dp,
        tint = fillColor,
        borderBrush = Brush.verticalGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = borderColor.alpha * 0.5f)
            )
        ),
        borderWidth = 0.5.dp,
        highlightBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDarkTheme) 0.08f else 0.2f),
                Color.White.copy(alpha = if (isDarkTheme) 0.02f else 0.05f)
            )
        ),
        noiseAlpha = 0f,
        backdropConfig = BackdropLiquidGlassConfig.Default.copy(cornerRadius = cornerRadius),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            if (isCreateNew) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = chipTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = text,
                color = chipTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                letterSpacing = 0.3.sp
            )
        }
    }
}
