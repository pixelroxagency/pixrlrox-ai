package com.example.data.repository

import com.example.core.database.dao.LudoGameDao
import com.example.core.database.entity.LudoGameEntity
import com.example.core.games.ludo.LudoEngine
import com.example.core.games.ludo.LudoGameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LudoRepository(
    private val ludoGameDao: LudoGameDao
) {
    val savedGameFlow: Flow<LudoGameEntity?> = ludoGameDao.getSavedGameFlow()

    suspend fun getSavedGame(): LudoGameEntity? = withContext(Dispatchers.IO) {
        ludoGameDao.getSavedGame()
    }

    suspend fun saveGame(engine: LudoEngine, state: LudoGameState) = withContext(Dispatchers.IO) {
        val entity = LudoGameEntity(
            id = 1,
            playerCount = state.playerCount,
            currentTurnIndex = state.currentTurnPlayerIndex,
            diceValue = state.diceValue,
            hasRolledDice = state.hasRolledDice,
            consecutiveSixes = state.consecutiveSixes,
            tokensJson = engine.serializeTokens(),
            winnerIndex = state.winnerPlayerIndex,
            updatedAt = System.currentTimeMillis()
        )
        ludoGameDao.saveGame(entity)
    }

    suspend fun clearSavedGame() = withContext(Dispatchers.IO) {
        ludoGameDao.clearGame()
    }
}
