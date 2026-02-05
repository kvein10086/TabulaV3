package com.tabula.v3.ui.util

import com.tabula.v3.data.model.Album
import com.tabula.v3.data.model.ImageFile

fun resolveAlbumCleanupImages(album: Album, allImagesForCleanup: List<ImageFile>): List<ImageFile> {
    val albumPath = album.systemAlbumPath ?: return emptyList()
    val bucketName = albumPath.trimEnd('/').substringAfterLast('/')
    if (bucketName.isBlank()) return emptyList()
    return allImagesForCleanup.filter { image ->
        image.bucketDisplayName == bucketName
    }
}
