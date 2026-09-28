package com.example

import com.example.core.games.ludo.LudoGameState
import com.example.core.games.ludo.LudoMove
import com.example.core.games.ludo.LudoPlayer
import com.example.core.games.ludo.LudoTurnState
import com.example.core.games.ludo.TokenState
import com.example.core.games.ludo.LudoToken
import com.example.ui.screens.games.determinePrimaryLegalTokenInCell
import com.example.ui.screens.games.getStackedTokenPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoStackedTokenTest {

    private fun createPlayers(count: Int = 4): List<LudoPlayer> {
        val colors = listOf(0xFFEA2127, 0xFF0F9D58, 0xFFFFCC00, 0xFF1976D2)
        val names = listOf("Red", "Green", "Yellow", "Blue")
        return (0 until count).map { i ->
            LudoPlayer(
                index = i,
                name = names[i],
                colorHex = colors[i],
                isAI = false,
                isActive = true
            )
        }
    }

    private fun createTestToken(playerIndex: Int, id: Int, trackPos: Int): LudoToken {
        return LudoToken(
            id = id,
            playerIndex = playerIndex,
            state = TokenState.ON_TRACK,
            trackPosition = trackPos,
            routeProgress = 5
        )
    }

    private fun createTestMove(playerIndex: Int, tokenId: Int, targetTrackPos: Int): LudoMove {
        return LudoMove(
            tokenId = tokenId,
            playerIndex = playerIndex,
            targetState = TokenState.ON_TRACK,
            targetTrackPos = targetTrackPos,
            targetHomeStretchPos = -1,
            targetRouteProgress = targetTrackPos
        )
    }

    /**
     * Case 1:
     * Two tokens on same cell: Token A selectable, Token B not selectable.
     * Expected:
     * - Token A priority > Token B priority (tier 2 / 200+ vs tier 0 / 1+).
     * - Sorted order places Token A last (rendered topmost in Compose child hierarchy).
     */
    @Test
    fun testCase1_twoTokensOneSelectable_selectableRendersTopmost() {
        val players = createPlayers()
        val tokenA = createTestToken(playerIndex = 0, id = 0, trackPos = 5) // Red, selectable
        val tokenB = createTestToken(playerIndex = 1, id = 0, trackPos = 5) // Green, not selectable

        val legalMoveA = createTestMove(playerIndex = 0, tokenId = 0, targetTrackPos = 8)

        val gameState = LudoGameState(
            playerCount = 4,
            players = players,
            tokens = listOf(tokenA, tokenB),
            currentTurnPlayerIndex = 0,
            diceValue = 3,
            hasRolledDice = true,
            turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
            legalMoves = listOf(legalMoveA)
        )

        val legalTokenIds = setOf(0)
        val tokensInCell = listOf(tokenB, tokenA) // arbitrary incoming order

        val primaryTokenId = determinePrimaryLegalTokenInCell(tokensInCell, gameState, legalTokenIds)
        assertEquals(0, primaryTokenId)

        val priorityA = getStackedTokenPriority(tokenA, gameState, legalTokenIds, primaryTokenId)
        val priorityB = getStackedTokenPriority(tokenB, gameState, legalTokenIds, primaryLegalTokenIdInCell = primaryTokenId)

        // Selectable token A must have higher priority tier (>= 200f) than inactive token B (< 10f)
        assertTrue("Selectable token A priority must be in top tier (>= 200f)", priorityA >= 200f)
        assertTrue("Inactive token B priority must be in bottom tier (< 10f)", priorityB < 10f)
        assertTrue("Token A must strictly outrank Token B", priorityA > priorityB)

        // Sorting for Compose rendering places topmost token at the end
        val sorted = tokensInCell.sortedWith(
            compareBy<LudoToken> { token ->
                getStackedTokenPriority(token, gameState, legalTokenIds, primaryTokenId)
            }.thenBy { token ->
                token.playerIndex * 4 + token.id
            }
        )

        assertEquals("Inactive token B must be composed first (underneath)", tokenB.playerIndex, sorted[0].playerIndex)
        assertEquals("Active token A must be composed last (on top)", tokenA.playerIndex, sorted[1].playerIndex)
    }

    /**
     * Case 2:
     * Three/four tokens on same cell: only one legal.
     * Expected:
     * - Legal token appears on top and has topmost priority.
     */
    @Test
    fun testCase2_fourTokensOnSameCellOnlyOneLegal_legalTokenTopmost() {
        val players = createPlayers()
        val tokenRed = createTestToken(playerIndex = 0, id = 0, trackPos = 8) // Red, inactive
        val tokenGreen = createTestToken(playerIndex = 1, id = 0, trackPos = 8) // Green, inactive
        val tokenYellow = createTestToken(playerIndex = 2, id = 0, trackPos = 8) // Yellow, inactive
        val tokenBlue = createTestToken(playerIndex = 3, id = 0, trackPos = 8) // Blue, active/legal

        val legalMoveBlue = createTestMove(playerIndex = 3, tokenId = 0, targetTrackPos = 12)

        val gameState = LudoGameState(
            playerCount = 4,
            players = players,
            tokens = listOf(tokenRed, tokenGreen, tokenYellow, tokenBlue),
            currentTurnPlayerIndex = 3, // Blue's turn
            diceValue = 4,
            hasRolledDice = true,
            turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
            legalMoves = listOf(legalMoveBlue)
        )

        val legalTokenIds = setOf(0)
        val tokensInCell = listOf(tokenBlue, tokenRed, tokenGreen, tokenYellow) // mixed order

        val primaryTokenId = determinePrimaryLegalTokenInCell(tokensInCell, gameState, legalTokenIds)
        assertEquals(0, primaryTokenId)

        val sorted = tokensInCell.sortedWith(
            compareBy<LudoToken> { token ->
                getStackedTokenPriority(token, gameState, legalTokenIds, primaryTokenId)
            }.thenBy { token ->
                token.playerIndex * 4 + token.id
            }
        )

        assertEquals("Blue token must be at last index to render topmost", 3, sorted.last().playerIndex)
        assertTrue("Blue token priority must be >= 200f",
            getStackedTokenPriority(sorted.last(), gameState, legalTokenIds, primaryTokenId) >= 200f)

        // Verify all 3 other tokens are in inactive tier
        for (i in 0 until 3) {
            assertTrue("Token at index $i must be inactive (< 10f)",
                getStackedTokenPriority(sorted[i], gameState, legalTokenIds, primaryTokenId) < 10f)
        }
    }

    /**
     * Case 3:
     * Two same-player tokens stacked and both legal.
     * Expected:
     * - The visually active/top token receives the tap reliably.
     * - Deterministic primary designation.
     */
    @Test
    fun testCase3_twoSamePlayerTokensStackedBothLegal_deterministicTop() {
        val players = createPlayers()
        val token0 = createTestToken(playerIndex = 0, id = 0, trackPos = 10)
        val token1 = createTestToken(playerIndex = 0, id = 1, trackPos = 10)

        val move0 = createTestMove(playerIndex = 0, tokenId = 0, targetTrackPos = 12)
        val move1 = createTestMove(playerIndex = 0, tokenId = 1, targetTrackPos = 12)

        val gameState = LudoGameState(
            playerCount = 4,
            players = players,
            tokens = listOf(token0, token1),
            currentTurnPlayerIndex = 0,
            diceValue = 2,
            hasRolledDice = true,
            turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
            legalMoves = listOf(move0, move1)
        )

        val legalTokenIds = setOf(0, 1)
        val tokensInCell = listOf(token0, token1)

        val primaryTokenId = determinePrimaryLegalTokenInCell(tokensInCell, gameState, legalTokenIds)
        assertEquals("Token 0 should be deterministically designated primary", 0, primaryTokenId)

        val priority0 = getStackedTokenPriority(token0, gameState, legalTokenIds, primaryTokenId)
        val priority1 = getStackedTokenPriority(token1, gameState, legalTokenIds, primaryTokenId)

        // Both are legal (>= 100f), but primary is top tier (>= 200f)
        assertTrue("Token 0 must be in top tier (>= 200f)", priority0 >= 200f)
        assertTrue("Token 1 must be in selectable tier (>= 100f, < 200f)", priority1 in 100f..199f)
        assertTrue("Primary token 0 must have higher priority than token 1", priority0 > priority1)

        val sorted = tokensInCell.sortedWith(
            compareBy<LudoToken> { token ->
                getStackedTokenPriority(token, gameState, legalTokenIds, primaryTokenId)
            }.thenBy { token ->
                token.playerIndex * 4 + token.id
            }
        )
        assertEquals("Primary token 0 must be last (topmost)", 0, sorted.last().id)
    }

    /**
     * Case 4:
     * Opponent tokens on safe-cell stacking.
     * Expected:
     * - Inactive opponent token has priority < 10f, active token has priority >= 200f.
     */
    @Test
    fun testCase4_opponentSafeCellStacking_inactiveDoesNotBlockActive() {
        val players = createPlayers()
        val opponentToken = createTestToken(playerIndex = 1, id = 2, trackPos = 8) // Green on star
        val activeToken = createTestToken(playerIndex = 0, id = 1, trackPos = 8)   // Red on star

        val move = createTestMove(playerIndex = 0, tokenId = 1, targetTrackPos = 13)

        val gameState = LudoGameState(
            playerCount = 4,
            players = players,
            tokens = listOf(opponentToken, activeToken),
            currentTurnPlayerIndex = 0,
            diceValue = 5,
            hasRolledDice = true,
            turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
            legalMoves = listOf(move)
        )

        val legalTokenIds = setOf(1)
        val tokensInCell = listOf(opponentToken, activeToken)

        val primaryTokenId = determinePrimaryLegalTokenInCell(tokensInCell, gameState, legalTokenIds)
        assertEquals(1, primaryTokenId)

        val oppPriority = getStackedTokenPriority(opponentToken, gameState, legalTokenIds, primaryTokenId)
        val actPriority = getStackedTokenPriority(activeToken, gameState, legalTokenIds, primaryTokenId)

        assertTrue("Opponent token must be < 10f", oppPriority < 10f)
        assertTrue("Active token must be >= 200f", actPriority >= 200f)

        val sorted = tokensInCell.sortedWith(
            compareBy<LudoToken> { token ->
                getStackedTokenPriority(token, gameState, legalTokenIds, primaryTokenId)
            }.thenBy { token ->
                token.playerIndex * 4 + token.id
            }
        )
        assertEquals("Active Red token must be topmost", 0, sorted.last().playerIndex)
        assertEquals("Active Red token ID must be 1", 1, sorted.last().id)
    }

    /**
     * Case 5:
     * After turn changes, z-order immediately flips so the NEW selectable token becomes topmost.
     */
    @Test
    fun testCase5_turnChange_zOrderImmediatelyFlips() {
        val players = createPlayers()
        val redToken = createTestToken(playerIndex = 0, id = 0, trackPos = 8)
        val greenToken = createTestToken(playerIndex = 1, id = 0, trackPos = 8)
        val tokensInCell = listOf(redToken, greenToken)

        // --- Turn 1: Red's turn ---
        val redMove = createTestMove(playerIndex = 0, tokenId = 0, targetTrackPos = 9)
        val redGameState = LudoGameState(
            playerCount = 4,
            players = players,
            tokens = tokensInCell,
            currentTurnPlayerIndex = 0,
            diceValue = 1,
            hasRolledDice = true,
            turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
            legalMoves = listOf(redMove)
        )

        val redPrimary = determinePrimaryLegalTokenInCell(tokensInCell, redGameState, setOf(0))
        val redSorted = tokensInCell.sortedWith(
            compareBy<LudoToken> { token ->
                getStackedTokenPriority(token, redGameState, setOf(0), redPrimary)
            }.thenBy { token ->
                token.playerIndex * 4 + token.id
            }
        )
        assertEquals("During Red's turn, Red token must be topmost", 0, redSorted.last().playerIndex)

        // --- Turn 2: Turn passes to Green ---
        val greenMove = createTestMove(playerIndex = 1, tokenId = 0, targetTrackPos = 11)
        val greenGameState = LudoGameState(
            playerCount = 4,
            players = players,
            tokens = tokensInCell,
            currentTurnPlayerIndex = 1,
            diceValue = 3,
            hasRolledDice = true,
            turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
            legalMoves = listOf(greenMove)
        )

        val greenPrimary = determinePrimaryLegalTokenInCell(tokensInCell, greenGameState, setOf(0))
        val greenSorted = tokensInCell.sortedWith(
            compareBy<LudoToken> { token ->
                getStackedTokenPriority(token, greenGameState, setOf(0), greenPrimary)
            }.thenBy { token ->
                token.playerIndex * 4 + token.id
            }
        )
        assertEquals("During Green's turn, Green token must immediately become topmost", 1, greenSorted.last().playerIndex)
        assertTrue("Green token priority must now be >= 200f",
            getStackedTokenPriority(greenToken, greenGameState, setOf(0), greenPrimary) >= 200f)
        assertTrue("Red token priority must now drop to < 10f",
            getStackedTokenPriority(redToken, greenGameState, setOf(0), greenPrimary) < 10f)
    }

    /**
     * Case 6:
     * Test all four colors: RED (0), GREEN (1), YELLOW (2), BLUE (3).
     * Verify each color when active on a cell with tokens from all other 3 colors becomes topmost.
     */
    @Test
    fun testCase6_allFourColorsRedGreenYellowBlue() {
        val players = createPlayers()
        for (activePlayer in 0 until 4) {
            val tokens = (0 until 4).map { p -> createTestToken(playerIndex = p, id = 0, trackPos = 15) }
            val activeMove = createTestMove(playerIndex = activePlayer, tokenId = 0, targetTrackPos = 17)

            val gameState = LudoGameState(
                playerCount = 4,
                players = players,
                tokens = tokens,
                currentTurnPlayerIndex = activePlayer,
                diceValue = 2,
                hasRolledDice = true,
                turnState = LudoTurnState.WAITING_FOR_TOKEN_SELECTION,
                legalMoves = listOf(activeMove)
            )

            val primary = determinePrimaryLegalTokenInCell(tokens, gameState, setOf(0))
            assertEquals(0, primary)

            val sorted = tokens.sortedWith(
                compareBy<LudoToken> { token ->
                    getStackedTokenPriority(token, gameState, setOf(0), primary)
                }.thenBy { token ->
                    token.playerIndex * 4 + token.id
                }
            )

            assertEquals("Player $activePlayer must be rendered topmost", activePlayer, sorted.last().playerIndex)
            assertTrue("Player $activePlayer priority must be >= 200f",
                getStackedTokenPriority(sorted.last(), gameState, setOf(0), primary) >= 200f)

            // Verify all other 3 players are inactive (< 10f)
            for (i in 0 until 3) {
                assertTrue("Inactive token at index $i must be < 10f",
                    getStackedTokenPriority(sorted[i], gameState, setOf(0), primary) < 10f)
            }
        }
    }
}
