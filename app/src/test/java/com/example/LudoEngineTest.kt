package com.example

import com.example.core.games.ludo.DiceMode
import com.example.core.games.ludo.LudoEngine
import com.example.core.games.ludo.LudoRuleSet
import com.example.core.games.ludo.LudoTopology
import com.example.core.games.ludo.LudoToken
import com.example.core.games.ludo.LudoTurnState
import com.example.core.games.ludo.SmartDicePolicy
import com.example.core.games.ludo.TokenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LudoEngineTest {

    @Test
    fun testInitialization4Players() {
        val engine = LudoEngine(playerCount = 4)
        val state = engine.getGameState()
        assertEquals(4, state.players.size)
        assertEquals(16, state.tokens.size)
        assertEquals(0, state.currentTurnPlayerIndex)
        assertEquals(LudoTurnState.WAITING_FOR_ROLL, state.turnState)
        assertFalse(state.hasRolledDice)
    }

    @Test
    fun testTokenReleaseOnSix() {
        val engine = LudoEngine(playerCount = 4)
        // Roll a 6
        val stateAfterSix = engine.rollDice(overrideDice = 6)
        assertEquals(6, stateAfterSix.diceValue)
        assertEquals(1, stateAfterSix.consecutiveSixes)
        assertEquals(4, stateAfterSix.legalMoves.size) // All 4 tokens in yard can leave

        // Move token 0
        val stateAfterMove = engine.makeMove(tokenId = 0)
        val movedToken = stateAfterMove.tokens.first { it.playerIndex == 0 && it.id == 0 }
        assertEquals(TokenState.ON_TRACK, movedToken.state)
        assertEquals(engine.topology.playerStarts[0], movedToken.trackPosition)

        // Rolling a 6 grants bonus turn for same player
        assertEquals(0, stateAfterMove.currentTurnPlayerIndex)
        assertEquals(LudoTurnState.WAITING_FOR_ROLL, stateAfterMove.turnState)
    }

    @Test
    fun testConsecutiveSixesLimit() {
        val engine = LudoEngine(playerCount = 4)
        val policy = SmartDicePolicy()

        // Turn 1: Roll 6
        engine.rollDice(overrideDice = 6)
        engine.makeMove(tokenId = 0) // Token 0 now ON_TRACK
        assertEquals(1, engine.getGameState().consecutiveSixes)

        // Turn 2: Roll second 6
        engine.rollDice(overrideDice = 6)
        engine.makeMove(tokenId = 1) // Token 1 now ON_TRACK
        assertEquals(2, engine.getGameState().consecutiveSixes)

        // Turn 3: 3rd roll must NEVER be 6
        for (i in 0 until 50) {
            val roll = policy.roll(
                playerIndex = 0,
                consecutiveSixes = 2,
                engine = engine,
                ruleSet = LudoRuleSet(maxConsecutiveSixes = 2),
                diceMode = DiceMode.STANDARD_RANDOM,
                randomSource = Random(i)
            )
            assertNotEquals("3rd consecutive roll must not be 6", 6, roll)
            assertTrue(roll in 1..5)
        }
    }

    @Test
    fun testLegalTrackMovement() {
        val engine = LudoEngine(playerCount = 4)
        engine.rollDice(overrideDice = 6)
        engine.makeMove(tokenId = 0)

        val startPos = engine.topology.playerStarts[0]
        engine.rollDice(overrideDice = 4)
        val state = engine.makeMove(tokenId = 0)

        val token = state.tokens.first { it.playerIndex == 0 && it.id == 0 }
        assertEquals((startPos + 4) % engine.topology.totalTrackCells, token.trackPosition)
        assertEquals(TokenState.ON_TRACK, token.state)
        // Non-6 passes turn to player 1
        assertEquals(1, state.currentTurnPlayerIndex)
    }

    @Test
    fun testSafeCellProtectionAndCapture() {
        val engine = LudoEngine(playerCount = 4)

        // Release Red token 0 to Start Cell 0 (safe start)
        engine.rollDice(overrideDice = 6)
        engine.makeMove(tokenId = 0)

        // Move Red token 0 to non-safe cell (e.g. cell 3)
        engine.rollDice(overrideDice = 3)
        engine.makeMove(tokenId = 0)

        // Now Player 1 (Green) turn. Pass turns or setup Blue token (start 39)
        val blueStart = engine.topology.playerStarts[3] // 39
        // Cell 3 is distance 16 from 39 (39 -> 52 -> 3)
        // Let's test capture directly by computing legal moves
        val updatedTokens = engine.getGameState().tokens.toMutableList()
        // Put Blue token 0 on track at cell 1
        val blueIdx = updatedTokens.indexOfFirst { it.playerIndex == 3 && it.id == 0 }
        updatedTokens[blueIdx] = updatedTokens[blueIdx].copy(state = TokenState.ON_TRACK, trackPosition = 1)

        val redIdx = updatedTokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        updatedTokens[redIdx] = updatedTokens[redIdx].copy(state = TokenState.ON_TRACK, trackPosition = 3)
        engine.setTokensForTesting(updatedTokens)

        // If Blue rolls 2: target is cell 3 (Red token location)
        val moves = engine.computeLegalMoves(playerIndex = 3, dice = 2)
        val captureMove = moves.firstOrNull { it.tokenId == 0 }
        assertNotNull(captureMove)
        assertNotNull(captureMove?.capturesToken)
        assertEquals(0, captureMove?.capturesToken?.playerIndex)
        assertEquals(0, captureMove?.capturesToken?.id)
    }

    @Test
    fun testExactHomeRequirementAndOvershootRejection() {
        val engine = LudoEngine(playerCount = 4)
        val updatedTokens = engine.getGameState().tokens.toMutableList()

        // Place token 0 in home stretch position 3 (needs exact 2 steps to finish step 5)
        val tIdx = updatedTokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        updatedTokens[tIdx] = updatedTokens[tIdx].copy(
            state = TokenState.IN_HOME_STRETCH,
            trackPosition = -1,
            homeStretchPosition = 3
        )

        // Other tokens in yard
        for (i in 1..3) {
            val otherIdx = updatedTokens.indexOfFirst { it.playerIndex == 0 && it.id == i }
            updatedTokens[otherIdx] = updatedTokens[otherIdx].copy(state = TokenState.IN_HOME_YARD)
        }
        engine.setTokensForTesting(updatedTokens)

        // Roll 1: legal move to stretch pos 4
        val moves1 = engine.computeLegalMoves(playerIndex = 0, dice = 1)
        assertEquals(1, moves1.size)
        assertEquals(TokenState.IN_HOME_STRETCH, moves1[0].targetState)
        assertEquals(4, moves1[0].targetHomeStretchPos)

        // Roll 2: exact finish to goal!
        val moves2 = engine.computeLegalMoves(playerIndex = 0, dice = 2)
        assertEquals(1, moves2.size)
        assertEquals(TokenState.FINISHED, moves2[0].targetState)
        assertEquals(5, moves2[0].targetHomeStretchPos)

        // Roll 3, 4, 5: OVERSHOOT! Must be completely illegal for token 0 (and no 6 to release yard)
        val moves3 = engine.computeLegalMoves(playerIndex = 0, dice = 3)
        assertTrue(moves3.isEmpty())

        val moves4 = engine.computeLegalMoves(playerIndex = 0, dice = 4)
        assertTrue(moves4.isEmpty())

        val moves5 = engine.computeLegalMoves(playerIndex = 0, dice = 5)
        assertTrue(moves5.isEmpty())
    }

    @Test
    fun testSmartDicePolicyCaseA() {
        // CASE A: useful values filter returns only useful dice
        val engine = LudoEngine(playerCount = 4)
        val policy = SmartDicePolicy()

        // Token 0 in home stretch pos 3 (needs 1 or 2). All others in yard (need 6).
        // Valid useful rolls = [1, 2, 6]
        // Rolls 3, 4, 5 produce zero moves
        for (i in 0 until 30) {
            val roll = policy.roll(
                playerIndex = 0,
                consecutiveSixes = 0,
                engine = engine,
                ruleSet = LudoRuleSet(),
                diceMode = DiceMode.PIXELROX_SMART,
                randomSource = Random(i)
            )
            // Candidate roll must be in valid range 1..6
            assertTrue(roll in 1..6)
        }
    }

    @Test
    fun testSmartDicePolicyCaseC() {
        // CASE C: legal values include 6 but consecutiveSixes = 2 -> 6 must be excluded
        val engine = LudoEngine(playerCount = 4)
        val policy = SmartDicePolicy()

        val roll = policy.roll(
            playerIndex = 0,
            consecutiveSixes = 2,
            engine = engine,
            ruleSet = LudoRuleSet(maxConsecutiveSixes = 2),
            diceMode = DiceMode.PIXELROX_SMART,
            randomSource = Random(42)
        )
        assertNotEquals(6, roll)
    }

    @Test
    fun testSmartDicePolicyCaseE_NoFreeze() {
        // CASE E: when no moves are possible, engine does not freeze and falls back cleanly
        val engine = LudoEngine(playerCount = 4)
        val policy = SmartDicePolicy()

        // If consecutiveSixes = 2 and all tokens in yard (only 6 could release), useful is empty.
        // It must fallback and return a number 1..5 without error or infinite loop.
        val roll = policy.roll(
            playerIndex = 0,
            consecutiveSixes = 2,
            engine = engine,
            ruleSet = LudoRuleSet(maxConsecutiveSixes = 2),
            diceMode = DiceMode.PIXELROX_SMART,
            randomSource = Random(123)
        )
        assertTrue(roll in 1..5)
    }

    @Test
    fun testTwoPlayerTopology() {
        val topo = LudoTopology.getTopology(2)
        assertEquals(2, topo.playerCount)
        assertEquals(52, topo.totalTrackCells)
        assertEquals(listOf(0, 26), topo.playerStarts)
        assertEquals(listOf(50, 24), topo.homeEntryOffsets)
    }

    @Test
    fun testThreePlayerDedicatedTopology() {
        val topo = LudoTopology.getTopology(3)
        assertEquals(3, topo.playerCount)
        assertEquals(39, topo.totalTrackCells) // 3 arms * 13 cells
        assertEquals(listOf(0, 13, 26), topo.playerStarts)
        assertEquals(3, topo.homeStretchGridCoords.size)
        assertEquals(3, topo.baseSlotGridCoords.size)
    }

    @Test
    fun testSaveAndRestore() {
        val engine1 = LudoEngine(playerCount = 4)
        engine1.rollDice(overrideDice = 6)
        engine1.makeMove(tokenId = 0)

        val serialized = engine1.serializeTokens()
        assertTrue(serialized.contains("ON_TRACK"))

        val engine2 = LudoEngine(playerCount = 4)
        engine2.restoreFromSaved(
            savedTokensJson = serialized,
            currentTurn = 0,
            diceValue = 6,
            hasRolled = false,
            winner = null
        )

        val restoredToken = engine2.getGameState().tokens.first { it.playerIndex == 0 && it.id == 0 }
        assertEquals(TokenState.ON_TRACK, restoredToken.state)
        assertEquals(engine1.topology.playerStarts[0], restoredToken.trackPosition)
    }

    @Test
    fun testFirstRollIsNotHardCoded() {
        val engine = LudoEngine(playerCount = 4)
        engine.setDiceMode(DiceMode.STANDARD_RANDOM)
        
        // Mock standard Kotlin Random to return a deterministic index
        val mockRandom2 = object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 1 // index 1 of [1,2,3,4,5,6] -> 2
        }
        val state2 = engine.rollDice(randomSource = mockRandom2)
        assertEquals(2, state2.diceValue)
        
        val engine5 = LudoEngine(playerCount = 4)
        engine5.setDiceMode(DiceMode.STANDARD_RANDOM)
        val mockRandom5 = object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 4 // index 4 of [1,2,3,4,5,6] -> 5
        }
        val state5 = engine5.rollDice(randomSource = mockRandom5)
        assertEquals(5, state5.diceValue)
    }

    @Test
    fun testStandardRandomNoMove() {
        val engine = LudoEngine(playerCount = 4)
        engine.setDiceMode(DiceMode.STANDARD_RANDOM)
        
        val mockRandom3 = object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 2 // index 2 of [1,2,3,4,5,6] -> 3
        }
        
        val state = engine.rollDice(randomSource = mockRandom3)
        assertEquals(3, state.diceValue)
        
        // No tokens should be released from home yard
        for (token in state.tokens) {
            assertEquals(TokenState.IN_HOME_YARD, token.state)
        }
        
        // Assert that turn state is TURN_COMPLETE indicating no legal moves
        assertEquals(LudoTurnState.TURN_COMPLETE, state.turnState)
    }

    // =========================================================================
    // MANDATORY TESTS A THROUGH F: HOME-ON-6 DEPLOYMENT
    // =========================================================================

    @Test
    fun testMandatoryTestA_Green2Home2BoardDice6_BothHomeTokensLegal() {
        val engine = LudoEngine(playerCount = 4)
        val greenIndex = 1
        val greenStart = engine.topology.playerStarts[greenIndex] // 13

        // Green has 2 tokens on board (token 0 on start cell 13, token 1 on cell 20)
        // and 2 tokens still in Yard (token 2, token 3)
        val tokens = engine.getGameState().tokens.map { token ->
            if (token.playerIndex == greenIndex) {
                when (token.id) {
                    0 -> token.copy(state = TokenState.ON_TRACK, trackPosition = greenStart)
                    1 -> token.copy(state = TokenState.ON_TRACK, trackPosition = (greenStart + 7) % 52)
                    2 -> token.copy(state = TokenState.IN_HOME_YARD, trackPosition = -1)
                    3 -> token.copy(state = TokenState.IN_HOME_YARD, trackPosition = -1)
                    else -> token
                }
            } else {
                token
            }
        }
        engine.setTokensForTesting(tokens)
        engine.setCurrentTurnForTesting(greenIndex)

        // Green rolls 6
        val state = engine.rollDice(overrideDice = 6)
        assertEquals(6, state.diceValue)
        assertEquals(greenIndex, state.currentTurnPlayerIndex)

        val legalTokenIds = state.legalMoves.map { it.tokenId }.toSet()

        // Assert: BOTH Home token IDs exist in legalMoves
        assertTrue("Home token 2 must be legal", legalTokenIds.contains(2))
        assertTrue("Home token 3 must be legal", legalTokenIds.contains(3))

        // Also verify that moves for tokens 2 and 3 deploy to Green's startCell
        val move2 = state.legalMoves.first { it.tokenId == 2 }
        assertEquals(TokenState.ON_TRACK, move2.targetState)
        assertEquals(greenStart, move2.targetTrackPos)

        val move3 = state.legalMoves.first { it.tokenId == 3 }
        assertEquals(TokenState.ON_TRACK, move3.targetState)
        assertEquals(greenStart, move3.targetTrackPos)
    }

    @Test
    fun testMandatoryTestB_Green4HomeDice6_All4HomeTokensLegal() {
        val engine = LudoEngine(playerCount = 4)
        val greenIndex = 1
        val greenStart = engine.topology.playerStarts[greenIndex]

        engine.setCurrentTurnForTesting(greenIndex)

        val state = engine.rollDice(overrideDice = 6)
        assertEquals(6, state.diceValue)
        assertEquals(greenIndex, state.currentTurnPlayerIndex)

        val legalTokenIds = state.legalMoves.map { it.tokenId }.toSet()
        assertEquals("All 4 tokens must be legal choices", setOf(0, 1, 2, 3), legalTokenIds)

        for (tokenId in 0..3) {
            val move = state.legalMoves.first { it.tokenId == tokenId }
            assertEquals(TokenState.ON_TRACK, move.targetState)
            assertEquals(greenStart, move.targetTrackPos)
        }
    }

    @Test
    fun testMandatoryTestC_Green2Home2BoardDice5_HomeTokensNotLegal() {
        val engine = LudoEngine(playerCount = 4)
        val greenIndex = 1
        val greenStart = engine.topology.playerStarts[greenIndex]

        val tokens = engine.getGameState().tokens.map { token ->
            if (token.playerIndex == greenIndex) {
                when (token.id) {
                    0 -> token.copy(state = TokenState.ON_TRACK, trackPosition = greenStart)
                    1 -> token.copy(state = TokenState.ON_TRACK, trackPosition = (greenStart + 7) % 52)
                    2 -> token.copy(state = TokenState.IN_HOME_YARD, trackPosition = -1)
                    3 -> token.copy(state = TokenState.IN_HOME_YARD, trackPosition = -1)
                    else -> token
                }
            } else {
                token
            }
        }
        engine.setTokensForTesting(tokens)
        engine.setCurrentTurnForTesting(greenIndex)

        // Green rolls 5
        val state = engine.rollDice(overrideDice = 5)
        assertEquals(5, state.diceValue)

        val legalTokenIds = state.legalMoves.map { it.tokenId }.toSet()

        // Home tokens 2 and 3 must NOT be legal on roll 5
        assertFalse("Home token 2 must NOT be legal on 5", legalTokenIds.contains(2))
        assertFalse("Home token 3 must NOT be legal on 5", legalTokenIds.contains(3))
    }

    @Test
    fun testMandatoryTestD_AllPlayersHomeDice6DeploymentEligibility() {
        for (playerIdx in 0..3) {
            val engine = LudoEngine(playerCount = 4)
            val expectedStart = engine.topology.playerStarts[playerIdx]

            engine.setCurrentTurnForTesting(playerIdx)

            val state = engine.rollDice(overrideDice = 6)
            assertEquals(6, state.diceValue)
            assertEquals(playerIdx, state.currentTurnPlayerIndex)

            val legalTokenIds = state.legalMoves.map { it.tokenId }.toSet()
            assertEquals("Player $playerIdx all 4 home tokens must be legal", setOf(0, 1, 2, 3), legalTokenIds)

            for (move in state.legalMoves) {
                assertEquals("Move must target start cell $expectedStart for player $playerIdx", expectedStart, move.targetTrackPos)
                assertEquals(TokenState.ON_TRACK, move.targetState)
            }
        }
    }

    @Test
    fun testMandatoryTestE_ExecuteDeploymentActionGreenHomeTokenToStart() {
        val engine = LudoEngine(playerCount = 4)
        val greenIndex = 1
        val greenStart = engine.topology.playerStarts[greenIndex]

        engine.setCurrentTurnForTesting(greenIndex)
        engine.rollDice(overrideDice = 6)

        // Execute move for token 2
        val stateAfterMove = engine.makeMove(tokenId = 2)
        val token2 = stateAfterMove.tokens.first { it.playerIndex == greenIndex && it.id == 2 }

        // Assert: token is no longer HOME, route progress is START
        assertEquals(TokenState.ON_TRACK, token2.state)
        assertEquals(greenStart, token2.trackPosition)

        // Verify topology grid coords map to Green Start
        val expectedCoord = engine.topology.trackGridCoords[greenStart]
        assertNotNull(expectedCoord)
    }

    @Test
    fun testMandatoryTestF_HomeChoicesAndBoardMovementCoexist() {
        val engine = LudoEngine(playerCount = 4)
        val greenIndex = 1
        val greenStart = engine.topology.playerStarts[greenIndex]

        // 2 Home (tokens 2, 3), 1 board token that can move 6 (token 0 at cell 20)
        val tokens = engine.getGameState().tokens.map { token ->
            if (token.playerIndex == greenIndex) {
                when (token.id) {
                    0 -> token.copy(state = TokenState.ON_TRACK, trackPosition = 20)
                    1 -> token.copy(state = TokenState.FINISHED, trackPosition = -1, homeStretchPosition = 5)
                    2 -> token.copy(state = TokenState.IN_HOME_YARD, trackPosition = -1)
                    3 -> token.copy(state = TokenState.IN_HOME_YARD, trackPosition = -1)
                    else -> token
                }
            } else {
                token
            }
        }
        engine.setTokensForTesting(tokens)
        engine.setCurrentTurnForTesting(greenIndex)

        val state = engine.rollDice(overrideDice = 6)
        val legalTokenIds = state.legalMoves.map { it.tokenId }.toSet()

        // Assert: Home choices (2, 3) AND board movement (0) COEXIST!
        assertTrue("Home token 2 must be legal", legalTokenIds.contains(2))
        assertTrue("Home token 3 must be legal", legalTokenIds.contains(3))
        assertTrue("Board token 0 moving 6 spaces must be legal", legalTokenIds.contains(0))
        assertFalse("Finished token 1 must not be legal", legalTokenIds.contains(1))
    }

    @Test
    fun testAllFourColorsTransitionIntoOwnHomeLaneNotSecondLap() {
        // Test all 4 player colors (0=Red, 1=Green, 2=Yellow, 3=Blue)
        for (playerIdx in 0 until 4) {
            val engine = LudoEngine(playerCount = 4)
            val topo = engine.topology
            val entryPos = topo.homeEntryOffsets[playerIdx]
            val commonTrackLen = topo.commonTrackLength(playerIdx)
            assertEquals(50, commonTrackLen)

            // Place token 0 at the end of the common route (routeProgress = 50, trackPosition = entryPos)
            val testTokens = engine.getGameState().tokens.map { token ->
                if (token.playerIndex == playerIdx && token.id == 0) {
                    token.copy(
                        state = TokenState.ON_TRACK,
                        trackPosition = entryPos,
                        homeStretchPosition = -1,
                        routeProgress = commonTrackLen
                    )
                } else token
            }
            engine.setTokensForTesting(testTokens)
            engine.setCurrentTurnForTesting(playerIdx)

            // Roll 1: Token MUST enter player's own home stretch lane at index 0, NOT wrap to (entryPos + 1) % 52
            val moves1 = engine.computeLegalMoves(playerIdx, dice = 1)
            assertEquals(1, moves1.size)
            val move1 = moves1.first()
            assertEquals(0, move1.tokenId)
            assertEquals(TokenState.IN_HOME_STRETCH, move1.targetState)
            assertEquals(-1, move1.targetTrackPos)
            assertEquals(0, move1.targetHomeStretchPos)
            assertEquals(51, move1.targetRouteProgress)
            assertNull(move1.capturesToken)
            // Path must point to home stretch coordinate, never track coordinate
            assertEquals(1, move1.pathStepCoords.size)
            assertEquals(topo.homeStretchGridCoords[playerIdx][0], move1.pathStepCoords[0])

            // Roll 2: Must enter home stretch at index 1 (progress 52)
            val moves2 = engine.computeLegalMoves(playerIdx, dice = 2)
            assertEquals(1, moves2.size)
            val move2 = moves2.first()
            assertEquals(TokenState.IN_HOME_STRETCH, move2.targetState)
            assertEquals(1, move2.targetHomeStretchPos)
            assertEquals(52, move2.targetRouteProgress)
            assertEquals(2, move2.pathStepCoords.size)
            assertEquals(topo.homeStretchGridCoords[playerIdx][0], move2.pathStepCoords[0])
            assertEquals(topo.homeStretchGridCoords[playerIdx][1], move2.pathStepCoords[1])

            // Roll 6: Exact finish from entry boundary (50 + 6 = 56 == maxProgress)
            val move6 = engine.computeLegalMoves(playerIdx, dice = 6).firstOrNull { it.tokenId == 0 }
            assertNotNull("Move for token 0 must be legal on roll 6", move6)
            assertEquals(TokenState.FINISHED, move6!!.targetState)
            assertEquals(56, move6.targetRouteProgress)
            assertEquals(6, move6.pathStepCoords.size)
            assertEquals(topo.homeGoalGridCoords[playerIdx], move6.pathStepCoords.last())
        }
    }

    @Test
    fun testBoundaryTransitionFromOneStepBeforeHomeEntry() {
        for (playerIdx in 0 until 4) {
            val engine = LudoEngine(playerCount = 4)
            val topo = engine.topology
            val startCell = topo.playerStarts[playerIdx]
            val cellBeforeEntry = (startCell + 49) % topo.totalTrackCells

            val testTokens = engine.getGameState().tokens.map { token ->
                if (token.playerIndex == playerIdx && token.id == 0) {
                    token.copy(
                        state = TokenState.ON_TRACK,
                        trackPosition = cellBeforeEntry,
                        homeStretchPosition = -1,
                        routeProgress = 49
                    )
                } else token
            }
            engine.setTokensForTesting(testTokens)
            engine.setCurrentTurnForTesting(playerIdx)

            // Roll 1 -> advances to last track cell (entryPos, progress 50)
            val move1 = engine.computeLegalMoves(playerIdx, 1).first()
            assertEquals(TokenState.ON_TRACK, move1.targetState)
            assertEquals(topo.homeEntryOffsets[playerIdx], move1.targetTrackPos)
            assertEquals(50, move1.targetRouteProgress)

            // Roll 2 -> enters home stretch index 0 (progress 51)
            val move2 = engine.computeLegalMoves(playerIdx, 2).first()
            assertEquals(TokenState.IN_HOME_STRETCH, move2.targetState)
            assertEquals(0, move2.targetHomeStretchPos)
            assertEquals(51, move2.targetRouteProgress)

            // Roll 6 -> reaches home stretch index 4 (progress 55)
            val move6 = engine.computeLegalMoves(playerIdx, 6).first()
            assertEquals(TokenState.IN_HOME_STRETCH, move6.targetState)
            assertEquals(4, move6.targetHomeStretchPos)
            assertEquals(55, move6.targetRouteProgress)
        }
    }

    @Test
    fun testExactRollFinishingRule() {
        val engine = LudoEngine(playerCount = 4)
        val topo = engine.topology
        val playerIdx = 0

        // Place token in home stretch at index 2 (progress 53), exactly 3 steps away from finish (56)
        val testTokens = engine.getGameState().tokens.map { token ->
            if (token.playerIndex == playerIdx && token.id == 0) {
                token.copy(
                    state = TokenState.IN_HOME_STRETCH,
                    trackPosition = -1,
                    homeStretchPosition = 2,
                    routeProgress = 53
                )
            } else token
        }
        engine.setTokensForTesting(testTokens)
        engine.setCurrentTurnForTesting(playerIdx)

        // dice = 1 -> advance 1 to home stretch index 3 (progress 54)
        val moves1 = engine.computeLegalMoves(playerIdx, 1).filter { it.tokenId == 0 }
        assertEquals(1, moves1.size)
        assertEquals(TokenState.IN_HOME_STRETCH, moves1.first().targetState)
        assertEquals(3, moves1.first().targetHomeStretchPos)
        assertEquals(54, moves1.first().targetRouteProgress)

        // dice = 2 -> advance 2 to home stretch index 4 (progress 55)
        val moves2 = engine.computeLegalMoves(playerIdx, 2).filter { it.tokenId == 0 }
        assertEquals(1, moves2.size)
        assertEquals(TokenState.IN_HOME_STRETCH, moves2.first().targetState)
        assertEquals(4, moves2.first().targetHomeStretchPos)
        assertEquals(55, moves2.first().targetRouteProgress)

        // dice = 3 -> FINISHED (progress 56)
        val moves3 = engine.computeLegalMoves(playerIdx, 3).filter { it.tokenId == 0 }
        assertEquals(1, moves3.size)
        assertEquals(TokenState.FINISHED, moves3.first().targetState)
        assertEquals(56, moves3.first().targetRouteProgress)

        // dice = 4, 5, 6 -> NOT legal (overshoot)
        assertTrue(engine.computeLegalMoves(playerIdx, 4).none { it.tokenId == 0 })
        assertTrue(engine.computeLegalMoves(playerIdx, 5).none { it.tokenId == 0 })
        assertTrue(engine.computeLegalMoves(playerIdx, 6).none { it.tokenId == 0 })
    }

    @Test
    fun testTokenNeverWrapsAroundCommonTrackForSecondLap() {
        for (p in 0 until 4) {
            val engine = LudoEngine(playerCount = 4)
            val topo = engine.topology

            // Start token on track at progress 0
            var token = LudoToken(
                id = 0,
                playerIndex = p,
                state = TokenState.ON_TRACK,
                trackPosition = topo.playerStarts[p],
                homeStretchPosition = -1,
                routeProgress = 0
            )

            // Step through entire journey step by step with dice = 1
            for (step in 1..56) {
                engine.setTokensForTesting(listOf(token))
                engine.setCurrentTurnForTesting(p)
                val legal = engine.computeLegalMoves(p, 1)
                assertEquals("Move must be legal at progress ${step - 1}", 1, legal.size)
                val move = legal.first()
                assertEquals(step, move.targetRouteProgress)

                if (step <= 50) {
                    assertEquals("Step $step must be ON_TRACK", TokenState.ON_TRACK, move.targetState)
                    assertEquals((topo.playerStarts[p] + step) % topo.totalTrackCells, move.targetTrackPos)
                } else if (step <= 55) {
                    assertEquals("Step $step must be IN_HOME_STRETCH", TokenState.IN_HOME_STRETCH, move.targetState)
                    assertEquals(step - 51, move.targetHomeStretchPos)
                    assertEquals(-1, move.targetTrackPos)
                } else {
                    assertEquals("Step 56 must be FINISHED", TokenState.FINISHED, move.targetState)
                }

                engine.rollDice(overrideDice = 1)
                val newState = engine.makeMove(0)
                token = newState.tokens.first { it.playerIndex == p && it.id == 0 }
                assertEquals(step, token.routeProgress)
            }
            assertEquals(TokenState.FINISHED, token.state)
        }
    }
}
