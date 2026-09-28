package com.example.core.games.ludo

data class LudoPlayer(
    val index: Int,
    val name: String,
    val colorHex: Long,
    val isFinished: Boolean = false,
    val finishRank: Int? = null,
    val isAI: Boolean = false,
    val isActive: Boolean = true
)

data class LudoToken(
    val id: Int, // 0..3 for each player
    val playerIndex: Int,
    val state: TokenState = TokenState.IN_HOME_YARD,
    val trackPosition: Int = -1, // position on main ring
    val homeStretchPosition: Int = -1, // 0..4 in home lane, 5 is goal
    val routeProgress: Int = -1 // monotonic player-relative progress along route
)

enum class TokenState {
    IN_HOME_YARD,
    ON_TRACK,
    IN_HOME_STRETCH,
    FINISHED
}

enum class LudoTurnState {
    WAITING_FOR_ROLL,
    ROLLING,
    WAITING_FOR_TOKEN_SELECTION,
    ANIMATING_MOVE,
    RESOLVING_MOVE,
    TURN_COMPLETE,
    GAME_OVER
}

enum class DiceMode {
    PIXELROX_SMART,
    STANDARD_RANDOM
}

data class LudoRuleSet(
    val releaseRoll: Int = 6,
    val exactHomeRequired: Boolean = true,
    val safeCellsEnabled: Boolean = true,
    val captureEnabled: Boolean = true,
    val extraTurnOnSix: Boolean = true,
    val maxConsecutiveSixes: Int = 2,
    val smartUsefulRolls: Boolean = true,
    val autoMoveSingleOption: Boolean = true
)

data class GridCoord(
    val x: Float,
    val y: Float
)

data class BoardTopology(
    val playerCount: Int,
    val totalTrackCells: Int,
    val homeStretchLength: Int = 5,
    val playerStarts: List<Int>, // index on main track where each player starts
    val homeEntryOffsets: List<Int>, // index on main track from which player enters home stretch
    val safeCells: Set<Int>,
    val trackGridCoords: List<GridCoord> = emptyList(),
    val homeStretchGridCoords: List<List<GridCoord>> = emptyList(),
    val baseSlotGridCoords: List<List<GridCoord>> = emptyList(),
    val homeGoalGridCoords: List<GridCoord> = emptyList()
) {
    fun commonTrackLength(playerIndex: Int): Int {
        val start = playerStarts[playerIndex]
        val entry = homeEntryOffsets[playerIndex]
        return (entry - start + totalTrackCells) % totalTrackCells
    }

    fun maxRouteProgress(playerIndex: Int): Int {
        return commonTrackLength(playerIndex) + homeStretchLength + 1
    }
}

fun LudoToken.effectiveRouteProgress(topology: BoardTopology): Int {
    if (routeProgress >= 0) return routeProgress
    return when (state) {
        TokenState.IN_HOME_YARD -> -1
        TokenState.ON_TRACK -> {
            val startCell = topology.playerStarts[playerIndex]
            val totalTrack = topology.totalTrackCells
            (trackPosition - startCell + totalTrack) % totalTrack
        }
        TokenState.IN_HOME_STRETCH -> {
            val commonLen = topology.commonTrackLength(playerIndex)
            commonLen + 1 + homeStretchPosition
        }
        TokenState.FINISHED -> {
            topology.maxRouteProgress(playerIndex)
        }
    }
}

data class LudoMove(
    val tokenId: Int,
    val playerIndex: Int,
    val targetState: TokenState,
    val targetTrackPos: Int,
    val targetHomeStretchPos: Int,
    val targetRouteProgress: Int = -1,
    val capturesToken: LudoToken? = null,
    val pathStepCoords: List<GridCoord> = emptyList()
)

data class LudoGameState(
    val playerCount: Int,
    val players: List<LudoPlayer>,
    val tokens: List<LudoToken>,
    val currentTurnPlayerIndex: Int,
    val diceValue: Int,
    val hasRolledDice: Boolean = false,
    val turnState: LudoTurnState = LudoTurnState.WAITING_FOR_ROLL,
    val consecutiveSixes: Int = 0,
    val legalMoves: List<LudoMove> = emptyList(),
    val selectedTokenId: Int? = null,
    val winnerPlayerIndex: Int? = null,
    val finishOrder: List<Int> = emptyList(),
    val statusMessage: String = "Roll the dice to start!",
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    val diceMode: DiceMode = DiceMode.PIXELROX_SMART,
    val rollOpportunityId: Long = 1L
)
