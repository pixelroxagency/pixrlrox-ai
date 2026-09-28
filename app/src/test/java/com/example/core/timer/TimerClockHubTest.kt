package com.example.core.timer

import org.junit.Assert.*
import org.junit.Test

class TimerClockHubTest {

    @Test
    fun testStopwatchEngine() {
        val fakeClock = FakeClockProvider(0L)
        val sw = StopwatchEngine(fakeClock)

        assertEquals(0L, sw.getElapsedMillis())
        assertFalse(sw.isRunning())

        sw.start()
        assertTrue(sw.isRunning())

        fakeClock.advance(1500L)
        assertEquals(1500L, sw.getElapsedMillis())

        val lap1 = sw.recordLap()
        assertNotNull(lap1)
        assertEquals(1, lap1?.lapNumber)
        assertEquals(1500L, lap1?.lapDurationMillis)
        assertEquals(1500L, lap1?.totalElapsedMillis)

        fakeClock.advance(500L)
        sw.pause()
        assertFalse(sw.isRunning())
        assertEquals(2000L, sw.getElapsedMillis())

        fakeClock.advance(5000L)
        assertEquals(2000L, sw.getElapsedMillis())

        sw.resume()
        assertTrue(sw.isRunning())
        fakeClock.advance(1000L)
        assertEquals(3000L, sw.getElapsedMillis())

        val lap2 = sw.recordLap()
        assertEquals(2, lap2?.lapNumber)
        assertEquals(1500L, lap2?.lapDurationMillis)

        sw.reset()
        assertEquals(0L, sw.getElapsedMillis())
        assertFalse(sw.isRunning())
        assertTrue(sw.getLaps().isEmpty())
    }

    @Test
    fun testCountdownEngine() {
        val fakeClock = FakeClockProvider(0L)
        val cd = CountdownEngine(fakeClock)

        assertEquals(0L, cd.getRemainingMillis())
        assertFalse(cd.isRunning())

        cd.start(10000L)
        assertTrue(cd.isRunning())
        assertEquals(10000L, cd.getRemainingMillis())

        fakeClock.advance(3000L)
        assertEquals(7000L, cd.getRemainingMillis())

        cd.pause()
        assertFalse(cd.isRunning())
        assertEquals(7000L, cd.getRemainingMillis())

        fakeClock.advance(20000L)
        assertEquals(7000L, cd.getRemainingMillis())

        cd.resume()
        assertTrue(cd.isRunning())
        fakeClock.advance(7000L)
        assertEquals(0L, cd.getRemainingMillis())

        val completed = cd.update()
        assertTrue(completed)
        assertTrue(cd.isCompleted())
        assertFalse(cd.isRunning())
    }

    @Test
    fun testIntervalTimerEngine() {
        val fakeClock = FakeClockProvider(0L)
        val interval = IntervalTimerEngine(fakeClock)

        interval.start(5000L, 3000L, 2)
        assertTrue(interval.isRunning())
        assertEquals(IntervalPhase.WORK, interval.getCurrentPhase())
        assertEquals(1, interval.getCurrentRound())
        assertEquals(5000L, interval.getPhaseRemainingMillis())

        fakeClock.advance(5000L)
        interval.update()
        assertEquals(IntervalPhase.REST, interval.getCurrentPhase())
        assertEquals(1, interval.getCurrentRound())
        assertEquals(3000L, interval.getPhaseRemainingMillis())

        fakeClock.advance(3000L)
        interval.update()
        assertEquals(IntervalPhase.WORK, interval.getCurrentPhase())
        assertEquals(2, interval.getCurrentRound())
        assertEquals(5000L, interval.getPhaseRemainingMillis())

        fakeClock.advance(5000L)
        interval.update()
        assertEquals(IntervalPhase.COMPLETED, interval.getCurrentPhase())
        assertFalse(interval.isRunning())
    }
}
