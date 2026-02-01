package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabula.v3.ui.theme.LocalIsDarkTheme
import com.tabula.v3.ui.util.HapticFeedback

/**
 * 模式切换器组件
 * 
 * 用于在照片浏览模式和图集管理模式之间切换的胶囊形切换按钮。
 * 采用 iOS 风格的分段控件设计。
 */
@Composable
fun ModeToggle(
    isAlbumMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 容器背景色：iOS 风格 - 高透明度，让背景内容透过来
    val containerTint = if (isDarkTheme) {
        Color(0xFFFFFFFF).copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    // 高光：深色模式下禁用，保持扁平 2D 风格
    val highlightBrush = if (isDarkTheme) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Transparent)
        )
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }
    
    // 边框：深色模式下几乎无边框，保持扁平
    val borderBrush = if (isDarkTheme) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent
            )
        )
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.8f),
                Color.White.copy(alpha = 0.2f)
            )
        )
    }

    // 滑块颜色：iOS 风格 - 深色滑块与透明容器形成对比
    val selectedBgColor = if (isDarkTheme) {
        Color(0xFF48484A)
    } else {
        Color(0xFFF2F2F7).copy(alpha = 0.9f)
    }
    
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val unselectedTextColor = if (isDarkTheme) Color.Gray else Color.Gray

    // 滑块动画
    val segmentWidth = 80.dp
    // 字体排版
    val fontSize = 15.sp

    val offsetX by animateDpAsState(
        targetValue = if (isAlbumMode) segmentWidth else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "toggle_offset"
    )

    // 深色模式下减少阴影，保持扁平
    val shadowElevation = if (isDarkTheme) 2.dp else 12.dp
    val shadowAlpha = if (isDarkTheme) 0.05f else 0.15f
    
    AdaptiveGlass(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(100),
                spotColor = Color.Black.copy(alpha = shadowAlpha),
                ambientColor = Color.Black.copy(alpha = shadowAlpha * 0.6f)
            ),
        shape = RoundedCornerShape(100),
        blurRadius = if (isDarkTheme) 20.dp else 34.dp,
        tint = containerTint,
        borderBrush = borderBrush,
        borderWidth = if (isDarkTheme) 0.dp else 1.dp,
        highlightBrush = highlightBrush,
        noiseAlpha = if (isDarkTheme) 0f else 0.06f,
        backdropConfig = BackdropLiquidGlassConfig.Bar.copy(cornerRadius = 100.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
        // 滑动选中背景 - 深色模式下无阴影，保持扁平
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(segmentWidth)
                .then(
                    if (isDarkTheme) Modifier else Modifier.shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(100),
                        spotColor = Color.Black.copy(alpha = 0.1f)
                    )
                )
                .clip(RoundedCornerShape(100))
                .background(selectedBgColor)
                .padding(vertical = 8.dp)
        ) {
             Text(text = " ", fontSize = fontSize) // Placeholder
        }

        // 按钮
        Row {
            // 卡片模式
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .clip(RoundedCornerShape(100))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isAlbumMode) {
                            HapticFeedback.lightTap(context)
                            onModeChange(false)
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "卡片",
                    fontSize = fontSize,
                    fontWeight = if (!isAlbumMode) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (!isAlbumMode) textColor else unselectedTextColor
                )
            }

            // 图库模式
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .clip(RoundedCornerShape(100))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isAlbumMode) {
                            HapticFeedback.lightTap(context)
                            onModeChange(true)
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "图库",
                    fontSize = fontSize,
                    fontWeight = if (isAlbumMode) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isAlbumMode) textColor else unselectedTextColor
                )
            }
        }
        }
    }
}

