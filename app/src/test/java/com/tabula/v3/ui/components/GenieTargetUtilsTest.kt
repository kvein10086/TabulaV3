package com.tabula.v3.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class GenieTargetUtilsTest {

    @Test
    fun trashTargetUsesBottomCenterWhenBoundsAvailable() {
        val bounds = Rect(left = 100f, top = 50f, right = 140f, bottom = 90f)
        val fallback = Offset(1f, 2f)

        val target = computeTrashTargetInRoot(bounds, fallback)

        assertEquals(120f, target.x, 0.001f)
        assertEquals(90f, target.y, 0.001f)
    }

    @Test
    fun tagTargetUsesTopCenterWhenBoundsAvailable() {
        val bounds = Rect(left = 20f, top = 40f, right = 60f, bottom = 80f)
        val fallback = Offset(1f, 2f)

        val target = computeTagTargetInRoot(bounds, fallback)

        assertEquals(40f, target.x, 0.001f)
        assertEquals(40f, target.y, 0.001f)
    }
}
