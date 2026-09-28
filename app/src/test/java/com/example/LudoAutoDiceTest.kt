package com.example

import com.example.core.games.ludo.AutoDiceController
import com.example.core.games.ludo.DiceMode
import com.example.core.games.ludo.LudoEngine
import com.example.core.games.ludo.LudoGameState
import com.example.core.games.ludo.LudoPlayer
import com.example.core.games.ludo.LudoRuleSet
import com.example.core.games.ludo.LudoTurnState
import com.example.core.games.ludo.TokenState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LudoAutoDiceTest {

    @Test
    fun defaultState_allPlayersAutoOff() {
        val controller = AutoDiceController()
        for (i in 0..3) {
            assertFalse("Player $i auto should be OFF by default", controller.isAutoDiceEnabled(i))
        }
    }

    @Test
    fun independentToggles_perPlayerState() {
        val controller = AutoDiceController()

        controller.setAutoDiceEnabled(0, true) // Red = ON
        controller.setAutoDiceEnabled(2, true) // Yellow = ON

        assertTrue(controller.isAutoDiceEnabled(0))
        assertFalse(controller.isAutoDiceEnabled(1))
        assertTrue(controller.isAutoDiceEnabled(2))
        assertFalse(controller.isAutoDiceEnabled(3))

        // Toggle Yellow OFF
        controller.toggleAutoDice(2)
        assertFalse(controller.isAutoDiceEnabled(2))
        assertTrue(controller.isAutoDiceEnabled(0))

        // Reset/clearAll
        controller.clearAll()
        for (i in 0..3) {
            assertFalse(controller.isAutoDiceEnabled(i))
        }
    }

    @Test
    fun canAutoRoll_validatesTurnStateAndPlayerAutoStatus() {
        val controller = AutoDiceController()
        val engine = LudoEngine(selectedPlayerCount = 4)
        val state = engine.getGameState()

        // P0 is current player, but auto is OFF
        assertFalse(controller.canAutoRoll(state, isRollingAnim = false))

        // Enable auto for P0
        controller.setAutoDiceEnabled(0, true)
        assertTrue(controller.canAutoRoll(state, isRollingAnim = false))

        // If dice animation is already active, must be false
        assertFalse(controller.canAutoRoll(state, isRollingAnim = true))

        // If turnState is ROLLING or WAITING_FOR_TOKEN_SELECTION, must be false
        val rollingState = state.copy(turnState = LudoTurnState.ROLLING)
        assertFalse(controller.canAutoRoll(rollingState, isRollingAnim = false))

        val selectState = state.copy(turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION)
        assertFalse(controller.canAutoRoll(selectState, isRollingAnim = false))

        // If winner exists, must be false
        val wonState = state.copy(winnerPlayerIndex = 0)
        assertFalse(controller.canAutoRoll(wonState, isRollingAnim = false))
    }

    @Test
    fun autoRoll_executesAfterDelay() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val controller = AutoDiceController()
        controller.setAutoDiceEnabled(0, true)

        val engine = LudoEngine(selectedPlayerCount = 4)
        val state = engine.getGameState()

        var rollCount = 0
        controller.scheduleAutoRoll(
            scope = testScope,
            opportunityId = state.rollOpportunityId,
            playerIndex = 0,
            delayMs = 800L,
            getState = { state },
            isBusy = { false },
            onRoll = { rollCount++ }
        )

        // At 700ms, roll has NOT fired yet
        testScheduler.advanceTimeBy(700)
        assertEquals(0, rollCount)

        // At 800ms+, roll fires exactly once
        testScheduler.advanceTimeBy(150)
        assertEquals(1, rollCount)

        // Further time advances do NOT fire again
        testScheduler.advanceTimeBy(1000)
        assertEquals(1, rollCount)
    }

    @Test
    fun manualRoll_cancelsPendingAutoRoll_preventingDuplicateRolls() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val controller = AutoDiceController()
        controller.setAutoDiceEnabled(0, true)

        val engine = LudoEngine(selectedPlayerCount = 4)
        val state = engine.getGameState()

        var autoRollCount = 0
        controller.scheduleAutoRoll(
            scope = testScope,
            opportunityId = state.rollOpportunityId,
            playerIndex = 0,
            delayMs = 800L,
            getState = { state },
            isBusy = { false },
            onRoll = { autoRollCount++ }
        )

        // User taps manually at 400ms before auto roll fires
        testScheduler.advanceTimeBy(400)
        controller.onManualRoll(state.rollOpportunityId)

        // Advance past the original auto-roll time
        testScheduler.advanceTimeBy(600)

        // Auto roll must NOT have fired because manual roll cancelled it!
        assertEquals(0, autoRollCount)
    }

    @Test
    fun turnPass_cancelsPendingAutoRoll() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val controller = AutoDiceController()
        controller.setAutoDiceEnabled(0, true)

        val engine = LudoEngine(selectedPlayerCount = 4)
        var state = engine.getGameState()

        var autoRollCount = 0
        controller.scheduleAutoRoll(
            scope = testScope,
            opportunityId = state.rollOpportunityId,
            playerIndex = 0,
            delayMs = 800L,
            getState = { state },
            isBusy = { false },
            onRoll = { autoRollCount++ }
        )

        // Opportunity changes (e.g. turn was passed or cancelled)
        testScheduler.advanceTimeBy(300)
        state = engine.passTurnToNextPlayer()
        controller.cancelPendingRoll()

        testScheduler.advanceTimeBy(700)
        assertEquals(0, autoRollCount)
    }

    @Test
    fun rollOpportunityId_incrementsAcrossTurnsAndBonusRolls() {
        val engine = LudoEngine(selectedPlayerCount = 4)
        val id1 = engine.getGameState().rollOpportunityId
        assertTrue(id1 > 0)

        val nextState = engine.passTurnToNextPlayer()
        val id2 = nextState.rollOpportunityId
        assertTrue("rollOpportunityId should increment on passTurnToNextPlayer", id2 > id1)

        // Deactivating current player should increment opportunity ID
        val activeState = engine.setPlayerActive(nextState.currentTurnPlayerIndex, false)
        val id3 = activeState.rollOpportunityId
        assertTrue("rollOpportunityId should increment when current player is deactivated", id3 > id2)
    }

    @Test
    fun autoRoll_doesNotMoveTokensAutomatically() = runTest {
        // Confirm that auto roll only invokes onRoll and does NOT alter tokens
        val controller = AutoDiceController()
        controller.setAutoDiceEnabled(0, true)

        val engine = LudoEngine(selectedPlayerCount = 4)
        val stateBefore = engine.getGameState()
        val yardTokensBefore = stateBefore.tokens.count { it.playerIndex == 0 && it.state == TokenState.IN_HOME_YARD }
        assertEquals(4, yardTokensBefore)

        var rollInvoked = false
        controller.scheduleAutoRoll(
            scope = this,
            opportunityId = stateBefore.rollOpportunityId,
            playerIndex = 0,
            delayMs = 10L,
            getState = { stateBefore },
            isBusy = { false },
            onRoll = { rollInvoked = true }
        )

        testScheduler.advanceTimeBy(20)
        assertTrue(rollInvoked)
        // Tokens must be unchanged
        val yardTokensAfter = engine.getGameState().tokens.count { it.playerIndex == 0 && it.state == TokenState.IN_HOME_YARD }
        assertEquals(4, yardTokensAfter)
    }
}
