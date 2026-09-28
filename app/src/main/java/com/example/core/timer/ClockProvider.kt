package com.example.core.timer

import android.os.SystemClock

interface ClockProvider {
    fun elapsedRealtime(): Long
}

class SystemClockProvider : ClockProvider {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

class FakeClockProvider(var currentTime: Long = 0L) : ClockProvider {
    override fun elapsedRealtime(): Long = currentTime
    fun advance(millis: Long) { currentTime += millis }
}
