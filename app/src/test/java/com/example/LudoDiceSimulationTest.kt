package com.example

import com.example.core.games.ludo.DiceMode
import com.example.core.games.ludo.LudoEngine
import com.example.core.games.ludo.LudoRuleSet
import com.example.core.games.ludo.SmartDicePolicy
import com.example.core.games.ludo.TokenState
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LudoDiceSimulationTest {

    @Test
    fun run100000RollsSimulationsRealEngine() {
        println("=== 100,000 ROLLS DETERMINISTIC SIMULATION RESULTS (25% Final Capture Cap) ===")

        val policy = SmartDicePolicy()
        val rng = Random(42)
        val ruleSet = LudoRuleSet()
        val numRolls = 100000

        // Helper to run simulation and print
        fun runScenario(name: String, setupEngine: () -> LudoEngine, expectedCaptureValues: Set<Int>? = null) {
            val engine = setupEngine()
            val captureValues = expectedCaptureValues ?: engine.getLegalCaptureDiceValues(0)
            var captureCount = 0
            val dist = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0)

            for (i in 0 until numRolls) {
                val roll = policy.roll(0, 0, engine, ruleSet, DiceMode.STANDARD_RANDOM, rng)
                dist[roll] = (dist[roll] ?: 0) + 1
                if (captureValues.contains(roll)) captureCount++
            }

            val pct = (captureCount.toDouble() / numRolls.toDouble()) * 100.0
            println("Scenario $name (captureValues: $captureValues):")
            println("  Total Capture Frequency: ${String.format("%.2f", pct)}% (Count: $captureCount/$numRolls)")
            println("  Dice distribution: $dist")

            if (captureValues.isNotEmpty() && captureValues.size < 6) {
                assertTrue("Capture frequency (${pct}%) must be approximately 25% (23% - 27%)", pct in 23.0..27.0)
            }
        }

        // Scenario A: captureValues = {4} (opponent 4 steps ahead of red)
        runScenario("A", {
            val engine = LudoEngine(playerCount = 4)
            val tokens = engine.getGameState().tokens.toMutableList()
            val red = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
            tokens[red] = tokens[red].copy(state = TokenState.ON_TRACK, trackPosition = 10, routeProgress = 10)
            val green = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
            tokens[green] = tokens[green].copy(state = TokenState.ON_TRACK, trackPosition = 14, routeProgress = 42)
            engine.setTokensForTesting(tokens)
            engine
        })

        // Scenario B: captureValues = {2, 5}
        runScenario("B", {
            val engine = LudoEngine(playerCount = 4)
            val tokens = engine.getGameState().tokens.toMutableList()
            val red = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
            tokens[red] = tokens[red].copy(state = TokenState.ON_TRACK, trackPosition = 10, routeProgress = 10)
            val green = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
            tokens[green] = tokens[green].copy(state = TokenState.ON_TRACK, trackPosition = 12, routeProgress = 42)
            val yellow = tokens.indexOfFirst { it.playerIndex == 2 && it.id == 0 }
            tokens[yellow] = tokens[yellow].copy(state = TokenState.ON_TRACK, trackPosition = 15, routeProgress = 42)
            engine.setTokensForTesting(tokens)
            engine
        })

        // Scenario C: captureValues = {1, 3, 5}
        runScenario("C", {
            val engine = LudoEngine(playerCount = 4)
            val tokens = engine.getGameState().tokens.toMutableList()
            val red = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
            tokens[red] = tokens[red].copy(state = TokenState.ON_TRACK, trackPosition = 10, routeProgress = 10)
            val g1 = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 0 }
            tokens[g1] = tokens[g1].copy(state = TokenState.ON_TRACK, trackPosition = 11, routeProgress = 42)
            val g2 = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 1 }
            tokens[g2] = tokens[g2].copy(state = TokenState.ON_TRACK, trackPosition = 13, routeProgress = 42)
            val g3 = tokens.indexOfFirst { it.playerIndex == 1 && it.id == 2 }
            tokens[g3] = tokens[g3].copy(state = TokenState.ON_TRACK, trackPosition = 15, routeProgress = 42)
            engine.setTokensForTesting(tokens)
            engine
        })

        // Scenario D: captureValues = {1, 2, 3, 4, 5}
        runScenario("D", {
            val engine = LudoEngine(playerCount = 4)
            val tokens = engine.getGameState().tokens.toMutableList()
            val red = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
            tokens[red] = tokens[red].copy(state = TokenState.ON_TRACK, trackPosition = 10, routeProgress = 10)
            for (p in 1..3) {
                for (tId in 0..1) {
                    val idx = tokens.indexOfFirst { it.playerIndex == p && it.id == tId }
                    if (idx != -1) {
                        tokens[idx] = tokens[idx].copy(state = TokenState.ON_TRACK, trackPosition = 10 + (p * 2 + tId), routeProgress = 42)
                    }
                }
            }
            engine.setTokensForTesting(tokens)
            engine
        }, setOf(1, 2, 3, 4, 5))

        // Scenario E: captureValues = empty (normal dice)
        runScenario("E", {
            LudoEngine(playerCount = 4)
        }, emptySet())

        // Scenario F: captureValues = {1, 2, 3, 4, 5, 6} (edge case: all are capture values)
        runScenario("F", {
            val engine = LudoEngine(playerCount = 4)
            val tokens = engine.getGameState().tokens.toMutableList()
            val red = tokens.indexOfFirst { it.playerIndex == 0 && it.id == 0 }
            tokens[red] = tokens[red].copy(state = TokenState.ON_TRACK, trackPosition = 10, routeProgress = 10)
            for (cellOffset in 1..6) {
                val pIdx = cellOffset % 3 + 1
                val tId = cellOffset / 3
                val idx = tokens.indexOfFirst { it.playerIndex == pIdx && it.id == tId }
                if (idx != -1) {
                    tokens[idx] = tokens[idx].copy(state = TokenState.ON_TRACK, trackPosition = 10 + cellOffset, routeProgress = 42)
                }
            }
            engine.setTokensForTesting(tokens)
            engine
        }, setOf(1, 2, 3, 4, 5, 6))
    }
}
