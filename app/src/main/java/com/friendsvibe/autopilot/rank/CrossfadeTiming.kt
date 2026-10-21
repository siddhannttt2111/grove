package com.friendsvibe.autopilot.rank

object CrossfadeTiming {
    const val MAX_MS = 4_000L
    const val MIN_MS = 800L

    /** Short tracks are cut rather than overlapped. Longer tracks fade for a quarter of their length, capped at four seconds. */
    fun crossfadeMs(durationMs: Long): Long {
        if (durationMs < 2_000L) return 0L
        return (durationMs / 4).coerceIn(MIN_MS, MAX_MS)
    }
}
