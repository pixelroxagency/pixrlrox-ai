package com.example.core.timer

class CountdownEngine(private val clock: ClockProvider = SystemClockProvider()) {
    private var isRunning = false
    private var totalDurationMillis = 0L
    private var deadlineRealtime = 0L
    private var pausedRemainingMillis = 0L
    private var isCompleted = false

    fun getRemainingMillis(): Long {
        if (!isRunning) return pausedRemainingMillis
        val remaining = deadlineRealtime - clock.elapsedRealtime()
        return if (remaining <= 0L) 0L else remaining
    }

    fun isRunning(): Boolean = isRunning
    fun isCompleted(): Boolean = isCompleted

    fun start(durationMillis: Long) {
        if (durationMillis <= 0L) return
        totalDurationMillis = durationMillis
        pausedRemainingMillis = durationMillis
        deadlineRealtime = clock.elapsedRealtime() + durationMillis
        isRunning = true
        isCompleted = false
    }

    fun pause() {
        if (isRunning) {
            pausedRemainingMillis = getRemainingMillis()
            isRunning = false
            if (pausedRemainingMillis <= 0L) {
                isCompleted = true
            }
        }
    }

    fun resume() {
        if (!isRunning && pausedRemainingMillis > 0L && !isCompleted) {
            deadlineRealtime = clock.elapsedRealtime() + pausedRemainingMillis
            isRunning = true
        }
    }

    fun reset() {
        isRunning = false
        totalDurationMillis = 0L
        deadlineRealtime = 0L
        pausedRemainingMillis = 0L
        isCompleted = false
    }

    fun update(): Boolean {
        if (isRunning) {
            val remaining = getRemainingMillis()
            if (remaining <= 0L) {
                isRunning = false
                pausedRemainingMillis = 0L
                isCompleted = true
                return true
            }
        }
        return false
    }
}
