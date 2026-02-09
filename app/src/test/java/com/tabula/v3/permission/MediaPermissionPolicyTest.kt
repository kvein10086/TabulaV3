package com.tabula.v3.permission

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionPolicyTest {

    @Test
    fun requiredPermissions_sdk34_includesPartialAccessPermission() {
        val permissions = MediaPermissionPolicy.requiredPermissionsForSdk(34)
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ),
            permissions
        )
    }

    @Test
    fun hasMediaReadPermission_sdk34_trueWhenOnlySelectedPhotosGranted() {
        val result = MediaPermissionPolicy.hasMediaReadPermission(34) { permission ->
            permission == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        }

        assertTrue(result)
    }

    @Test
    fun hasMediaReadPermission_sdk33_requiresReadMediaImages() {
        val granted = MediaPermissionPolicy.hasMediaReadPermission(33) { permission ->
            permission == Manifest.permission.READ_MEDIA_IMAGES
        }
        val denied = MediaPermissionPolicy.hasMediaReadPermission(33) { _ -> false }

        assertTrue(granted)
        assertFalse(denied)
    }

    @Test
    fun isAnyRequestedPermissionGranted_returnsTrueWhenAtLeastOneGranted() {
        val result = MediaPermissionPolicy.isAnyRequestedPermissionGranted(
            mapOf(
                Manifest.permission.READ_MEDIA_IMAGES to false,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to true
            )
        )

        assertTrue(result)
    }
}
