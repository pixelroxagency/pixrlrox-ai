package com.example.core.games.ludo

import kotlin.math.cos
import kotlin.math.sin

object LudoTopology {

    // 52 Clockwise Track Grid Coordinates for 15x15 Classic Board
    val classicTrackCoords: List<GridCoord> = listOf(
        // 0..4: Left arm top row going right
        GridCoord(1f, 6f), GridCoord(2f, 6f), GridCoord(3f, 6f), GridCoord(4f, 6f), GridCoord(5f, 6f),
        // 5..10: Top arm left col going up
        GridCoord(6f, 5f), GridCoord(6f, 4f), GridCoord(6f, 3f), GridCoord(6f, 2f), GridCoord(6f, 1f), GridCoord(6f, 0f),
        // 11..12: Top arm top turn
        GridCoord(7f, 0f), GridCoord(8f, 0f),
        // 13..17: Top arm right col going down
        GridCoord(8f, 1f), GridCoord(8f, 2f), GridCoord(8f, 3f), GridCoord(8f, 4f), GridCoord(8f, 5f),
        // 18..23: Right arm top row going right
        GridCoord(9f, 6f), GridCoord(10f, 6f), GridCoord(11f, 6f), GridCoord(12f, 6f), GridCoord(13f, 6f), GridCoord(14f, 6f),
        // 24..25: Right arm right turn
        GridCoord(14f, 7f), GridCoord(14f, 8f),
        // 26..30: Right arm bottom row going left
        GridCoord(13f, 8f), GridCoord(12f, 8f), GridCoord(11f, 8f), GridCoord(10f, 8f), GridCoord(9f, 8f),
        // 31..36: Bottom arm right col going down
        GridCoord(8f, 9f), GridCoord(8f, 10f), GridCoord(8f, 11f), GridCoord(8f, 12f), GridCoord(8f, 13f), GridCoord(8f, 14f),
        // 37..38: Bottom arm bottom turn
        GridCoord(7f, 14f), GridCoord(6f, 14f),
        // 39..43: Bottom arm left col going up
        GridCoord(6f, 13f), GridCoord(6f, 12f), GridCoord(6f, 11f), GridCoord(6f, 10f), GridCoord(6f, 9f),
        // 44..49: Left arm bottom row going left
        GridCoord(5f, 8f), GridCoord(4f, 8f), GridCoord(3f, 8f), GridCoord(2f, 8f), GridCoord(1f, 8f), GridCoord(0f, 8f),
        // 50..51: Left arm left turn
        GridCoord(0f, 7f), GridCoord(0f, 6f)
    )

    // Home lanes (5 steps each)
    val classicHomeLanes: List<List<GridCoord>> = listOf(
        // Player 0 (Red - Left arm middle row)
        listOf(GridCoord(1f, 7f), GridCoord(2f, 7f), GridCoord(3f, 7f), GridCoord(4f, 7f), GridCoord(5f, 7f)),
        // Player 1 (Green - Top arm middle col)
        listOf(GridCoord(7f, 1f), GridCoord(7f, 2f), GridCoord(7f, 3f), GridCoord(7f, 4f), GridCoord(7f, 5f)),
        // Player 2 (Yellow - Right arm middle row)
        listOf(GridCoord(13f, 7f), GridCoord(12f, 7f), GridCoord(11f, 7f), GridCoord(10f, 7f), GridCoord(9f, 7f)),
        // Player 3 (Blue - Bottom arm middle col)
        listOf(GridCoord(7f, 13f), GridCoord(7f, 12f), GridCoord(7f, 11f), GridCoord(7f, 10f), GridCoord(7f, 9f))
    )

    // Base slots for 4 tokens each
    val classicBaseSlots: List<List<GridCoord>> = listOf(
        // Player 0 (Red Base: Top-Left)
        listOf(GridCoord(1.5f, 1.5f), GridCoord(3.5f, 1.5f), GridCoord(1.5f, 3.5f), GridCoord(3.5f, 3.5f)),
        // Player 1 (Green Base: Top-Right)
        listOf(GridCoord(10.5f, 1.5f), GridCoord(12.5f, 1.5f), GridCoord(10.5f, 3.5f), GridCoord(12.5f, 3.5f)),
        // Player 2 (Yellow Base: Bottom-Right)
        listOf(GridCoord(10.5f, 10.5f), GridCoord(12.5f, 10.5f), GridCoord(10.5f, 12.5f), GridCoord(12.5f, 12.5f)),
        // Player 3 (Blue Base: Bottom-Left)
        listOf(GridCoord(1.5f, 10.5f), GridCoord(3.5f, 10.5f), GridCoord(1.5f, 12.5f), GridCoord(3.5f, 12.5f))
    )

    // Center home finish destinations
    val classicHomeGoals: List<GridCoord> = listOf(
        GridCoord(6.3f, 7f), // Red goal
        GridCoord(7f, 6.3f), // Green goal
        GridCoord(7.7f, 7f), // Yellow goal
        GridCoord(7f, 7.7f)  // Blue goal
    )

