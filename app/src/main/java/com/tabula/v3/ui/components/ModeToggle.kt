package com.tabula.v3.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * 底部模式切换器
 * 
 * 类似 iOS 相册的 "照片 | 图集" 切换器
 * 用于在 "滑一滑"（卡片模式）和 "图集"（相册列表）之间切换
 */
@Composable
fun ModeToggle(
    isAlbumMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    
    // 颜色
    val containerColor = if (isDarkTheme) {
        Color(0xFF2C2C2E).copy(alpha = 0.9f)
    } else {
        Color(0xFFE5E5EA).copy(alpha = 0.9f)
    }
    
    val selectedBgColor = if (isDarkTheme) {
        Color(0xFF636366) // Slightly lighter in dark mode for contrast
    } else {
        Color.White
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(containerColor)
            .padding(2.dp), // Thinner padding
        contentAlignment = Alignment.CenterStart
    ) {
        // 滑动选中背景
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(segmentWidth)
                .clip(RoundedCornerShape(100))
                .background(selectedBgColor)
                .padding(vertical = 8.dp) // Height control
        ) {
             Text(text = " ", fontSize = fontSize) // Placeholder
        }

        // 按钮
        Row {
            // 照片
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
                    text = "照片",
                    fontSize = fontSize,
                    fontWeight = if (!isAlbumMode) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (!isAlbumMode) textColor else unselectedTextColor
                )
            }

            // 图集
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
                    text = "图集",
                    fontSize = fontSize,
                    fontWeight = if (isAlbumMode) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isAlbumMode) textColor else unselectedTextColor
                )
            }
        }
    }
}
