package com.example.core.timer

data class StopwatchLap(
    val lapNumber: Int,
    val lapDurationMillis: Long,
    val totalElapsedMillis: Long
)

class StopwatchEngine(private val clock: ClockProvider = SystemClockProvider()) {
    private var isRunning = false
    private var accumulatedMillis = 0L
    private var startRealtime = 0L
    private var lastLapElapsedMillis = 0L
    private val _laps = mutableListOf<StopwatchLap>()

    fun getElapsedMillis(): Long {
        if (!isRunning) return accumulatedMillis
        return accumulatedMillis + (clock.elapsedRealtime() - startRealtime)
    }

    fun isRunning(): Boolean = isRunning
    fun getLaps(): List<StopwatchLap> = _laps.toList()

    fun start() {
        if (!isRunning) {
            isRunning = true
            startRealtime = clock.elapsedRealtime()
        }
    }

    fun pause() {
        if (isRunning) {
            accumulatedMillis += clock.elapsedRealtime() - startRealtime
            isRunning = false
        }
    }

    fun resume() {
        start()
    }

    fun reset() {
        isRunning = false
        accumulatedMillis = 0L
        startRealtime = 0L
        lastLapElapsedMillis = 0L
        _laps.clear()
    }

    fun recordLap(): StopwatchLap? {
        val currentTotal = getElapsedMillis()
        if (currentTotal == 0L && _laps.isEmpty()) return null
        val lapDuration = currentTotal - lastLapElapsedMillis
        val lapNumber = _laps.size + 1
        val lap = StopwatchLap(lapNumber, lapDuration, currentTotal)
        _laps.add(0, lap)
        lastLapElapsedMillis = currentTotal
        return lap
    }
}
