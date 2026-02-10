package com.example.alakey

import com.example.alakey.data.FactEntity
import com.example.alakey.data.PodcastEntity
import com.example.alakey.domain.InformationModel
import org.junit.Assert.assertEquals
import org.junit.Test

class InformationModelTest {

    @Test
    fun `hydrate applies latest facts to base entity`() {
        val base = PodcastEntity(
            id = "test-id",
            title = "Test Podcast",
            episodeTitle = "Original Title",
            description = "Desc",
            imageUrl = "url",
            audioUrl = "audio"
        )

        val facts = listOf(
            FactEntity("test-id", InformationModel.ATTR_PROGRESS, "5000", tx = 100),
            FactEntity("test-id", InformationModel.ATTR_PROGRESS, "10000", tx = 200), // Latest progress
            FactEntity("test-id", InformationModel.ATTR_IN_QUEUE, "true", tx = 150),
            FactEntity("test-id", InformationModel.ATTR_DOWNLOADED, "true", tx = 50)
        )

        val hydrated = InformationModel.hydrate(base, facts)

        assertEquals(10000L, hydrated.progress)
        assertEquals(true, hydrated.isInQueue)
        assertEquals(true, hydrated.isDownloaded)
        assertEquals("Original Title", hydrated.episodeTitle) // Immutable core preserved
    }

    @Test
    fun `hydrate handles empty facts by returning base`() {
        val base = PodcastEntity(id = "test", title = "T", episodeTitle = "E", description = "D", imageUrl = "I", audioUrl = "A", progress = 123)
        val hydrated = InformationModel.hydrate(base, emptyList())
        assertEquals(base, hydrated)
    }

    @Test
    fun `hydrate handles malformed values gracefully`() {
        val base = PodcastEntity(id = "test", title = "T", episodeTitle = "E", description = "D", imageUrl = "I", audioUrl = "A", progress = 0)
        val facts = listOf(
            FactEntity("test", InformationModel.ATTR_PROGRESS, "not-a-number", tx = 100)
        )
        val hydrated = InformationModel.hydrate(base, facts)
        assertEquals(0L, hydrated.progress)
    }
}
