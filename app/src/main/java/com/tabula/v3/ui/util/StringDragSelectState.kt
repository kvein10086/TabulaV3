package com.tabula.v3.ui.util

import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback

class StringDragSelectState<T>(
    private val items: () -> List<T>,
    private val itemKey: (T) -> String,
    private val selectedIds: () -> Set<String>,
    private val onSelectionChange: (Set<String>) -> Unit,
    private val onEnterSelectionMode: () -> Unit
) {
    internal val itemBounds = mutableMapOf<String, ItemBounds>()
    internal var gridPositionInWindow: Offset = Offset.Zero

    internal var isDragging by mutableStateOf(false)
    internal var dragStartId: String? = null
    internal var initialSelected: Boolean = false
    internal var lastHoveredId: String? = null
    internal var selectedDuringDrag = mutableSetOf<String>()

    fun registerGridPosition(position: Offset) {
        gridPositionInWindow = position
    }

    fun registerItemBounds(id: String, bounds: ItemBounds) {
        itemBounds[id] = bounds
    }

    fun onDragStart(startPosition: Offset, haptic: HapticFeedback) {
        val windowPosition = startPosition + gridPositionInWindow
        val hitItem = findItemAt(windowPosition)
        if (hitItem != null) {
            isDragging = true
            dragStartId = hitItem
            initialSelected = hitItem in selectedIds()
            lastHoveredId = hitItem
            selectedDuringDrag.clear()
            selectedDuringDrag.add(hitItem)

            onEnterSelectionMode()

            val newSelection = if (initialSelected) {
                selectedIds() - hitItem
            } else {
                selectedIds() + hitItem
            }
            onSelectionChange(newSelection)

            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun onDrag(position: Offset, haptic: HapticFeedback) {
        if (!isDragging) return
        val windowPosition = position + gridPositionInWindow
        val hitItem = findItemAt(windowPosition)
        if (hitItem != null && hitItem != lastHoveredId) {
            lastHoveredId = hitItem
            if (hitItem !in selectedDuringDrag) {
                selectedDuringDrag.add(hitItem)
                val newSelection = if (initialSelected) {
                    selectedIds() - hitItem
                } else {
                    selectedIds() + hitItem
                }
                onSelectionChange(newSelection)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun onDragEnd() {
        isDragging = false
        dragStartId = null
        lastHoveredId = null
        selectedDuringDrag.clear()
    }

    fun onDragCancel() {
        onDragEnd()
    }

    private fun findItemAt(position: Offset): String? {
        for ((id, bounds) in itemBounds) {
            if (bounds.contains(position)) {
                return id
            }
        }
        return null
    }
}

@Composable
fun <T> rememberStringDragSelectState(
    items: List<T>,
    itemKey: (T) -> String,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onEnterSelectionMode: () -> Unit
): StringDragSelectState<T> {
    return remember(items, selectedIds) {
        StringDragSelectState(
            items = { items },
            itemKey = itemKey,
            selectedIds = { selectedIds },
            onSelectionChange = onSelectionChange,
            onEnterSelectionMode = onEnterSelectionMode
        )
    }
}

@Composable
fun <T> Modifier.gridDragHandlerString(
    dragSelectState: StringDragSelectState<T>,
    isSelectionMode: Boolean = false
): Modifier {
    val haptic = LocalHapticFeedback.current

    return if (isSelectionMode) {
        this
            .onGloballyPositioned { coordinates ->
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

fun <T> Modifier.itemDragReceiverString(
    dragSelectState: StringDragSelectState<T>,
    itemId: String
): Modifier {
    return this.onGloballyPositioned { coordinates ->
        val position = coordinates.positionInWindow()
        val size = coordinates.size
        dragSelectState.registerItemBounds(
            itemId,
            ItemBounds(
                left = position.x,
                top = position.y,
                right = position.x + size.width,
                bottom = position.y + size.height
            )
        )
    }
}
