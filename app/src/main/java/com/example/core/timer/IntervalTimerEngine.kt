package com.example.core.timer

enum class IntervalPhase { WORK, REST, COMPLETED }

class IntervalTimerEngine(private val clock: ClockProvider = SystemClockProvider()) {
    private var isRunning = false
    private var workMillis = 0L
    private var restMillis = 0L
    private var totalRounds = 1
    private var currentRound = 1
    private var currentPhase = IntervalPhase.WORK
    private var deadlineRealtime = 0L
    private var phaseRemainingMillis = 0L

    fun getPhaseRemainingMillis(): Long {
        if (!isRunning) return phaseRemainingMillis
        val remaining = deadlineRealtime - clock.elapsedRealtime()
        return if (remaining <= 0L) 0L else remaining
    }

    fun isRunning(): Boolean = isRunning
    fun getCurrentRound(): Int = currentRound
    fun getTotalRounds(): Int = totalRounds
    fun getCurrentPhase(): IntervalPhase = currentPhase

    fun start(workDurationMillis: Long, restDurationMillis: Long, rounds: Int) {
        if (workDurationMillis <= 0L || rounds <= 0) return
        workMillis = workDurationMillis
        restMillis = restDurationMillis
        totalRounds = rounds
        currentRound = 1
        currentPhase = IntervalPhase.WORK
        phaseRemainingMillis = workMillis
        deadlineRealtime = clock.elapsedRealtime() + workMillis
        isRunning = true
    }

    fun pause() {
        if (isRunning) {
            phaseRemainingMillis = getPhaseRemainingMillis()
            isRunning = false
        }
    }

    fun resume() {
        if (!isRunning && currentPhase != IntervalPhase.COMPLETED && phaseRemainingMillis > 0L) {
            deadlineRealtime = clock.elapsedRealtime() + phaseRemainingMillis
            isRunning = true
        }
    }

    fun reset() {
        isRunning = false
        workMillis = 0L
        restMillis = 0L
        totalRounds = 1
        currentRound = 1
        currentPhase = IntervalPhase.WORK
        deadlineRealtime = 0L
        phaseRemainingMillis = 0L
    }

    fun skipPhase() {
        advancePhase()
    }

    fun update(): Boolean {
        if (isRunning) {
            val rem = getPhaseRemainingMillis()
            if (rem <= 0L) {
                advancePhase()
                return true
            }
        }
        return false
    }

    private fun advancePhase() {
        if (currentPhase == IntervalPhase.WORK) {
            if (currentRound < totalRounds && restMillis > 0L) {
                currentPhase = IntervalPhase.REST
                phaseRemainingMillis = restMillis
                deadlineRealtime = clock.elapsedRealtime() + restMillis
            } else if (currentRound < totalRounds) {
                currentRound++
                currentPhase = IntervalPhase.WORK
                phaseRemainingMillis = workMillis
                deadlineRealtime = clock.elapsedRealtime() + workMillis
            } else {
                currentPhase = IntervalPhase.COMPLETED
                isRunning = false
                phaseRemainingMillis = 0L
            }
        } else if (currentPhase == IntervalPhase.REST) {
            currentRound++
            if (currentRound <= totalRounds) {
                currentPhase = IntervalPhase.WORK
                phaseRemainingMillis = workMillis
                deadlineRealtime = clock.elapsedRealtime() + workMillis
            } else {
                currentPhase = IntervalPhase.COMPLETED
                isRunning = false
                phaseRemainingMillis = 0L
            }
        }
    }
}
