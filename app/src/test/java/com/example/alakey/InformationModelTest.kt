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
    fun `hydrate applies latest facts regardless of input order`() {
        val base = PodcastEntity(
            id = "test-id",
            title = "Test Podcast",
            episodeTitle = "Original Title",
            description = "Desc",
            imageUrl = "url",
            audioUrl = "audio"
        )

        val facts = listOf(
            FactEntity("test-id", InformationModel.ATTR_PROGRESS, "10000", tx = 200),
            FactEntity("test-id", InformationModel.ATTR_PROGRESS, "5000", tx = 100),
            FactEntity("test-id", InformationModel.ATTR_IN_QUEUE, "false", tx = 150),
            FactEntity("test-id", InformationModel.ATTR_IN_QUEUE, "true", tx = 300)
        )

        val hydrated = InformationModel.hydrate(base, facts)

        assertEquals(10000L, hydrated.progress)
        assertEquals(true, hydrated.isInQueue)
    }

    @Test
    fun `hydrate handles empty facts by returning base`() {
        val base = PodcastEntity(id = "test", title = "T", episodeTitle = "E", description = "D", imageUrl = "I", audioUrl = "A").apply { progress = 123 }
        val hydrated = InformationModel.hydrate(base, emptyList())
        assertEquals(base, hydrated)
    }

    @Test
    fun `hydrate handles malformed values gracefully`() {
        val base = PodcastEntity(id = "test", title = "T", episodeTitle = "E", description = "D", imageUrl = "I", audioUrl = "A")
        val facts = listOf(
            FactEntity("test", InformationModel.ATTR_PROGRESS, "not-a-number", tx = 100)
        )
        val hydrated = InformationModel.hydrate(base, facts)
        assertEquals(0L, hydrated.progress)
    }

    @Test
    fun `hydrate uses fact audio path as downloaded playback source`() {
        val base = PodcastEntity(id = "test", title = "T", episodeTitle = "E", description = "D", imageUrl = "I", audioUrl = "https://example.com/e.mp3")
        val facts = listOf(
            FactEntity("test", InformationModel.ATTR_AUDIO_PATH, "/files/test.mp3", tx = 100),
            FactEntity("test", InformationModel.ATTR_DOWNLOADED, "true", tx = 100)
        )

        val hydrated = InformationModel.hydrate(base, facts)

        assertEquals("/files/test.mp3", hydrated.audioUrl)
        assertEquals(true, hydrated.isDownloaded)
    }
}
