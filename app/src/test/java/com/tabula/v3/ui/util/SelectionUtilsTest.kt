package com.tabula.v3.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionUtilsTest {
    @Test
    fun toggleSelection_addsAndRemoves() {
        val selected = setOf("a")
        assertEquals(setOf("a", "b"), toggleSelection(selected, "b"))
        assertEquals(emptySet<String>(), toggleSelection(setOf("b"), "b"))
    }

    @Test
    fun selectAllOrClear_togglesBasedOnAllSelected() {
        val all = listOf("a", "b", "c")
        assertEquals(all.toSet(), selectAllOrClear(all, emptySet()))
        assertEquals(emptySet<String>(), selectAllOrClear(all, all.toSet()))
    }

    @Test
    fun clampSelectionToVisible_dropsHidden() {
        val visible = setOf("a", "c")
        val selected = setOf("a", "b")
        assertEquals(setOf("a"), clampSelectionToVisible(selected, visible))
    }
}
