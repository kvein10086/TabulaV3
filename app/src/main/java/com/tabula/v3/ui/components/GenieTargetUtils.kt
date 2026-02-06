package com.tabula.v3.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal fun computeTrashTargetInRoot(
    trashButtonBounds: Rect,
    fallbackTarget: Offset
): Offset {
    return if (trashButtonBounds != Rect.Zero) {
        Offset(trashButtonBounds.center.x, trashButtonBounds.bottom)
    } else {
        fallbackTarget
    }
}

internal fun computeTagTargetInRoot(
    tagBounds: Rect?,
    fallbackTarget: Offset
): Offset {
    return if (tagBounds != null && tagBounds != Rect.Zero) {
        Offset(tagBounds.center.x, tagBounds.top)
    } else {
        fallbackTarget
    }
}