    fun getTopology(playerCount: Int): BoardTopology {
        return when (playerCount) {
            2 -> BoardTopology(
                playerCount = 2,
                totalTrackCells = 52,
                homeStretchLength = 5,
                playerStarts = listOf(0, 26),
                homeEntryOffsets = listOf(50, 24),
                safeCells = setOf(0, 8, 13, 21, 26, 34, 39, 47),
                trackGridCoords = classicTrackCoords,
                homeStretchGridCoords = listOf(classicHomeLanes[0], classicHomeLanes[2]),
                baseSlotGridCoords = listOf(classicBaseSlots[0], classicBaseSlots[2]),
                homeGoalGridCoords = listOf(classicHomeGoals[0], classicHomeGoals[2])
            )
            3 -> generateThreePlayerTopology()
            4 -> BoardTopology(
                playerCount = 4,
                totalTrackCells = 52,
                homeStretchLength = 5,
                playerStarts = listOf(0, 13, 26, 39),
                homeEntryOffsets = listOf(50, 11, 24, 37),
                safeCells = setOf(0, 8, 13, 21, 26, 34, 39, 47),
                trackGridCoords = classicTrackCoords,
                homeStretchGridCoords = classicHomeLanes,
                baseSlotGridCoords = classicBaseSlots,
                homeGoalGridCoords = classicHomeGoals
            )
            else -> getTopology(4)
        }
    }

    private fun generateThreePlayerTopology(): BoardTopology {
        val totalTrack = 39
        val trackCoords = MutableList<GridCoord?>(totalTrack) { null }
        val center = GridCoord(7.5f, 7.5f)

        val homeLanes = mutableListOf<List<GridCoord>>()
        val baseSlots = mutableListOf<List<GridCoord>>()
        val homeGoals = mutableListOf<GridCoord>()

        val colOffset = 0.85f // Spacing between track columns
        val stepSpacing = 0.82f // Spacing between cells along an arm

        for (p in 0 until 3) {
            val angleDeg = p * 120f - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val dirX = cos(angleRad).toFloat()
            val dirY = sin(angleRad).toFloat()
            val perpX = -dirY
            val perpY = dirX

            // Symmetric Yard Center (base box center) offset to the left of the arm
            val yardCenterX = center.x + 5.0f * dirX - 3.0f * perpX
            val yardCenterY = center.y + 5.0f * dirY - 3.0f * perpY
            val yardCenter = GridCoord(yardCenterX, yardCenterY)

            // Base 4 token slots inside the home yard
            val pSlots = listOf(
                GridCoord(yardCenter.x - 0.7f * dirX - 0.7f * perpX, yardCenter.y - 0.7f * dirY - 0.7f * perpY),
                GridCoord(yardCenter.x - 0.7f * dirX + 0.7f * perpX, yardCenter.y - 0.7f * dirY + 0.7f * perpY),
                GridCoord(yardCenter.x + 0.7f * dirX - 0.7f * perpX, yardCenter.y + 0.7f * dirY - 0.7f * perpY),
                GridCoord(yardCenter.x + 0.7f * dirX + 0.7f * perpX, yardCenter.y + 0.7f * dirY + 0.7f * perpY)
            )
            baseSlots.add(pSlots)

            // Track coords for arm p
            // Left (outwards) column: 6 cells
            val outwardsStartIdx = (p * 13 + 31) % 39
            for (step in 0 until 6) {
                val idx = (outwardsStartIdx + step) % 39
                val dist = 1.1f + step * stepSpacing
                val x = center.x + dist * dirX - colOffset * perpX
                val y = center.y + dist * dirY - colOffset * perpY
                trackCoords[idx] = GridCoord(x, y)
            }

            // Turn cell
            val turnIdx = (p * 13 + 37) % 39
            val turnDist = 1.1f + 6 * stepSpacing
            val turnX = center.x + turnDist * dirX
            val turnY = center.y + turnDist * dirY
            trackCoords[turnIdx] = GridCoord(turnX, turnY)

            // Right (inwards) column: 6 cells
            val inwardsStartIdx = (p * 13 + 38) % 39
            for (step in 0 until 6) {
                val idx = (inwardsStartIdx + step) % 39
                val dist = 1.1f + (5 - step) * stepSpacing
                val x = center.x + dist * dirX + colOffset * perpX
                val y = center.y + dist * dirY + colOffset * perpY
                trackCoords[idx] = GridCoord(x, y)
            }

            // Home lane: 5 cells on centerline leading inwards
            val pLane = mutableListOf<GridCoord>()
            for (step in 0 until 5) {
                val dist = 1.1f + (4 - step) * stepSpacing
                val x = center.x + dist * dirX
                val y = center.y + dist * dirY
                pLane.add(GridCoord(x, y))
            }
            homeLanes.add(pLane)

            // Home goal
            val goalDist = 0.5f
            val goalX = center.x + goalDist * dirX
            val goalY = center.y + goalDist * dirY
            homeGoals.add(GridCoord(goalX, goalY))
        }

        return BoardTopology(
            playerCount = 3,
            totalTrackCells = totalTrack,
            homeStretchLength = 5,
            playerStarts = listOf(0, 13, 26),
            homeEntryOffsets = listOf(37, 11, 24),
            safeCells = setOf(0, 8, 13, 21, 26, 34),
            trackGridCoords = trackCoords.filterNotNull(),
            homeStretchGridCoords = homeLanes,
            baseSlotGridCoords = baseSlots,
            homeGoalGridCoords = homeGoals
        )
    }

    fun getInitialPlayers(playerCount: Int): List<LudoPlayer> {
        return when (playerCount) {
            2 -> listOf(
                LudoPlayer(0, "Red", 0xFFE53935),
                LudoPlayer(1, "Yellow", 0xFFFDD835)
            )
            3 -> listOf(
                LudoPlayer(0, "Red", 0xFFE53935),
                LudoPlayer(1, "Green", 0xFF43A047),
                LudoPlayer(2, "Yellow", 0xFFFDD835)
            )
            4 -> listOf(
                LudoPlayer(0, "Red", 0xFFE53935),
                LudoPlayer(1, "Green", 0xFF43A047),
                LudoPlayer(2, "Yellow", 0xFFFDD835),
                LudoPlayer(3, "Blue", 0xFF1E88E5)
            )
            else -> getInitialPlayers(4)
        }
    }
}
