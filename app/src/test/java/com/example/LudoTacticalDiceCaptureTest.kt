package com.example

import com.example.core.games.ludo.DiceMode
import com.example.core.games.ludo.LudoEngine
import com.example.core.games.ludo.LudoRuleSet
import com.example.core.games.ludo.LudoToken
import com.example.core.games.ludo.SmartDicePolicy
import com.example.core.games.ludo.TokenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LudoTacticalDiceCaptureTest {

    private fun createEngineWithAllTokensInYard(): LudoEngine {
        val engine = LudoEngine(playerCount = 4)
        return engine
    }

    @Test
    fun test1_opponentExactlyOneStepAheadIsIdentifiedAsCaptureValue() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Red (player 0) token 0 at track cell 2 (not safe)
        val redIdx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[redIdx] = tokens[redIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 2,
            routeProgress = 2
        )

        // Green (player 1) token 0 at track cell 3 (not safe) - exactly 1 step ahead of Red
        val greenIdx = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
        tokens[greenIdx] = tokens[greenIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 3,
            routeProgress = 42
        )

        engine.setTokensForTesting(tokens)

        val captureValues = engine.getLegalCaptureDiceValues(playerIndex = 0)
        assertTrue("Capture values should contain 1", captureValues.contains(1))
        assertEquals("Capture values should only contain 1", setOf(1), captureValues)
    }

    @Test
    fun test2_opponentExactlySixStepsAheadIsIdentifiedAsCaptureValue() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Red (player 0) token 0 at track cell 10 (not safe)
        val redIdx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[redIdx] = tokens[redIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 10,
            routeProgress = 10
        )

        // Yellow (player 2) token 0 at track cell 16 (not safe) - exactly 6 steps ahead of Red
        val yellowIdx = tokens.indexOfFirst { it.playerIndex == 2 && it.id == 0 }
        tokens[yellowIdx] = tokens[yellowIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 16,
            routeProgress = 42
        )

        engine.setTokensForTesting(tokens)

        val captureValues = engine.getLegalCaptureDiceValues(playerIndex = 0)
        assertTrue("Capture values should contain 6", captureValues.contains(6))
        assertEquals("Capture values should only contain 6", setOf(6), captureValues)
    }

    @Test
    fun test3_opponentSevenStepsAheadIsNotConsidered() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Red (player 0) token 0 at track cell 10 (not safe)
        val redIdx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[redIdx] = tokens[redIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 10,
            routeProgress = 10
        )

        // Blue (player 3) token 0 at track cell 17 (not safe) - 7 steps ahead
        val blueIdx = tokens.indexOfFirst { it.playerIndex == 3 && it.id == 0 }
        tokens[blueIdx] = tokens[blueIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 17,
            routeProgress = 30
        )

        engine.setTokensForTesting(tokens)

        val captureValues = engine.getLegalCaptureDiceValues(playerIndex = 0)
        assertFalse("Capture values must not contain 7 (dice only 1..6)", captureValues.contains(7))
        assertTrue("Capture values must be empty when opponent is 7 steps ahead", captureValues.isEmpty())
    }

    @Test
    fun test4_opponentThreeStepsAheadOnSafeCellMustNotBeConsideredCaptureValue() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Cell 8 is a safe cell in 4-player topology (star cell)
        assertTrue("Cell 8 must be a safe cell", engine.topology.safeCells.contains(8))

        // Red (player 0) token 0 at track cell 5 (not safe)
        val redIdx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[redIdx] = tokens[redIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 5,
            routeProgress = 5
        )

        // Green (player 1) token 0 at track cell 8 (SAFE CELL!) - 3 steps ahead
        val greenIdx = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
        tokens[greenIdx] = tokens[greenIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 8,
            routeProgress = 47
        )

        engine.setTokensForTesting(tokens)

        val captureValues = engine.getLegalCaptureDiceValues(playerIndex = 0)
        assertFalse("Capture values must NOT contain 3 because cell 8 is safe", captureValues.contains(3))
        assertTrue("Capture values must be empty when target is safe cell", captureValues.isEmpty())
    }

    @Test
    fun test5_multipleOwnTokensWithCapturesAtDistancesTwoAndFive() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Red token 0 at track cell 10 (not safe)
        val red0Idx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[red0Idx] = tokens[red0Idx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 10,
            routeProgress = 10
        )

        // Opponent (Green token 0) at cell 12 (dist 2 from Red 0, not safe)
        val green0Idx = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
        tokens[green0Idx] = tokens[green0Idx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 12,
            routeProgress = 51
        )

        // Red token 1 at track cell 20 (not safe)
        val red1Idx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 1 }
        tokens[red1Idx] = tokens[red1Idx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 20,
            routeProgress = 20
        )

        // Opponent (Blue token 0) at cell 25 (dist 5 from Red 1, not safe)
        val blue0Idx = tokens.indexOfFirst { it.playerIndex == 3 && it.id == 0 }
        tokens[blue0Idx] = tokens[blue0Idx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 25,
            routeProgress = 38
        )

        // Red token 2 at track cell 30 (no opponents within 1..6)
        val red2Idx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 2 }
        tokens[red2Idx] = tokens[red2Idx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 30,
            routeProgress = 30
        )

        engine.setTokensForTesting(tokens)

        val captureValues = engine.getLegalCaptureDiceValues(playerIndex = 0)
        assertEquals("Capture values should be exactly {2, 5}", setOf(2, 5), captureValues)
    }

    @Test
    fun test6_noCapturableOpponentsLeavesStandardDiceActive() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Red token 0 at track cell 10
        val red0Idx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[red0Idx] = tokens[red0Idx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 10,
            routeProgress = 10
        )

        // No opponents on track (all in yard)
        engine.setTokensForTesting(tokens)

        val captureValues = engine.getLegalCaptureDiceValues(playerIndex = 0)
        assertTrue("Capture values must be empty when no opponents are reachable", captureValues.isEmpty())

        // Standard dice policy should generate all valid rolls 1..6 over multiple trials
        val policy = SmartDicePolicy()
        val rolls = mutableSetOf<Int>()
        for (i in 0 until 100) {
            val r = policy.roll(
                playerIndex = 0,
                consecutiveSixes = 0,
                engine = engine,
                ruleSet = LudoRuleSet(),
                diceMode = DiceMode.STANDARD_RANDOM,
                randomSource = Random(i)
            )
            rolls.add(r)
        }
        assertEquals("Standard dice rolls must cover all 1..6 values", setOf(1, 2, 3, 4, 5, 6), rolls)
    }

    @Test
    fun test7_tacticalProbabilitySimulationOverSeededRNG() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Setup: Red token 0 at cell 2, opponent at cell 6 (dist 4, cell 6 is not safe)
        val redIdx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[redIdx] = tokens[redIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 2,
            routeProgress = 2
        )

        val greenIdx = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
        tokens[greenIdx] = tokens[greenIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 6,
            routeProgress = 45
        )

        engine.setTokensForTesting(tokens)

        val captureValues = engine.getLegalCaptureDiceValues(playerIndex = 0)
        assertEquals("Capture values should be exactly {4}", setOf(4), captureValues)

        val policy = SmartDicePolicy()
        val totalRolls = 10000
        var captureRollCount = 0
        val rng = Random(42)

        for (i in 0 until totalRolls) {
            val roll = policy.roll(
                playerIndex = 0,
                consecutiveSixes = 0,
                engine = engine,
                ruleSet = LudoRuleSet(),
                diceMode = DiceMode.STANDARD_RANDOM,
                randomSource = rng
            )
            if (roll == 4) {
                captureRollCount++
            }
        }

        val observedProbability = captureRollCount.toDouble() / totalRolls.toDouble()
        // Theoretical probability: 0.10 (tactical) + 0.90 * (1/6 standard) ≈ 0.10 + 0.15 = 0.25 (25%)
        println("Deterministic simulation: observed probability of capture roll 4 = $observedProbability ($captureRollCount / $totalRolls)")
        assertTrue(
            "Capture-enabling dice probability should be around 25% (observed: $observedProbability)",
            observedProbability in 0.20..0.32
        )
    }

    @Test
    fun test8_verifyAllFourPlayerColorsRedGreenYellowBlue() {
        val playerNames = listOf("RED", "GREEN", "YELLOW", "BLUE")
        val policy = SmartDicePolicy()

        for (playerIdx in 0 until 4) {
            val engine = createEngineWithAllTokensInYard()
            val tokens = engine.getGameState().tokens.toMutableList()
            val startCell = engine.topology.playerStarts[playerIdx]

            // Place active player's token 0 at startCell + 1
            val playerTrackPos = (startCell + 1) % engine.topology.totalTrackCells
            val playerTokIdx = tokens.indexOfFirst { it.playerIndex == playerIdx && it.id == 0 }
            tokens[playerTokIdx] = tokens[playerTokIdx].copy(
                state = TokenState.ON_TRACK,
                trackPosition = playerTrackPos,
                routeProgress = 1
            )

            // Place an opponent's token 3 steps ahead (targetPos = playerTrackPos + 3)
            val opponentPlayerIdx = (playerIdx + 1) % 4
            val opponentTrackPos = (playerTrackPos + 3) % engine.topology.totalTrackCells
            val oppTokIdx = tokens.indexOfFirst { it.playerIndex == opponentPlayerIdx && it.id == 0 }
            tokens[oppTokIdx] = tokens[oppTokIdx].copy(
                state = TokenState.ON_TRACK,
                trackPosition = opponentTrackPos,
                routeProgress = 20
            )

            engine.setTokensForTesting(tokens)

            // If target cell happens to be a safe cell in topology, adjust position to ensure capturable
            if (engine.topology.safeCells.contains(opponentTrackPos)) {
                // Shift both by 1 step so target is not safe
                val shiftedPlayerPos = (playerTrackPos + 1) % engine.topology.totalTrackCells
                val shiftedOpponentPos = (opponentTrackPos + 1) % engine.topology.totalTrackCells
                tokens[playerTokIdx] = tokens[playerTokIdx].copy(trackPosition = shiftedPlayerPos, routeProgress = 2)
                tokens[oppTokIdx] = tokens[oppTokIdx].copy(trackPosition = shiftedOpponentPos)
                engine.setTokensForTesting(tokens)
            }

            val captureValues = engine.getLegalCaptureDiceValues(playerIndex = playerIdx)
            assertTrue(
                "Player $playerIdx (${playerNames[playerIdx]}) should identify 3 as capture value",
                captureValues.contains(3)
            )

            // Roll 200 times and verify 3 is rolled with expected tactical boost (>= 12% on sample)
            var count3 = 0
            val rng = Random(playerIdx * 1000 + 7)
            for (i in 0 until 200) {
                val roll = policy.roll(
                    playerIndex = playerIdx,
                    consecutiveSixes = 0,
                    engine = engine,
                    ruleSet = LudoRuleSet(),
                    diceMode = DiceMode.STANDARD_RANDOM,
                    randomSource = rng
                )
                if (roll == 3) count3++
            }
            val p = count3.toDouble() / 200.0
            assertTrue(
                "Player $playerIdx (${playerNames[playerIdx]}) should have at least 12% probability (observed: $p)",
                p >= 0.12
            )
        }
    }

    @Test
    fun testMaxConsecutiveSixesExcludesSixFromCapture() {
        val engine = createEngineWithAllTokensInYard()
        val tokens = engine.getGameState().tokens.toMutableList()

        // Red at track 10, opponent at track 16 (distance 6)
        val redIdx = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        tokens[redIdx] = tokens[redIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 10,
            routeProgress = 10
        )

        val greenIdx = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
        tokens[greenIdx] = tokens[greenIdx].copy(
            state = TokenState.ON_TRACK,
            trackPosition = 16,
            routeProgress = 40
        )

        engine.setTokensForTesting(tokens)

        val policy = SmartDicePolicy()
        // If consecutiveSixes == 2 (maxConsecutiveSixes = 2), 6 is forbidden!
        for (i in 0 until 50) {
            val roll = policy.roll(
                playerIndex = 0,
                consecutiveSixes = 2,
                engine = engine,
                ruleSet = LudoRuleSet(maxConsecutiveSixes = 2),
                diceMode = DiceMode.STANDARD_RANDOM,
                randomSource = Random(i)
            )
            assertNotEquals("Should never roll 6 when maxConsecutiveSixes is reached", 6, roll)
        }
    }
}
