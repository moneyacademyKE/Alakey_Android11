package com.example.alakey.ui

import com.example.alakey.data.PodcastEntity

/** Pure state transitions for navigation and durable UI operation state. */
object AppReducer {
    fun reduce(state: AppViewModel.UiState, action: AppViewModel.Action): AppViewModel.UiState = when (action) {
        is AppViewModel.Action.Navigate -> {
            if (state.navigationStack.lastOrNull() == action.screen) state
            else state.copy(navigationStack = listOf(action.screen))
        }
        is AppViewModel.Action.Pop -> {
            if (state.navigationStack.size > 1) state.copy(navigationStack = state.navigationStack.dropLast(1)) else state
        }
        is AppViewModel.Action.SetPlayerOpen -> state.copy(isPlayerOpen = action.isOpen)
        is AppViewModel.Action.SetFilter -> state.copy(activeFilter = action.filter)
        is AppViewModel.Action.SetCarMode -> state.copy(isCarMode = action.enabled)
        is AppViewModel.Action.Play -> state.copy(current = action.podcast, isPlayerOpen = true)
        // Restore is passive rehydration: surface the episode on the mini-player strip,
        // sheet stays CLOSED (Play opens the sheet because a tap is intent to engage).
        is AppViewModel.Action.Restore -> state.copy(current = action.podcast)
        is AppViewModel.Action.Subscribe -> state.copy(
            optimisticPodcasts = state.optimisticPodcasts.filterNot { it.feedUrl == action.feedUrl } + optimisticPodcast(action)
        )
        is AppViewModel.Action.SetMarketOp -> state.copy(
            marketOps = state.marketOps + (action.query to action.operation)
        )
        is AppViewModel.Action.SetDownloadOp -> state.copy(
            downloadOps = state.downloadOps + (action.episodeId to action.operation)
        )
        is AppViewModel.Action.Rollback -> state.copy(
            optimisticPodcasts = state.optimisticPodcasts.filterNot { it.feedUrl == action.feedUrl },
            marketOps = action.marketQuery?.let { state.marketOps + (it to AsyncOp.Failed(action.error)) } ?: state.marketOps
        )
        else -> state
    }

    private fun optimisticPodcast(action: AppViewModel.Action.Subscribe): PodcastEntity = PodcastEntity(
        id = "optimistic_${action.feedUrl.hashCode()}",
        title = action.title,
        episodeTitle = "Syncing feed…",
        description = "Requesting information from ${action.feedUrl}",
        imageUrl = action.imageUrl,
        audioUrl = "",
        feedUrl = action.feedUrl
    )
}
