package com.example.core.games.ludo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Controller managing the optional per-player Auto Dice feature.
 *
 * Automates ONLY dice rolling (never token selection/movement).
 * Ensures exactly one roll per valid dice opportunity, prevents duplicate rolls,
 * and handles race conditions, user cancellations, and player removals safely.
 */
class AutoDiceController(
    initialAutoPlayers: Set<Int> = emptySet(),
    val naturalDelayMs: Long = 800L
) {
    var autoDicePlayers by mutableStateOf(initialAutoPlayers)
        private set

    private var pendingRollJob: Job? = null
    var lastExecutedOpportunityId: Long = -1L
        private set

    val isRollPending: Boolean
        get() = pendingRollJob?.isActive == true

    fun isAutoDiceEnabled(playerIndex: Int): Boolean {
        return autoDicePlayers.contains(playerIndex)
    }

    fun setAutoDiceEnabled(playerIndex: Int, enabled: Boolean) {
        autoDicePlayers = if (enabled) {
            autoDicePlayers + playerIndex
        } else {
            autoDicePlayers - playerIndex
        }
        if (!enabled) {
            cancelPendingRoll()
        }
    }

    fun toggleAutoDice(playerIndex: Int): Boolean {
        val newState = !isAutoDiceEnabled(playerIndex)
        setAutoDiceEnabled(playerIndex, newState)
        return newState
    }

    fun clearAll() {
        cancelPendingRoll()
        autoDicePlayers = emptySet()
        lastExecutedOpportunityId = -1L
    }

    fun cancelPendingRoll() {
        pendingRollJob?.cancel()
        pendingRollJob = null
    }

    fun onManualRoll(opportunityId: Long) {
        cancelPendingRoll()
        lastExecutedOpportunityId = opportunityId
    }

    fun canAutoRoll(
        state: LudoGameState,
        isRollingAnim: Boolean = false,
        isExecutingMove: Boolean = false
    ): Boolean {
        if (isRollingAnim || isExecutingMove) return false
        if (state.winnerPlayerIndex != null) return false
        if (state.turnState != LudoTurnState.WAITING_FOR_ROLL) return false
        if (state.hasRolledDice) return false

        val currentPlayer = state.players.getOrNull(state.currentTurnPlayerIndex) ?: return false
        if (!currentPlayer.isActive || currentPlayer.isFinished) return false

        return isAutoDiceEnabled(currentPlayer.index)
    }

    fun scheduleAutoRoll(
        scope: CoroutineScope,
        opportunityId: Long,
        playerIndex: Int,
        delayMs: Long = naturalDelayMs,
        getState: () -> LudoGameState,
        isBusy: () -> Boolean,
        onRoll: () -> Unit
    ): Boolean {
        if (lastExecutedOpportunityId == opportunityId) {
            return false
        }

        val currentState = getState()
        if (!canAutoRoll(currentState, isBusy())) {
            cancelPendingRoll()
            return false
        }

        cancelPendingRoll()

        pendingRollJob = scope.launch {
            try {
                delay(delayMs)
                val liveState = getState()
                val busy = isBusy()
                if (
                    lastExecutedOpportunityId != opportunityId &&
                    liveState.rollOpportunityId == opportunityId &&
                    liveState.currentTurnPlayerIndex == playerIndex &&
                    isAutoDiceEnabled(playerIndex) &&
                    canAutoRoll(liveState, busy)
                ) {
                    lastExecutedOpportunityId = opportunityId
                    onRoll()
                }
            } finally {
                if (pendingRollJob == coroutineContext[Job]) {
                    pendingRollJob = null
                }
            }
        }
        return true
    }
}
