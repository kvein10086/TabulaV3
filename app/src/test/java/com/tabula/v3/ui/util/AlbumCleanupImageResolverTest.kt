package com.tabula.v3.ui.util

import android.net.Uri
import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumCleanupImageResolverTest {
    @Test
    fun resolvesImagesByAlbumPathBucket() {
        val album = Album(
            name = "Trip",
            systemAlbumPath = "Pictures/Tabula/Trip"
        )
        val match = ImageFile(
            id = 1L,
            uri = Uri.parse("content://media/external/images/media/1"),
            displayName = "a.jpg",
            dateModified = 0L,
            size = 100L,
            width = 100,
            height = 100,
            bucketDisplayName = "Trip"
        )
        val other = ImageFile(
            id = 2L,
            uri = Uri.parse("content://media/external/images/media/2"),
            displayName = "b.jpg",
            dateModified = 0L,
            size = 100L,
            width = 100,
            height = 100,
            bucketDisplayName = "Other"
        )

        val result = resolveAlbumCleanupImages(album, listOf(match, other))

        assertEquals(listOf(match), result)
    }

    @Test
    fun returnsEmptyWhenAlbumPathMissing() {
        val album = Album(name = "Empty", systemAlbumPath = null)
        val image = ImageFile(
            id = 3L,
            uri = Uri.parse("content://media/external/images/media/3"),
            displayName = "c.jpg",
            dateModified = 0L,
            size = 100L,
            width = 100,
            height = 100,
            bucketDisplayName = "Empty"
        )

        val result = resolveAlbumCleanupImages(album, listOf(image))

        assertEquals(emptyList<ImageFile>(), result)
    }
}
