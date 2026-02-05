package com.tabula.v3.ui.components

import android.net.Uri
import com.tabula.v3.data.model.ImageFile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class MonthInfoBuilderTest {
    @Test
    fun buildsMonthInfosInDescOrderAndPicksLatestCover() {
        val janOld = image(id = 1L, year = 2025, month = 1, day = 10, timeMillis = 1_000L)
        val janNew = image(id = 2L, year = 2025, month = 1, day = 20, timeMillis = 2_000L)
        val feb = image(id = 3L, year = 2025, month = 2, day = 5, timeMillis = 3_000L)
        val decPrev = image(id = 4L, year = 2024, month = 12, day = 31, timeMillis = 4_000L)

        val result = buildMonthInfos(listOf(janOld, feb, janNew, decPrev))

        assertEquals(listOf("2025-2", "2025-1", "2024-12"), result.map { it.id })

        val january = result[1]
        assertEquals(2, january.imageCount)
        assertEquals(janNew.id, january.coverImageId)
        assertEquals(listOf(janNew.id, janOld.id), january.images.map { it.id })
    }

    @Test
    fun returnsEmptyWhenNoImages() {
        val result = buildMonthInfos(emptyList())

        assertEquals(emptyList<MonthInfo>(), result)
    }

    private fun image(id: Long, year: Int, month: Int, day: Int, timeMillis: Long): ImageFile {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ImageFile(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            displayName = "img$id.jpg",
            dateModified = calendar.timeInMillis + timeMillis,
            size = 100L,
            width = 100,
            height = 100,
            bucketDisplayName = "Any"
        )
    }
}
