package com.example.core.games.ludo

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class LudoEngine(
    val selectedPlayerCount: Int = 4,
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    initialDiceMode: DiceMode = DiceMode.PIXELROX_SMART,
    private val dicePolicy: DicePolicy = SmartDicePolicy(),
    val playerCount: Int = selectedPlayerCount
) {

    val topology: BoardTopology = LudoTopology.getTopology(4)
    private var state: LudoGameState
    private var currentDiceMode: DiceMode = initialDiceMode

    private fun debugLog(msg: String) {
        try {
            android.util.Log.d("LudoEngine", msg)
        } catch (_: Throwable) {
            // Fallback for JVM unit tests where android.util.Log is not mocked
        }
        println("[LudoEngine] $msg")
    }

    init {
        val basePlayers = LudoTopology.getInitialPlayers(4)
        val activePlayerCount = if (playerCount != 4) playerCount else selectedPlayerCount
        val players = basePlayers.map { player ->
            if (activePlayerCount == 2 && (player.index == 1 || player.index == 3)) {
                player.copy(isActive = false)
            } else if (activePlayerCount == 3 && player.index == 3) {
                player.copy(isActive = false)
            } else {
                player
            }
        }
        val tokens = mutableListOf<LudoToken>()
        for (p in 0 until 4) {
            for (t in 0 until 4) {
                tokens.add(
                    LudoToken(
                        id = t,
                        playerIndex = p,
                        state = TokenState.IN_HOME_YARD,
                        trackPosition = -1,
                        homeStretchPosition = -1
                    )
                )
            }
        }

        var firstActiveIndex = 0
        while (firstActiveIndex < 4 && !players[firstActiveIndex].isActive) {
            firstActiveIndex++
        }
        if (firstActiveIndex >= 4) firstActiveIndex = 0

        state = LudoGameState(
            playerCount = 4,
            players = players,
            tokens = tokens,
            currentTurnPlayerIndex = firstActiveIndex,
            diceValue = 1,
            turnState = LudoTurnState.WAITING_FOR_ROLL,
            hasRolledDice = false,
            consecutiveSixes = 0,
            legalMoves = emptyList(),
            winnerPlayerIndex = null,
            statusMessage = "${players[firstActiveIndex].name}'s turn. Tap the dice to roll!",
            ruleSet = ruleSet,
            diceMode = initialDiceMode
        )
    }

    fun getGameState(): LudoGameState = state

    fun isOccupiedByOwnPawn(tokenIdToIgnore: Int, playerIndex: Int, trackPos: Int, homeStretchPos: Int): Boolean {
        // Standard Ludo permits stacking own pawns on track and start cells.
        return false
    }

    fun addMoveIfValid(moves: MutableList<LudoMove>, move: LudoMove) {
        moves.add(move)
    }

    fun getDiceMode(): DiceMode = currentDiceMode

    fun setDiceMode(mode: DiceMode) {
        currentDiceMode = mode
        state = state.copy(diceMode = mode)
    }

    fun setTurnState(newState: LudoTurnState): LudoGameState {
        state = state.copy(turnState = newState)
        return state
    }

    fun setCurrentTurnForTesting(playerIndex: Int) {
        state = state.copy(currentTurnPlayerIndex = playerIndex)
    }

    /**
     * Rolls the dice using the configured DicePolicy (Smart Dice or Standard Random).
     */
    fun rollDice(overrideDice: Int? = null, randomSource: Random = Random.Default): LudoGameState {
        if (state.winnerPlayerIndex != null) return state
        if (state.turnState != LudoTurnState.WAITING_FOR_ROLL && state.turnState != LudoTurnState.ROLLING) {
            return state
        }

        val currentPlayer = state.players[state.currentTurnPlayerIndex]

        val dice = overrideDice ?: dicePolicy.roll(
            playerIndex = state.currentTurnPlayerIndex,
            consecutiveSixes = state.consecutiveSixes,
            engine = this,
            ruleSet = ruleSet,
            diceMode = currentDiceMode,
            randomSource = randomSource
        )

        val newConsecutiveSixes = if (dice == 6) state.consecutiveSixes + 1 else 0
        val legalMoves = computeLegalMoves(state.currentTurnPlayerIndex, dice)

        if (legalMoves.isEmpty()) {
            // No legal moves available: display roll and auto-pass turn
            state = state.copy(
                diceValue = dice,
                hasRolledDice = true,
                consecutiveSixes = newConsecutiveSixes,
                legalMoves = emptyList(),
                turnState = LudoTurnState.TURN_COMPLETE,
                statusMessage = "${currentPlayer.name} rolled $dice. No legal move available!"
            )
            return state
        }

        state = state.copy(
            diceValue = dice,
            hasRolledDice = true,
            consecutiveSixes = newConsecutiveSixes,
            legalMoves = legalMoves,
            turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
            selectedTokenId = null,
            statusMessage = if (legalMoves.size == 1) {
                "${currentPlayer.name} rolled $dice! Moving token..."
            } else {
                "${currentPlayer.name} rolled $dice! Select a token to move."
            }
        )
        return state
    }

    /**
     * Compute all legal moves for a given player and dice value based on exact distance-to-home and rules.
     */
    fun computeLegalMoves(
        playerIndex: Int,
        dice: Int,
        targetState: LudoGameState = state
    ): List<LudoMove> {
        val playerTokens = targetState.tokens.filter { it.playerIndex == playerIndex }
        val moves = mutableListOf<LudoMove>()

        val startCell = topology.playerStarts[playerIndex]
        val totalTrack = topology.totalTrackCells
        val homeStretchLength = topology.homeStretchLength
        val commonTrackLen = topology.commonTrackLength(playerIndex)
        val maxProgress = topology.maxRouteProgress(playerIndex)

        debugLog("--- computeLegalMoves: player=$playerIndex, dice=$dice, startCell=$startCell, commonTrackLen=$commonTrackLen, maxProgress=$maxProgress ---")

        for (token in playerTokens) {
            when (token.state) {
                TokenState.IN_HOME_YARD -> {
                    // Release rule: default requires 6
                    if (dice == ruleSet.releaseRoll) {
                        val isStartSafe = ruleSet.safeCellsEnabled && topology.safeCells.contains(startCell)
                        val targetOccupant = if (ruleSet.captureEnabled && !isStartSafe) {
                            targetState.tokens.firstOrNull {
                                it.state == TokenState.ON_TRACK && it.trackPosition == startCell && it.playerIndex != playerIndex
                            }
                        } else null

                        val pathCoords = computePathForMove(
                            playerIndex = playerIndex,
                            fromProgress = -1,
                            toProgress = 0,
                            fromState = TokenState.IN_HOME_YARD
                        )

                        moves.add(
                            LudoMove(
                                tokenId = token.id,
                                playerIndex = playerIndex,
                                targetState = TokenState.ON_TRACK,
                                targetTrackPos = startCell,
                                targetHomeStretchPos = -1,
                                targetRouteProgress = 0,
                                capturesToken = targetOccupant,
                                pathStepCoords = pathCoords
                            )
                        )
                        debugLog("  Token ${token.id}: state=IN_HOME_YARD -> LEGAL (deploy from Yard to startCell $startCell, progress 0)")
                    } else {
                        debugLog("  Token ${token.id}: state=IN_HOME_YARD, isHome=true, isFinished=false -> ILLEGAL (dice $dice != releaseRoll ${ruleSet.releaseRoll})")
                    }
                }

                TokenState.ON_TRACK, TokenState.IN_HOME_STRETCH -> {
                    val currentProgress = token.effectiveRouteProgress(topology)
                    val targetProgress = currentProgress + dice

                    // Exact finish rule: overshooting maxProgress is completely illegal
                    if (targetProgress > maxProgress) {
                        debugLog("  Token ${token.id}: progress=$currentProgress + dice=$dice = $targetProgress > maxProgress=$maxProgress -> ILLEGAL (overshoot)")
                        continue
                    }

                    if (targetProgress <= commonTrackLen) {
                        // Regular track movement along common board
                        val targetTrack = (startCell + targetProgress) % totalTrack

                        val isTargetSafe = ruleSet.safeCellsEnabled && topology.safeCells.contains(targetTrack)
                        val targetOccupant = if (ruleSet.captureEnabled && !isTargetSafe) {
                            targetState.tokens.firstOrNull {
                                it.state == TokenState.ON_TRACK && it.trackPosition == targetTrack && it.playerIndex != playerIndex
                            }
                        } else null

                        val pathCoords = computePathForMove(
                            playerIndex = playerIndex,
                            fromProgress = currentProgress,
                            toProgress = targetProgress,
                            fromState = token.state
                        )

                        moves.add(
                            LudoMove(
                                tokenId = token.id,
                                playerIndex = playerIndex,
                                targetState = TokenState.ON_TRACK,
                                targetTrackPos = targetTrack,
                                targetHomeStretchPos = -1,
                                targetRouteProgress = targetProgress,
                                capturesToken = targetOccupant,
                                pathStepCoords = pathCoords
                            )
                        )
                        debugLog("  Token ${token.id}: state=${token.state} (progress=$currentProgress) -> LEGAL (advance on common track to $targetTrack, progress $targetProgress)")
                    } else if (targetProgress < maxProgress) {
                        // Enters or advances in player's OWN home stretch lane
                        val stepsIntoHome = targetProgress - commonTrackLen - 1
                        val pathCoords = computePathForMove(
                            playerIndex = playerIndex,
                            fromProgress = currentProgress,
                            toProgress = targetProgress,
                            fromState = token.state
                        )

                        moves.add(
                            LudoMove(
                                tokenId = token.id,
                                playerIndex = playerIndex,
                                targetState = TokenState.IN_HOME_STRETCH,
                                targetTrackPos = -1,
                                targetHomeStretchPos = stepsIntoHome,
                                targetRouteProgress = targetProgress,
                                capturesToken = null,
                                pathStepCoords = pathCoords
                            )
                        )
                        debugLog("  Token ${token.id}: state=${token.state} (progress=$currentProgress) -> LEGAL (enter/advance home stretch lane at $stepsIntoHome, progress $targetProgress)")
                    } else {
                        // targetProgress == maxProgress: Exact finish into goal!
                        val pathCoords = computePathForMove(
                            playerIndex = playerIndex,
                            fromProgress = currentProgress,
                            toProgress = targetProgress,
                            fromState = token.state
                        )

                        moves.add(
                            LudoMove(
                                tokenId = token.id,
                                playerIndex = playerIndex,
                                targetState = TokenState.FINISHED,
                                targetTrackPos = -1,
                                targetHomeStretchPos = homeStretchLength,
                                targetRouteProgress = targetProgress,
                                capturesToken = null,
                                pathStepCoords = pathCoords
                            )
                        )
                        debugLog("  Token ${token.id}: state=${token.state} (progress=$currentProgress) -> LEGAL (exact finish into goal, progress $targetProgress)")
                    }
                }

                TokenState.FINISHED -> {
                    debugLog("  Token ${token.id}: state=FINISHED -> ILLEGAL (already finished)")
                }
            }
        }
        val finalTokenIds = moves.map { it.tokenId }
        debugLog("computeLegalMoves result: final legal token IDs = $finalTokenIds")
        return moves
    }

    /**
     * Identifies all legal dice values (1..6) that would allow the specified player to capture an opponent token.
     * Evaluates real player-relative route progress, obstacle legality, and safe-cell exclusions using computeLegalMoves.
     */
    fun getLegalCaptureDiceValues(
        playerIndex: Int = state.currentTurnPlayerIndex,
        targetState: LudoGameState = state
    ): Set<Int> {
        val captureDiceValues = mutableSetOf<Int>()
        for (dice in 1..6) {
            val legalMoves = computeLegalMoves(playerIndex, dice, targetState)
            if (legalMoves.any { it.capturesToken != null }) {
                captureDiceValues.add(dice)
            }
        }
        return captureDiceValues
    }

    fun computePathForMove(
        playerIndex: Int,
        fromProgress: Int,
        toProgress: Int,
        fromState: TokenState
    ): List<GridCoord> {
        val path = mutableListOf<GridCoord>()
        val startCell = topology.playerStarts[playerIndex]
        val totalTrack = topology.totalTrackCells
        val commonTrackLen = topology.commonTrackLength(playerIndex)
        val maxProgress = topology.maxRouteProgress(playerIndex)

        if (fromState == TokenState.IN_HOME_YARD && toProgress == 0) {
            if (startCell in topology.trackGridCoords.indices) {
                path.add(topology.trackGridCoords[startCell])
            }
            return path
        }

        val stepStart = fromProgress + 1
        for (p in stepStart..toProgress) {
            if (p <= commonTrackLen) {
                val trackIdx = (startCell + p) % totalTrack
                if (trackIdx in topology.trackGridCoords.indices) {
                    path.add(topology.trackGridCoords[trackIdx])
                }
            } else if (p < maxProgress) {
                val stretchIdx = p - commonTrackLen - 1
                val playerLanes = topology.homeStretchGridCoords.getOrNull(playerIndex) ?: emptyList()
                if (stretchIdx in playerLanes.indices) {
                    path.add(playerLanes[stretchIdx])
                }
            } else if (p == maxProgress) {
                val goalCoord = topology.homeGoalGridCoords.getOrNull(playerIndex)
                if (goalCoord != null) {
                    path.add(goalCoord)
                }
            }
        }
        return path
    }

    private fun computePathCoords(
        fromState: TokenState,
        fromTrackPos: Int,
        fromHomeStretchPos: Int,
        toState: TokenState,
        toTrackPos: Int,
        toHomeStretchPos: Int,
        playerIndex: Int,
        tokenId: Int
    ): List<GridCoord> {
        val dummyFrom = LudoToken(
            id = tokenId,
            playerIndex = playerIndex,
            state = fromState,
            trackPosition = fromTrackPos,
            homeStretchPosition = fromHomeStretchPos
        )
        val fromProgress = dummyFrom.effectiveRouteProgress(topology)
        val dummyTo = LudoToken(
            id = tokenId,
            playerIndex = playerIndex,
            state = toState,
            trackPosition = toTrackPos,
            homeStretchPosition = toHomeStretchPos
        )
        val toProgress = dummyTo.effectiveRouteProgress(topology)
        return computePathForMove(playerIndex, fromProgress, toProgress, fromState)
    }

    /**
     * Executes a legal move for the specified token.
     */
    fun makeMove(tokenId: Int): LudoGameState {
        val move = state.legalMoves.firstOrNull { it.tokenId == tokenId } ?: return state

        val updatedTokens = state.tokens.toMutableList()
        val tokenIndex = updatedTokens.indexOfFirst { it.playerIndex == move.playerIndex && it.id == move.tokenId }
        if (tokenIndex == -1) return state

        val movedToken = updatedTokens[tokenIndex].copy(
            state = move.targetState,
            trackPosition = move.targetTrackPos,
            homeStretchPosition = move.targetHomeStretchPos,
            routeProgress = move.targetRouteProgress
        )
        updatedTokens[tokenIndex] = movedToken

        // Handle capture
        var didCapture = false
        if (move.capturesToken != null) {
            val capturedIdx = updatedTokens.indexOfFirst {
                it.playerIndex == move.capturesToken.playerIndex && it.id == move.capturesToken.id
            }
            if (capturedIdx != -1) {
                updatedTokens[capturedIdx] = updatedTokens[capturedIdx].copy(
                    state = TokenState.IN_HOME_YARD,
                    trackPosition = -1,
                    homeStretchPosition = -1,
                    routeProgress = -1
                )
                didCapture = true
            }
        }

        // Check if player won / finished
        val playerTokens = updatedTokens.filter { it.playerIndex == move.playerIndex }
        val allFinished = playerTokens.all { it.state == TokenState.FINISHED }

        val updatedPlayers = state.players.toMutableList()
        var winner = state.winnerPlayerIndex
        val finishOrder = state.finishOrder.toMutableList()

        if (allFinished && !finishOrder.contains(move.playerIndex)) {
            finishOrder.add(move.playerIndex)
            updatedPlayers[move.playerIndex] = updatedPlayers[move.playerIndex].copy(
                isFinished = true,
                finishRank = finishOrder.size
            )
            if (winner == null) {
                winner = move.playerIndex
            }
        }

        // Game Over check
        if (winner != null && finishOrder.size >= updatedPlayers.size - 1) {
            val winnerName = updatedPlayers[winner].name
            state = state.copy(
                tokens = updatedTokens,
                players = updatedPlayers,
                hasRolledDice = false,
                legalMoves = emptyList(),
                winnerPlayerIndex = winner,
                finishOrder = finishOrder,
                turnState = LudoTurnState.GAME_OVER,
                statusMessage = "🎉 $winnerName has WON the match!"
            )
            return state
        }

        // Bonus turn check (6, capture, or finish)
        val bonusTurn = (state.diceValue == 6 || didCapture || move.targetState == TokenState.FINISHED) && (winner == null)
        val currentPlayer = updatedPlayers[state.currentTurnPlayerIndex]

        if (bonusTurn) {
            val bonusReason = when {
                didCapture -> "Captured an opponent! Bonus roll"
                move.targetState == TokenState.FINISHED -> "Token finished! Bonus roll"
                else -> "Rolled a 6! Bonus roll"
            }
            state = state.copy(
                tokens = updatedTokens,
                players = updatedPlayers,
                hasRolledDice = false,
                legalMoves = emptyList(),
                selectedTokenId = null,
                turnState = LudoTurnState.WAITING_FOR_ROLL,
                rollOpportunityId = state.rollOpportunityId + 1,
                statusMessage = "$bonusReason for ${currentPlayer.name}."
            )
            return state
        }

        // Pass turn to next active player
        return passTurnToNextPlayer(
            tokens = updatedTokens,
            players = updatedPlayers,
            diceValue = state.diceValue,
            status = null
        )
    }

    /**
     * Passes the turn to the next player who has not finished the game.
     */
    fun passTurnToNextPlayer(
        tokens: List<LudoToken> = state.tokens,
        players: List<LudoPlayer> = state.players,
        diceValue: Int = state.diceValue,
        status: String? = null
    ): LudoGameState {
        var nextPlayer = (state.currentTurnPlayerIndex + 1) % state.playerCount
        var loopCount = 0
        while ((players[nextPlayer].isFinished || !players[nextPlayer].isActive) && loopCount < state.playerCount) {
            nextPlayer = (nextPlayer + 1) % state.playerCount
            loopCount++
        }

        val nextPlayerName = players[nextPlayer].name
        val statusMsg = status ?: "${players[state.currentTurnPlayerIndex].name}'s turn ended. ${nextPlayerName}'s turn!"

        state = state.copy(
            tokens = tokens,
            players = players,
            currentTurnPlayerIndex = nextPlayer,
            diceValue = diceValue,
            hasRolledDice = false,
            consecutiveSixes = 0,
            legalMoves = emptyList(),
            selectedTokenId = null,
            turnState = LudoTurnState.WAITING_FOR_ROLL,
            rollOpportunityId = state.rollOpportunityId + 1,
            statusMessage = statusMsg
        )
        return state
    }

    fun setPlayerActive(playerIndex: Int, active: Boolean): LudoGameState {
        val updatedPlayers = state.players.toMutableList()
        val oldPlayer = updatedPlayers[playerIndex]
        updatedPlayers[playerIndex] = oldPlayer.copy(isActive = active)

        var newState = state.copy(players = updatedPlayers)

        // If we deactivated the current player, we must immediately advance the turn!
        if (!active && state.currentTurnPlayerIndex == playerIndex && state.winnerPlayerIndex == null) {
            var nextPlayer = (playerIndex + 1) % state.playerCount
            var loopCount = 0
            while ((updatedPlayers[nextPlayer].isFinished || !updatedPlayers[nextPlayer].isActive) && loopCount < state.playerCount) {
                nextPlayer = (nextPlayer + 1) % state.playerCount
                loopCount++
            }
            if (nextPlayer != playerIndex && updatedPlayers[nextPlayer].isActive && !updatedPlayers[nextPlayer].isFinished) {
                newState = newState.copy(
                    currentTurnPlayerIndex = nextPlayer,
                    hasRolledDice = false,
                    consecutiveSixes = 0,
                    legalMoves = emptyList(),
                    turnState = LudoTurnState.WAITING_FOR_ROLL,
                    rollOpportunityId = newState.rollOpportunityId + 1,
                    statusMessage = "${oldPlayer.name} was removed. ${updatedPlayers[nextPlayer].name}'s turn!"
                )
            } else {
                newState = newState.copy(
                    statusMessage = "${oldPlayer.name} was removed. No active players remaining!"
                )
            }
        } else {
            val action = if (active) "restored" else "removed"
            newState = newState.copy(
                statusMessage = "${oldPlayer.name} was $action."
            )
        }

        state = newState
        return state
    }

    fun setTokensForTesting(tokens: List<LudoToken>) {
        val normalized = tokens.map { t ->
            if (t.routeProgress < 0 && (t.state == TokenState.ON_TRACK || t.state == TokenState.IN_HOME_STRETCH || t.state == TokenState.FINISHED)) {
                t.copy(routeProgress = t.effectiveRouteProgress(topology))
            } else {
                t
            }
        }
        state = state.copy(tokens = normalized)
    }

    fun serializeTokens(): String {
        val obj = JSONObject()
        val tokensArray = JSONArray()
        for (token in state.tokens) {
            val tObj = JSONObject().apply {
                put("id", token.id)
                put("p", token.playerIndex)
                put("s", token.state.name)
                put("t", token.trackPosition)
                put("h", token.homeStretchPosition)
                put("rp", token.routeProgress)
            }
            tokensArray.put(tObj)
        }
        obj.put("tokens", tokensArray)

        val playersArray = JSONArray()
        for (player in state.players) {
            val pObj = JSONObject().apply {
                put("idx", player.index)
                put("name", player.name)
                put("color", player.colorHex)
                put("isFinished", player.isFinished)
                put("finishRank", player.finishRank ?: -1)
                put("isAI", player.isAI)
                put("isActive", player.isActive)
            }
            playersArray.put(pObj)
        }
        obj.put("players", playersArray)

        val finishOrderArray = JSONArray()
        for (item in state.finishOrder) {
            finishOrderArray.put(item)
        }
        obj.put("finishOrder", finishOrderArray)

        return obj.toString()
    }

    fun restoreFromSaved(
        savedTokensJson: String,
        currentTurn: Int,
        diceValue: Int,
        hasRolled: Boolean,
        winner: Int?
    ) {
        try {
            val restoredTokens = mutableListOf<LudoToken>()
            var restoredPlayers = state.players
            var finishOrderList = state.finishOrder

            if (savedTokensJson.startsWith("{")) {
                val rootObj = JSONObject(savedTokensJson)
                
                val tokensArray = rootObj.getJSONArray("tokens")
                for (i in 0 until tokensArray.length()) {
                    val obj = tokensArray.getJSONObject(i)
                    val rp = if (obj.has("rp")) obj.getInt("rp") else -1
                    val tok = LudoToken(
                        id = obj.getInt("id"),
                        playerIndex = obj.getInt("p"),
                        state = TokenState.valueOf(obj.getString("s")),
                        trackPosition = obj.getInt("t"),
                        homeStretchPosition = obj.getInt("h"),
                        routeProgress = rp
                    )
                    restoredTokens.add(
                        if (tok.routeProgress < 0 && (tok.state == TokenState.ON_TRACK || tok.state == TokenState.IN_HOME_STRETCH || tok.state == TokenState.FINISHED)) {
                            tok.copy(routeProgress = tok.effectiveRouteProgress(topology))
                        } else tok
                    )
                }

                if (rootObj.has("players")) {
                    val pList = mutableListOf<LudoPlayer>()
                    val playersArray = rootObj.getJSONArray("players")
                    for (i in 0 until playersArray.length()) {
                        val obj = playersArray.getJSONObject(i)
                        pList.add(
                            LudoPlayer(
                                index = obj.getInt("idx"),
                                name = obj.getString("name"),
                                colorHex = obj.getLong("color"),
                                isFinished = obj.getBoolean("isFinished"),
                                finishRank = if (obj.getInt("finishRank") == -1) null else obj.getInt("finishRank"),
                                isAI = obj.getBoolean("isAI"),
                                isActive = if (obj.has("isActive")) obj.getBoolean("isActive") else true
                            )
                        )
                    }
                    restoredPlayers = pList
                }

                if (rootObj.has("finishOrder")) {
                    val fList = mutableListOf<Int>()
                    val fArray = rootObj.getJSONArray("finishOrder")
                    for (i in 0 until fArray.length()) {
                        fList.add(fArray.getInt(i))
                    }
                    finishOrderList = fList
                }

                val legalMoves = if (hasRolled) computeLegalMoves(currentTurn, diceValue) else emptyList()
                state = state.copy(
                    tokens = restoredTokens,
                    players = restoredPlayers,
                    currentTurnPlayerIndex = currentTurn,
                    diceValue = diceValue,
                    hasRolledDice = hasRolled,
                    winnerPlayerIndex = winner,
                    finishOrder = finishOrderList,
                    legalMoves = legalMoves,
                    turnState = if (winner != null) {
                        LudoTurnState.GAME_OVER
                    } else if (hasRolled && legalMoves.isNotEmpty()) {
                        LudoTurnState.WAITING_FOR_TOKEN_SELECTION
                    } else {
                        LudoTurnState.WAITING_FOR_ROLL
                    },
                    statusMessage = "${restoredPlayers.getOrNull(currentTurn)?.name ?: "Player"}'s turn."
                )
            } else if (savedTokensJson.startsWith("[")) {
                val array = JSONArray(savedTokensJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rp = if (obj.has("rp")) obj.getInt("rp") else -1
                    val tok = LudoToken(
                        id = obj.getInt("id"),
                        playerIndex = obj.getInt("p"),
                        state = TokenState.valueOf(obj.getString("s")),
                        trackPosition = obj.getInt("t"),
                        homeStretchPosition = obj.getInt("h"),
                        routeProgress = rp
                    )
                    restoredTokens.add(
                        if (tok.routeProgress < 0 && (tok.state == TokenState.ON_TRACK || tok.state == TokenState.IN_HOME_STRETCH || tok.state == TokenState.FINISHED)) {
                            tok.copy(routeProgress = tok.effectiveRouteProgress(topology))
                        } else tok
                    )
                }
                val legalMoves = if (hasRolled) computeLegalMoves(currentTurn, diceValue) else emptyList()
                state = state.copy(
                    tokens = restoredTokens,
                    currentTurnPlayerIndex = currentTurn,
                    diceValue = diceValue,
                    hasRolledDice = hasRolled,
                    winnerPlayerIndex = winner,
                    legalMoves = legalMoves,
                    turnState = if (winner != null) {
                        LudoTurnState.GAME_OVER
                    } else if (hasRolled && legalMoves.isNotEmpty()) {
                        LudoTurnState.WAITING_FOR_TOKEN_SELECTION
                    } else {
                        LudoTurnState.WAITING_FOR_ROLL
                    },
                    statusMessage = "${state.players[currentTurn].name}'s turn."
                )
            } else {
                val items = savedTokensJson.split(";").filter { it.isNotBlank() }
                for (item in items) {
                    val parts = item.split(",")
                    if (parts.size >= 5) {
                        val tok = LudoToken(
                            id = parts[0].toInt(),
                            playerIndex = parts[1].toInt(),
                            state = TokenState.valueOf(parts[2]),
                            trackPosition = parts[3].toInt(),
                            homeStretchPosition = parts[4].toInt(),
                            routeProgress = if (parts.size >= 6) parts[5].toInt() else -1
                        )
                        restoredTokens.add(
                            if (tok.routeProgress < 0 && (tok.state == TokenState.ON_TRACK || tok.state == TokenState.IN_HOME_STRETCH || tok.state == TokenState.FINISHED)) {
                                tok.copy(routeProgress = tok.effectiveRouteProgress(topology))
                            } else tok
                        )
                    }
                }
                val legalMoves = if (hasRolled) computeLegalMoves(currentTurn, diceValue) else emptyList()
                state = state.copy(
                    tokens = restoredTokens,
                    currentTurnPlayerIndex = currentTurn,
                    diceValue = diceValue,
                    hasRolledDice = hasRolled,
                    winnerPlayerIndex = winner,
                    legalMoves = legalMoves,
                    turnState = if (winner != null) {
                        LudoTurnState.GAME_OVER
                    } else if (hasRolled && legalMoves.isNotEmpty()) {
                        LudoTurnState.WAITING_FOR_TOKEN_SELECTION
                    } else {
                        LudoTurnState.WAITING_FOR_ROLL
                    },
                    statusMessage = "${state.players[currentTurn].name}'s turn."
                )
            }
        } catch (_: Exception) {}
    }
}
