package com.tabula.v3.ui.components.quickaction

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 安全区域数据类
 */
data class SafeArea(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val screenWidth: Float,
    val screenHeight: Float
) {
    fun clamp(x: Float, y: Float): Offset {
        return Offset(
            x.coerceIn(minX, maxX),
            y.coerceIn(minY, maxY)
        )
    }
    
    fun contains(x: Float, y: Float): Boolean {
        return x in minX..maxX && y in minY..maxY
    }
    
    fun toPercentage(x: Float, y: Float): Pair<Float, Float> {
        val clampedX = x.coerceIn(minX, maxX)
        val clampedY = y.coerceIn(minY, maxY)
        return Pair(
            (clampedX / screenWidth).coerceIn(0f, 1f),
            (clampedY / screenHeight).coerceIn(0f, 1f)
        )
    }
    
    fun fromPercentage(xPercent: Float, yPercent: Float): Offset {
        val x = xPercent * screenWidth
        val y = yPercent * screenHeight
        return clamp(x, y)
    }
}

/**
 * 计算安全区域
 */
@Composable
fun rememberSafeArea(
    buttonSizeDp: Dp = 56.dp,
    horizontalPaddingDp: Dp = 16.dp,
    topOffsetDp: Dp = 100.dp,
    bottomOffsetDp: Dp = 180.dp
): SafeArea {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInsets = WindowInsets.safeDrawing
    
    return remember(density, configuration, layoutDirection) {
        val statusBarHeight = windowInsets.getTop(density).toFloat()
        val navigationBarHeight = windowInsets.getBottom(density).toFloat()
        
        val leftInset: Float
        val rightInset: Float
        if (layoutDirection == LayoutDirection.Rtl) {
            leftInset = windowInsets.getRight(density, layoutDirection).toFloat()
            rightInset = windowInsets.getLeft(density, layoutDirection).toFloat()
        } else {
            leftInset = windowInsets.getLeft(density, layoutDirection).toFloat()
            rightInset = windowInsets.getRight(density, layoutDirection).toFloat()
        }
        
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val buttonSizePx = with(density) { buttonSizeDp.toPx() }
        val horizontalPaddingPx = with(density) { horizontalPaddingDp.toPx() }
        val topOffsetPx = with(density) { topOffsetDp.toPx() }
        val bottomOffsetPx = with(density) { bottomOffsetDp.toPx() }
        
        SafeArea(
            minX = leftInset + horizontalPaddingPx,
            maxX = screenWidthPx - rightInset - buttonSizePx - horizontalPaddingPx,
            minY = statusBarHeight + topOffsetPx,
            maxY = screenHeightPx - navigationBarHeight - bottomOffsetPx,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx
        )
    }
}
