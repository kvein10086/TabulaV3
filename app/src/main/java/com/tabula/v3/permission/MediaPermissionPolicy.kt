package com.tabula.v3.permission

import android.Manifest

object MediaPermissionPolicy {

    fun requiredPermissionsForSdk(sdkInt: Int): Array<String> {
        return when {
            sdkInt >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            sdkInt >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasMediaReadPermission(
        sdkInt: Int,
        isGranted: (permission: String) -> Boolean
    ): Boolean {
        return when {
            sdkInt >= 34 -> {
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            sdkInt >= 33 -> isGranted(Manifest.permission.READ_MEDIA_IMAGES)
            else -> isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun isAnyRequestedPermissionGranted(result: Map<String, Boolean>): Boolean {
        return result.values.any { granted -> granted }
    }
}
