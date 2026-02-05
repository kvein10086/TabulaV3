package com.tabula.v3.ui.util

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 滑动多选状态管理器
 * 
 * 支持在 LazyVerticalGrid 或 LazyVerticalStaggeredGrid 中进行滑动多选。
 * 
 * 两种模式：
 * 1. 长按拖动模式 (gridDragHandlerLongPress)：长按后可以直接滑动选择，不松手
 * 2. 多选模式拖动 (gridDragHandler)：进入多选模式后才能拖动选择
 * 
 * 使用方式：
 * 1. 创建 DragSelectState
 * 2. 使用 gridDragHandlerLongPress 修饰符实现长按滑动多选
 * 3. 在每个 item 上注册位置（使用 registerItemBoundsInWindow）
 */
class DragSelectState<T>(
    private val items: () -> List<T>,
    private val itemKey: (T) -> Long,
    private val selectedIds: () -> Set<Long>,
    private val onSelectionChange: (Set<Long>) -> Unit,
    private val onEnterSelectionMode: () -> Unit
) {
    // 记录每个 item 的位置（窗口坐标系）
    internal val itemBounds = mutableMapOf<Long, ItemBounds>()
    
    // Grid 在窗口中的位置
    internal var gridPositionInWindow: Offset = Offset.Zero
    
    // 拖动状态
    internal var isDragging by mutableStateOf(false)
    internal var dragStartId: Long? = null
    internal var initialSelected: Boolean = false
    internal var lastHoveredId: Long? = null
    internal var selectedDuringDrag = mutableSetOf<Long>()
    
    /**
     * 记录 Grid 在窗口中的位置
     */
    fun registerGridPosition(position: Offset) {
        gridPositionInWindow = position
    }
    
    /**
     * 记录 item 位置（窗口坐标系）
     */
    fun registerItemBounds(id: Long, bounds: ItemBounds) {
        itemBounds[id] = bounds
    }
    
    /**
     * 开始拖动
     */
    fun onDragStart(startPosition: Offset, haptic: HapticFeedback) {
        // 将触摸位置转换为窗口坐标
        val windowPosition = startPosition + gridPositionInWindow
        
        // 查找起始位置对应的 item
        val hitItem = findItemAt(windowPosition)
        if (hitItem != null) {
            isDragging = true
            dragStartId = hitItem
            initialSelected = hitItem in selectedIds()
            lastHoveredId = hitItem
            selectedDuringDrag.clear()
            selectedDuringDrag.add(hitItem)
            
            // 进入多选模式
            onEnterSelectionMode()
            
            // 根据起始项的状态决定选中或取消选中
            val newSelection = if (initialSelected) {
                selectedIds() - hitItem
            } else {
                selectedIds() + hitItem
            }
            onSelectionChange(newSelection)
            
            // 触发触觉反馈
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    /**
     * 拖动中
     */
    fun onDrag(position: Offset, haptic: HapticFeedback) {
        if (!isDragging) return
        
        // 将触摸位置转换为窗口坐标
        val windowPosition = position + gridPositionInWindow
        
        val hitItem = findItemAt(windowPosition)
        if (hitItem != null && hitItem != lastHoveredId) {
            lastHoveredId = hitItem
            
            // 检查是否已经在这次拖动中处理过
            if (hitItem !in selectedDuringDrag) {
                selectedDuringDrag.add(hitItem)
                
                // 根据起始项的状态决定选中或取消选中
                val newSelection = if (initialSelected) {
                    selectedIds() - hitItem
                } else {
                    selectedIds() + hitItem
                }
                onSelectionChange(newSelection)
                
                // 触发轻触觉反馈
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    
    /**
     * 结束拖动
     */
    fun onDragEnd() {
        isDragging = false
        dragStartId = null
        lastHoveredId = null
        selectedDuringDrag.clear()
    }
    
    /**
     * 取消拖动
     */
    fun onDragCancel() {
        onDragEnd()
    }
    
    /**
     * 查找指定位置的 item
     */
    private fun findItemAt(position: Offset): Long? {
        for ((id, bounds) in itemBounds) {
            if (bounds.contains(position)) {
                return id
            }
        }
        return null
    }
}

/**
 * Item 位置信息
 */
data class ItemBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(position: Offset): Boolean {
        return position.x >= left && position.x <= right &&
               position.y >= top && position.y <= bottom
    }
}

/**
 * 创建并记住 DragSelectState
 */
@Composable
fun <T> rememberDragSelectState(
    items: List<T>,
    itemKey: (T) -> Long,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onEnterSelectionMode: () -> Unit
): DragSelectState<T> {
    return remember(items, selectedIds) {
        DragSelectState(
            items = { items },
            itemKey = itemKey,
            selectedIds = { selectedIds },
            onSelectionChange = onSelectionChange,
            onEnterSelectionMode = onEnterSelectionMode
        )
    }
}

/**
 * Grid 滑动多选手势处理修饰符（多选模式下）
 * 
 * 添加到 LazyVerticalGrid 或 LazyVerticalStaggeredGrid 上。
 * 只有在多选模式下 (isSelectionMode = true) 才会启用拖动选择功能。
 * 
 * @param dragSelectState 滑动多选状态
 * @param isSelectionMode 是否处于多选模式，只有在多选模式下才会启用拖动选择
 */
@Composable
fun <T> Modifier.gridDragHandler(
    dragSelectState: DragSelectState<T>,
    isSelectionMode: Boolean = false
): Modifier {
    val haptic = LocalHapticFeedback.current
    
    return if (isSelectionMode) {
        this
            .onGloballyPositioned { coordinates ->
                // 记录 Grid 在窗口中的位置
                dragSelectState.registerGridPosition(coordinates.positionInWindow())
            }
            .pointerInput(dragSelectState, isSelectionMode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragSelectState.onDragStart(offset, haptic)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragSelectState.onDrag(change.position, haptic)
                    },
                    onDragEnd = {
                        dragSelectState.onDragEnd()
                    },
                    onDragCancel = {
                        dragSelectState.onDragCancel()
                    }
                )
            }
    } else {
        this
    }
}

/**
 * Grid 长按拖动多选手势处理修饰符
 * 
 * 添加到 LazyVerticalGrid 或 LazyVerticalStaggeredGrid 上。
 * 支持长按后直接滑动选择多个 item（不松手）。
 * 
 * 注意：使用此修饰符后，不需要在 item 上设置 onLongClick，
 * 因为长按事件会被 Grid 捕获用于启动滑动选择。
 * 
 * @param dragSelectState 滑动多选状态
 * @param enabled 是否启用
 */
@Composable
fun <T> Modifier.gridDragHandlerLongPress(
    dragSelectState: DragSelectState<T>,
    enabled: Boolean = true
): Modifier {
    val haptic = LocalHapticFeedback.current
    
    return if (enabled) {
        this
            .onGloballyPositioned { coordinates ->
                // 记录 Grid 在窗口中的位置
                dragSelectState.registerGridPosition(coordinates.positionInWindow())
            }
            .pointerInput(dragSelectState, enabled) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragSelectState.onDragStart(offset, haptic)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragSelectState.onDrag(change.position, haptic)
                    },
                    onDragEnd = {
                        dragSelectState.onDragEnd()
                    },
                    onDragCancel = {
                        dragSelectState.onDragCancel()
                    }
                )
            }
    } else {
        this
    }
}

/**
 * Item 位置注册修饰符
 * 
 * 添加到每个 Grid item 上，用于记录 item 的位置
 */
fun <T> Modifier.itemDragReceiver(
    dragSelectState: DragSelectState<T>,
    itemId: Long
): Modifier {
    return this.onGloballyPositioned { coordinates ->
        val parentCoordinates = coordinates.parentLayoutCoordinates
        if (parentCoordinates != null) {
            // 获取相对于父容器（Grid）的位置
            val positionInParent = parentCoordinates.localPositionOf(coordinates, Offset.Zero)
            val size = coordinates.size
            
            dragSelectState.registerItemBounds(
                itemId,
                ItemBounds(
                    left = positionInParent.x,
                    top = positionInParent.y,
                    right = positionInParent.x + size.width,
                    bottom = positionInParent.y + size.height
                )
            )
        }
    }
}
