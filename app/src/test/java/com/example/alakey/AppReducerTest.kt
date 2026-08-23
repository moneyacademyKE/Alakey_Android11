package com.example.alakey

import com.example.alakey.data.PodcastEntity
import com.example.alakey.domain.HeadsetResumeLogic
import com.example.alakey.ui.AppReducer
import com.example.alakey.ui.AppViewModel
import com.example.alakey.ui.AsyncOp
import com.example.alakey.ui.LibraryFilters
import com.example.alakey.ui.formatMs
import com.example.alakey.ui.shouldDismissPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReducerTest {
    @Test fun `root navigation replaces rather than stacks tabs`() {
        val first = AppReducer.reduce(AppViewModel.UiState(), AppViewModel.Action.Navigate(AppViewModel.Screen.Queue))
        val second = AppReducer.reduce(first, AppViewModel.Action.Navigate(AppViewModel.Screen.Marketplace))
        assertEquals(listOf(AppViewModel.Screen.Marketplace), second.navigationStack)
    }

    @Test fun `play opens player with current podcast`() {
        val podcast = episode("id")
        val state = AppReducer.reduce(AppViewModel.UiState(), AppViewModel.Action.Play(podcast))
        assertEquals(podcast, state.current)
        assertTrue(state.isPlayerOpen)
    }

    @Test fun `subscribe adds one optimistic podcast per feed`() {
        val action = AppViewModel.Action.Subscribe("https://example.com/feed.xml", "Example", "image")
        val twice = AppReducer.reduce(AppReducer.reduce(AppViewModel.UiState(), action), action)
        assertEquals(1, twice.optimisticPodcasts.size)
    }

    @Test fun `operation state is owned by reducer`() {
        val market = AppReducer.reduce(AppViewModel.UiState(), AppViewModel.Action.SetMarketOp("query", AsyncOp.InFlight))
        val download = AppReducer.reduce(market, AppViewModel.Action.SetDownloadOp("episode", AsyncOp.Failed("offline")))
        assertEquals(AsyncOp.InFlight, download.marketOps["query"])
        assertEquals(AsyncOp.Failed("offline"), download.downloadOps["episode"])
    }

    @Test fun `restore opens player at episode but never marks playing`() {
        val state = AppReducer.reduce(AppViewModel.UiState(), AppViewModel.Action.Restore(episode("resume-me").apply { progress = 42_000 }))
        assertEquals("resume-me", state.current?.id)
        assertTrue(state.isPlayerOpen)
        assertFalse(state.isPlaying) // #49: restore is data; playing is intent
    }

    private fun episode(id: String, progress: Long = 0, duration: Long = 100, pubDate: String = "") =
        PodcastEntity(id, "Show", "Episode $id", "Desc", "image", "audio", duration = duration, pubDate = pubDate).apply { this.progress = progress }
}

class UxPolicyTest {
    @Test fun `headset sticky and disconnect events never resume`() {
        assertFalse(HeadsetResumeLogic.shouldResume(null, true))
        assertFalse(HeadsetResumeLogic.shouldResume(true, false))
        assertTrue(HeadsetResumeLogic.shouldResume(false, true))
    }

    @Test fun `all includes finished while new excludes started`() {
        val finished = PodcastEntity("finished", "Show", "Finished", "", "", "", duration = 100).apply { progress = 100 }
        val started = PodcastEntity("started", "Show", "Started", "", "", "", duration = 100).apply { progress = 1 }
        val fresh = PodcastEntity("fresh", "Show", "Fresh", "", "", "", duration = 100)
        assertEquals(setOf("finished", "started", "fresh"), LibraryFilters.apply("All", listOf(finished, started, fresh)).map { it.id }.toSet())
        assertEquals(listOf("fresh"), LibraryFilters.apply("New", listOf(finished, started, fresh)).map { it.id })
    }

    @Test fun `duration formatting includes hours`() {
        assertEquals("0:59", formatMs(59_000))
        assertEquals("1:15:20", formatMs(4_520_000))
    }

    @Test fun `dismiss policy accepts distance or velocity`() {
        assertTrue(shouldDismissPlayer(301f, 0f))
        assertTrue(shouldDismissPlayer(0f, 2501f))
        assertFalse(shouldDismissPlayer(100f, 100f))
    }
}
