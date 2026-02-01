package com.tabula.v3.ui.components.quickaction

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tabula.v3.ui.theme.TabulaColors
import com.tabula.v3.ui.util.HapticFeedback
import kotlin.math.roundToInt

/**
 * 快捷操作按钮 - 双向版本
 * 
 * 左半边点击 = 上一张
 * 右半边点击 = 下一张
 * 长按可拖动调整位置
 */
@Composable
fun QuickActionButton(
    visible: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    positionX: Float,
    positionY: Float,
    safeArea: SafeArea,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    val context = LocalContext.current
    
    // 计算实际位置
    val position = safeArea.fromPercentage(positionX, positionY)
    var currentX by remember { mutableFloatStateOf(position.x) }
    var currentY by remember { mutableFloatStateOf(position.y) }
    
    // 位置变化时更新
    androidx.compose.runtime.LaunchedEffect(positionX, positionY) {
        val newPos = safeArea.fromPercentage(positionX, positionY)
        currentX = newPos.x
        currentY = newPos.y
    }
    
    // 动画
    val animatedX by animateFloatAsState(currentX, spring(stiffness = Spring.StiffnessMedium))
    val animatedY by animateFloatAsState(currentY, spring(stiffness = Spring.StiffnessMedium))
    
    // 拖动状态
    var isDragging by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1.0f
    )
    
    // 按钮交互状态
    val leftInteractionSource = remember { MutableInteractionSource() }
    val rightInteractionSource = remember { MutableInteractionSource() }
    val isLeftPressed by leftInteractionSource.collectIsPressedAsState()
    val isRightPressed by rightInteractionSource.collectIsPressedAsState()
    
    Box(
        modifier = modifier
            .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        dragStartPosition = Offset(currentX, currentY)
                        HapticFeedback.heavyTap(context)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = currentX + dragAmount.x
                        val newY = currentY + dragAmount.y
                        val clamped = safeArea.clamp(newX, newY)
                        currentX = clamped.x
                        currentY = clamped.y
                    },
                    onDragEnd = {
                        val (xPercent, yPercent) = safeArea.toPercentage(currentX, currentY)
                        onPositionChanged(xPercent, yPercent)
                        HapticFeedback.lightTap(context)
                        isDragging = false
                    },
                    onDragCancel = {
                        currentX = dragStartPosition.x
                        currentY = dragStartPosition.y
                        isDragging = false
                    }
                )
            }
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：上一张
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = leftInteractionSource,
                        indication = null,
                        enabled = hasPrevious && !isDragging
                    ) {
                        HapticFeedback.lightTap(context)
                        onPrevious()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "上一张",
                    tint = if (hasPrevious) {
                        if (isLeftPressed) TabulaColors.EyeGold else Color.DarkGray
                    } else {
                        Color.LightGray
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // 分隔线
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 24.dp)
                    .background(Color.LightGray.copy(alpha = 0.5f))
            )
            
            // 右侧：下一张
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = rightInteractionSource,
                        indication = null,
                        enabled = hasNext && !isDragging
                    ) {
                        HapticFeedback.lightTap(context)
                        onNext()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "下一张",
                    tint = if (hasNext) {
                        if (isRightPressed) TabulaColors.EyeGold else Color.DarkGray
                    } else {
                        Color.LightGray
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
