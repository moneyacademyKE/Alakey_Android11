package com.example.alakey.ui

import com.example.alakey.system.Monotonic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * L69 (extracted from PlaybackClient): deadline-derived sleep countdown.
 * Counting `delay(1000)` iterations can only run late (each iteration is
 * 1s + dispatch, never reconciled), and `_seconds.value--` was a non-atomic
 * read-modify-write that reset() could interleave with. A Monotonic deadline
 * is self-correcting: every tick WRITES the recomputed remaining value, so
 * there is no read-modify-write left to race, and the display can never
 * disagree with the actual expiry time. Reset re-arms the deadline — the
 * shake-to-extend path gets a genuinely full duration, not a bumped counter.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onExpired: () -> Unit
) {
    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    /** Duration the timer was armed with; 0 when disarmed (drain fraction). */
    private val _totalSeconds = MutableStateFlow(0)
    val totalSeconds: StateFlow<Int> = _totalSeconds.asStateFlow()

    private var job: Job? = null
    private var initialSeconds = 0

    /** Armed countdown: expires and fires [onExpired]. Never forces playback itself. */
    fun start(minutes: Int) = startSeconds(minutes * 60)

    private fun startSeconds(totalSeconds: Int) {
        job?.cancel()
        initialSeconds = totalSeconds
        _totalSeconds.value = totalSeconds
        _seconds.value = totalSeconds
        val deadlineMs = Monotonic.now() + totalSeconds * 1000L
        job = scope.launch {
            while (true) {
                val remaining = ((deadlineMs - Monotonic.now() + 999L) / 1000L).toInt()
                if (remaining <= 0) break
                _seconds.value = remaining
                delay(1000)
            }
            _seconds.value = 0
            _totalSeconds.value = 0
            onExpired()
        }
    }

    /**
     * Episode-end mode display: mirrors remaining episode time with no
     * countdown job — the episode-end listener owns the pause (L44).
     */
    fun displayOnly(fromSeconds: Int) {
        job?.cancel()
        initialSeconds = 0
        _seconds.value = fromSeconds
        _totalSeconds.value = fromSeconds
    }

    /** Re-arm at the initial duration while a countdown is live (shake-to-extend). */
    fun reset() {
        if (initialSeconds > 0 && _seconds.value > 0) startSeconds(initialSeconds)
    }

    fun cancel() {
        job?.cancel()
        initialSeconds = 0
        _seconds.value = 0
        _totalSeconds.value = 0
    }
}
