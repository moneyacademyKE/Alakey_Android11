package com.example.alakey.ui

import com.example.alakey.data.PodcastEntity

object AppReducer {
    fun reduce(state: AppViewModel.UiState, action: AppViewModel.Action): AppViewModel.UiState {
        return when (action) {
            is AppViewModel.Action.Navigate -> {
                if (state.navigationStack.lastOrNull() != action.screen) {
                    state.copy(navigationStack = state.navigationStack + action.screen)
                } else state
            }
            is AppViewModel.Action.Pop -> {
                if (state.navigationStack.size > 1) state.copy(navigationStack = state.navigationStack.dropLast(1)) else state
            }
            is AppViewModel.Action.SetPlayerOpen -> state.copy(isPlayerOpen = action.isOpen)
            is AppViewModel.Action.SetFilter -> state.copy(activeFilter = action.filter)
            is AppViewModel.Action.SetCarMode -> state.copy(isCarMode = action.enabled)
            is AppViewModel.Action.Play -> state.copy(current = action.podcast, isPlayerOpen = true)
            is AppViewModel.Action.Subscribe -> state.copy(
                optimisticPodcasts = state.optimisticPodcasts + optimisticPodcast(action)
            )
            else -> state
        }
    }

    private fun optimisticPodcast(action: AppViewModel.Action.Subscribe): PodcastEntity {
        return PodcastEntity(
            id = "optimistic_${action.feedUrl.hashCode()}",
            title = action.title,
            episodeTitle = "Syncing feed...",
            description = "Requesting information from ${action.feedUrl}",
            imageUrl = action.imageUrl,
            audioUrl = "",
            feedUrl = action.feedUrl
        )
    }
}
