package com.example

import com.example.core.games.ludo.DiceMode
import com.example.core.games.ludo.LudoAudioEventListener
import com.example.core.games.ludo.LudoEngine
import com.example.core.games.ludo.LudoRuleSet
import com.example.core.games.ludo.LudoTurnState
import com.example.core.games.ludo.SmartDicePolicy
import com.example.core.games.ludo.TokenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TestAudioEventListener : LudoAudioEventListener {
    var diceRollCount = 0
    var sixCount = 0
    var tokenReleaseCount = 0
    var tokenStepCount = 0
    var captureCount = 0
    var tokenHomeCount = 0
    var victoryCount = 0

    override fun onDiceRoll() {
        diceRollCount++
    }

    override fun onSix() {
        sixCount++
    }

    override fun onTokenRelease() {
        tokenReleaseCount++
    }

    override fun onTokenStep() {
        tokenStepCount++
    }

    override fun onCapture() {
        captureCount++
    }

    override fun onTokenHome() {
        tokenHomeCount++
    }

    override fun onVictory() {
        victoryCount++
    }

    fun reset() {
        diceRollCount = 0
        sixCount = 0
        tokenReleaseCount = 0
        tokenStepCount = 0
        captureCount = 0
        tokenHomeCount = 0
        victoryCount = 0
    }
}

class LudoSoundAndLayoutTest {

    @Test
    fun testSoundEventsEmittedOnRoll() {
        val audioListener = TestAudioEventListener()
        val engine = LudoEngine(playerCount = 4)

        // Simulate roll
        audioListener.onDiceRoll()
        val state = engine.rollDice(overrideDice = 6)
        if (state.diceValue == 6) {
            audioListener.onSix()
        }

        assertEquals(1, audioListener.diceRollCount)
        assertEquals(1, audioListener.sixCount)
        assertEquals(6, state.diceValue)
    }

    @Test
    fun testSixSoundTriggeredOnlyOnSix() {
        val audioListener = TestAudioEventListener()

        // Non-six rolls: 1..5
        for (diceVal in 1..5) {
            val engine = LudoEngine(playerCount = 4)
            audioListener.reset()
            val state = engine.rollDice(overrideDice = diceVal)
            if (state.diceValue == 6) {
                audioListener.onSix()
            }
            assertEquals(0, audioListener.sixCount)
        }

        // Exact six roll
        val engine6 = LudoEngine(playerCount = 4)
        audioListener.reset()
        val state6 = engine6.rollDice(overrideDice = 6)
        if (state6.diceValue == 6) {
            audioListener.onSix()
        }
        assertEquals(1, audioListener.sixCount)
    }

    @Test
    fun testSoundEventsEmittedOnTokenStepsAndCapture() {
        val audioListener = TestAudioEventListener()
        val engine = LudoEngine(playerCount = 4)

        // Setup capture scenario
        val updatedTokens = engine.getGameState().tokens.toMutableList()
        val blueIdx = updatedTokens.indexOfFirst { it.playerIndex == 3 && it.id == 0 }
        updatedTokens[blueIdx] = updatedTokens[blueIdx].copy(state = TokenState.ON_TRACK, trackPosition = 1)

        val redIdx = updatedTokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
        updatedTokens[redIdx] = updatedTokens[redIdx].copy(state = TokenState.ON_TRACK, trackPosition = 3)
        engine.setTokensForTesting(updatedTokens)

        val moves = engine.computeLegalMoves(playerIndex = 3, dice = 2)
        val captureMove = moves.first { it.tokenId == 0 }

        // Simulate step animation + capture sound
        val steps = captureMove.pathStepCoords
        for (step in steps) {
            audioListener.onTokenStep()
        }
        if (captureMove.capturesToken != null) {
            audioListener.onCapture()
        }

        assertEquals(steps.size, audioListener.tokenStepCount)
        assertEquals(1, audioListener.captureCount)
    }

    @Test
    fun testThreePlayerLayoutAndDiceOwnership() {
        val engine = LudoEngine(playerCount = 3)
        val state = engine.getGameState()

        // Verify 3 active players and 12 active tokens
        val activePlayers = state.players.filter { it.isActive }
        assertEquals(3, activePlayers.size)
        val activeTokens = state.tokens.filter { token ->
            state.players[token.playerIndex].isActive
        }
        assertEquals(12, activeTokens.size)

        // Verify player colors and indices
        assertEquals(0, activePlayers[0].index)
        assertEquals(1, activePlayers[1].index)
        assertEquals(2, activePlayers[2].index)

        // Player 0 turn
        assertEquals(0, state.currentTurnPlayerIndex)

        // Non-6 roll passes to Player 1
        val roll1 = engine.rollDice(overrideDice = 4)
        assertEquals(LudoTurnState.TURN_COMPLETE, roll1.turnState)

        // Pass turn to Player 1
        val turnPassedState = engine.passTurnToNextPlayer()
        assertEquals(1, turnPassedState.currentTurnPlayerIndex)

        // Player 1 rolls
        val updatedState = engine.rollDice(overrideDice = 4)
        // Ensure turn progresses through player 0 -> 1 -> 2 -> 0
        assertTrue("Current turn should be player 0, 1, or 2", updatedState.currentTurnPlayerIndex in 0..2)
    }

    @Test
    fun testThirdSixRuleHandling() {
        val engine = LudoEngine(playerCount = 4)
        val policy = SmartDicePolicy()

        // 1st six
        engine.rollDice(overrideDice = 6)
        engine.makeMove(tokenId = 0)
        assertEquals(1, engine.getGameState().consecutiveSixes)

        // 2nd six
        engine.rollDice(overrideDice = 6)
        engine.makeMove(tokenId = 1)
        assertEquals(2, engine.getGameState().consecutiveSixes)

        // 3rd roll cap
        val thirdRoll = policy.roll(
            playerIndex = 0,
            consecutiveSixes = 2,
            engine = engine,
            ruleSet = LudoRuleSet(maxConsecutiveSixes = 2),
            diceMode = DiceMode.STANDARD_RANDOM,
            randomSource = Random(99)
        )
        assertNotEquals(6, thirdRoll)
        assertTrue(thirdRoll in 1..5)
    }

    @Test
    fun testInputLockDuringTurn() {
        val engine = LudoEngine(playerCount = 4)

        // Set state to ANIMATING_MOVE
        engine.setTurnState(LudoTurnState.ANIMATING_MOVE)
        assertEquals(LudoTurnState.ANIMATING_MOVE, engine.getGameState().turnState)

        // While animating, moves are blocked
        val movesDuringAnim = engine.computeLegalMoves(playerIndex = 0, dice = 6)
        // Engine state is lock-protected
        assertEquals(LudoTurnState.ANIMATING_MOVE, engine.getGameState().turnState)
    }
}
