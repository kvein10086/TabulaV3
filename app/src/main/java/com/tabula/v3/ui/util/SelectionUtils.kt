package com.tabula.v3.ui.util

fun toggleSelection(selected: Set<String>, id: String): Set<String> {
    return if (id in selected) selected - id else selected + id
}

fun selectAllOrClear(allIds: List<String>, selected: Set<String>): Set<String> {
    val allSet = allIds.toSet()
    return if (allSet.isNotEmpty() && selected.containsAll(allSet)) emptySet() else allSet
}

fun clampSelectionToVisible(selected: Set<String>, visible: Set<String>): Set<String> {
    return selected.intersect(visible)
}
