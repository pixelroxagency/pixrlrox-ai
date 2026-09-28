package com.example.core.decision

import kotlin.math.floor
import kotlin.random.Random

data class ChoiceItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String
)

data class HistoryRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String,
    val result: String
)

class RandomDecisionEngine {

    /**
     * Parses multi-line input string into a list of cleaned choice strings.
     * Trims surrounding whitespace per line, filters empty lines, keeps internal spaces.
     */
    fun parseMultilineInput(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        return input.split("\n", "\r\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Deduplicates items if allowDuplicates is false, keeping order.
     */
    fun processDuplicates(items: List<String>, allowDuplicates: Boolean): List<String> {
        return if (allowDuplicates) items else items.distinct()
    }

    /**
     * Randomly picks 1 item from choices.
     * Optional excludeSet allows excluding previous winners if possible.
     */
    fun pickOne(
        items: List<String>,
        excludeSet: Set<String> = emptySet(),
        random: Random = Random.Default
    ): Pair<String, Int>? {
        if (items.isEmpty()) return null
        
        val availableIndices = items.indices.filter { idx -> items[idx] !in excludeSet }
        val targetIndices = if (availableIndices.isNotEmpty()) availableIndices else items.indices.toList()
        
        val selectedIndex = targetIndices[random.nextInt(targetIndices.size)]
        return Pair(items[selectedIndex], selectedIndex)
    }

    /**
     * Picks multiple items from choices.
     * If allowDuplicates is false, bounds count to number of unique items.
     */
    fun pickMultiple(
        items: List<String>,
        count: Int,
        allowDuplicates: Boolean = false,
        random: Random = Random.Default
    ): List<String> {
        if (items.isEmpty() || count <= 0) return emptyList()

        if (allowDuplicates) {
            return List(count) { items[random.nextInt(items.size)] }
        } else {
            val uniqueItems = items.distinct()
            val safeCount = count.coerceAtMost(uniqueItems.size)
            return uniqueItems.shuffled(random).take(safeCount)
        }
    }

    /**
     * Calculates angle per segment in degrees (360 / N).
     */
    fun calculateSegmentAngle(itemCount: Int): Float {
        if (itemCount <= 0) return 0f
        return 360f / itemCount
    }

    /**
     * Calculates the target rotation degrees for the wheel to land on winningIndex at Top pointer.
     * Top pointer is at 0 degrees relative to top center.
     * Segment i spans [i * angle, (i + 1) * angle].
     * Segment i center = (i + 0.5) * angle.
     * To bring center of segment i to Top (0 degrees clockwise rotation):
     * targetNormalizedRotation = (360 - (i + 0.5) * angle) % 360
     */
    fun calculateTargetRotation(
        itemCount: Int,
        winningIndex: Int,
        currentRotation: Float,
        fullSpins: Int = 5
    ): Float {
        if (itemCount <= 0 || winningIndex !in 0 until itemCount) return currentRotation

        val segmentAngle = calculateSegmentAngle(itemCount)
        val centerAngle = (winningIndex + 0.5f) * segmentAngle
        
        var desiredR = (360f - centerAngle) % 360f
        if (desiredR < 0f) desiredR += 360f

        var currentR = currentRotation % 360f
        if (currentR < 0f) currentR += 360f

        var forwardDelta = desiredR - currentR
        if (forwardDelta <= 0f) {
            forwardDelta += 360f
        }

        return currentRotation + forwardDelta + (fullSpins * 360f)
    }

    /**
     * Helper to verify which segment index points to TOP pointer (0 deg) for a given total rotation.
     */
    fun calculateWinningIndexFromRotation(rotation: Float, itemCount: Int): Int {
        if (itemCount <= 0) return -1
        val segmentAngle = calculateSegmentAngle(itemCount)
        
        var normalizedR = rotation % 360f
        if (normalizedR < 0f) normalizedR += 360f

        var wheelAngleUnderPointer = (360f - normalizedR) % 360f
        if (wheelAngleUnderPointer < 0f) wheelAngleUnderPointer += 360f

        val index = floor(wheelAngleUnderPointer / segmentAngle).toInt()
        return index.coerceIn(0, itemCount - 1)
    }
}
