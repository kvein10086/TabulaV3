package com.tabula.v3.ui.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringDragSelectStateTest {
    @Test
    fun dragSelect_addsItemsDuringDrag() {
        val items = listOf("a", "b")
        var selected = emptySet<String>()
        var entered = false
        val state = StringDragSelectState(
            items = { items },
            itemKey = { it },
            selectedIds = { selected },
            onSelectionChange = { selected = it },
            onEnterSelectionMode = { entered = true }
        )

        state.registerGridPosition(Offset.Zero)
        state.registerItemBounds("a", ItemBounds(0f, 0f, 100f, 100f))
        state.registerItemBounds("b", ItemBounds(120f, 0f, 220f, 100f))

        state.onDragStart(Offset(10f, 10f), NoOpHaptic)
        assertTrue(entered)
        assertEquals(setOf("a"), selected)

        state.onDrag(Offset(150f, 10f), NoOpHaptic)
        assertEquals(setOf("a", "b"), selected)
    }

    @Test
    fun dragSelect_removesWhenInitiallySelected() {
        val items = listOf("a")
        var selected = setOf("a")
        val state = StringDragSelectState(
            items = { items },
            itemKey = { it },
            selectedIds = { selected },
            onSelectionChange = { selected = it },
            onEnterSelectionMode = { }
        )

        state.registerGridPosition(Offset.Zero)
        state.registerItemBounds("a", ItemBounds(0f, 0f, 100f, 100f))

        state.onDragStart(Offset(10f, 10f), NoOpHaptic)
        assertEquals(emptySet<String>(), selected)
    }
}

private object NoOpHaptic : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        // no-op for tests
    }
}
