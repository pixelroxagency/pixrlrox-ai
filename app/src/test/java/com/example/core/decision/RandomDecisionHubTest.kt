package com.example.core.decision

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RandomDecisionHubTest {

    private val engine = RandomDecisionEngine()

    @Test
    fun testMultilineInputParsing() {
        val rawInput = """
            Pizza 
               Burger  
            
            Biryani
            Ice Cream
        """.trimIndent()

        val parsed = engine.parseMultilineInput(rawInput)
        assertEquals(4, parsed.size)
        assertEquals("Pizza", parsed[0])
        assertEquals("Burger", parsed[1])
        assertEquals("Biryani", parsed[2])
        assertEquals("Ice Cream", parsed[3]) // Internal space preserved
    }

    @Test
    fun testDuplicateProcessing() {
        val items = listOf("Pizza", "Burger", "Pizza", "Salad", "Burger")
        
        val withDuplicates = engine.processDuplicates(items, allowDuplicates = true)
        assertEquals(5, withDuplicates.size)

        val withoutDuplicates = engine.processDuplicates(items, allowDuplicates = false)
        assertEquals(3, withoutDuplicates.size)
        assertEquals(listOf("Pizza", "Burger", "Salad"), withoutDuplicates)
    }

    @Test
    fun testSingleSelection() {
        val items = listOf("Option A", "Option B", "Option C")
        val fixedRandom = Random(12345)

        val result = engine.pickOne(items, random = fixedRandom)
        assertNotNull(result)
        assertTrue(items.contains(result!!.first))
        assertTrue(result.second in items.indices)

        // Test exclusion
        val excludeResult = engine.pickOne(items, excludeSet = setOf(result.first), random = fixedRandom)
        assertNotNull(excludeResult)
        assertNotEquals(result.first, excludeResult!!.first)
    }

    @Test
    fun testMultiSelectionBounds() {
        val items = listOf("A", "B", "C", "D")
        val random = Random(42)

        // Pick 2 without duplicates
        val picks2 = engine.pickMultiple(items, count = 2, allowDuplicates = false, random = random)
        assertEquals(2, picks2.size)
        assertEquals(2, picks2.distinct().size)

        // Request 10 without duplicates when only 4 available
        val picks10NoDup = engine.pickMultiple(items, count = 10, allowDuplicates = false, random = random)
        assertEquals(4, picks10NoDup.size)

        // Request 10 with duplicates
        val picks10WithDup = engine.pickMultiple(items, count = 10, allowDuplicates = true, random = random)
        assertEquals(10, picks10WithDup.size)
    }

    @Test
    fun testWheelSegmentAngle() {
        assertEquals(90f, engine.calculateSegmentAngle(4), 0.001f)
        assertEquals(60f, engine.calculateSegmentAngle(6), 0.001f)
        assertEquals(0f, engine.calculateSegmentAngle(0), 0.001f)
    }

    @Test
    fun testTargetRotationAndWinnerVerificationMath() {
        val itemCount = 4
        // Segment 0: 0..90 deg, center 45. Target rotation mod 360 = 315
        // Segment 1: 90..180 deg, center 135. Target rotation mod 360 = 225
        // Segment 2: 180..270 deg, center 225. Target rotation mod 360 = 135
        // Segment 3: 270..360 deg, center 315. Target rotation mod 360 = 45

        for (winningIndex in 0 until itemCount) {
            val targetRot = engine.calculateTargetRotation(
                itemCount = itemCount,
                winningIndex = winningIndex,
                currentRotation = 0f,
                fullSpins = 5
            )

            // Verify target rotation is at least 5 full spins ahead
            assertTrue(targetRot >= 1800f)

            // Verify pointer verification math maps targetRot back to winningIndex
            val calculatedIndex = engine.calculateWinningIndexFromRotation(targetRot, itemCount)
            assertEquals("Index $winningIndex failed rotation check", winningIndex, calculatedIndex)
        }
    }

    @Test
    fun testRotationNormalizationWithNonZeroCurrent() {
        val itemCount = 8
        val currentRotation = 1420.5f

        for (winningIndex in 0 until itemCount) {
            val targetRot = engine.calculateTargetRotation(
                itemCount = itemCount,
                winningIndex = winningIndex,
                currentRotation = currentRotation,
                fullSpins = 3
            )

            assertTrue(targetRot > currentRotation)
            val calculatedIndex = engine.calculateWinningIndexFromRotation(targetRot, itemCount)
            assertEquals("Index $winningIndex failed from non-zero current rotation", winningIndex, calculatedIndex)
        }
    }
}
