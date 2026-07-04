package com.example.alakey

import com.example.alakey.data.PodcastEntity
import com.example.alakey.ui.AppReducer
import com.example.alakey.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReducerTest {
    @Test
    fun `navigate pushes distinct screen only once`() {
        val state = AppViewModel.UiState()

        val first = AppReducer.reduce(state, AppViewModel.Action.Navigate(AppViewModel.Screen.Queue))
        val second = AppReducer.reduce(first, AppViewModel.Action.Navigate(AppViewModel.Screen.Queue))

        assertEquals(listOf(AppViewModel.Screen.Library, AppViewModel.Screen.Queue), second.navigationStack)
    }

    @Test
    fun `play opens player with current podcast`() {
        val podcast = PodcastEntity("id", "Show", "Episode", "Desc", "image", "audio")

        val state = AppReducer.reduce(AppViewModel.UiState(), AppViewModel.Action.Play(podcast))

        assertEquals(podcast, state.current)
        assertTrue(state.isPlayerOpen)
    }

    @Test
    fun `subscribe adds optimistic podcast`() {
        val state = AppReducer.reduce(AppViewModel.UiState(), AppViewModel.Action.Subscribe("https://example.com/feed.xml", "Example", "image"))

        assertEquals(1, state.optimisticPodcasts.size)
        assertEquals("Example", state.optimisticPodcasts.first().title)
    }
}
