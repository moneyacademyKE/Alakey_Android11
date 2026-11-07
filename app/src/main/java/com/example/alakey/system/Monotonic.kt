package com.example.alakey.system

import android.os.SystemClock

/**
 * M68: elapsed intervals must use the monotonic clock. The wall clock steps
 * (NTP resync, flight-mode exit, manual date change) — every interval that
 * measured "how long since X" with `System.currentTimeMillis` broke on a
 * step: the watch tile stopped ticking, progress saving stalled, smart-resume
 * rewound or never fired. Timestamps that CROSS a process boundary (fact tx,
 * lastPlayed, tile wire values) must stay on the wall clock —
 * elapsedRealtime is meaningless across reboot — so only interval sites
 * swap to [now].
 */
object Monotonic {
    fun now(): Long = SystemClock.elapsedRealtime()
}
