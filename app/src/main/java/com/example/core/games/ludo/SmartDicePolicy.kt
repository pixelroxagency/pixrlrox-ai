package com.example.core.games.ludo

import kotlin.random.Random

interface DicePolicy {
    fun roll(
        playerIndex: Int,
        consecutiveSixes: Int,
        engine: LudoEngine,
        ruleSet: LudoRuleSet,
        diceMode: DiceMode,
        randomSource: Random = Random.Default
    ): Int
}

class SmartDicePolicy : DicePolicy {

    override fun roll(
        playerIndex: Int,
        consecutiveSixes: Int,
        engine: LudoEngine,
        ruleSet: LudoRuleSet,
        diceMode: DiceMode,
        randomSource: Random
    ): Int {
        val candidateValues = mutableListOf(1, 2, 3, 4, 5, 6)
        if (consecutiveSixes >= ruleSet.maxConsecutiveSixes) {
            candidateValues.remove(6)
        }

        val captureDiceValues = engine.getLegalCaptureDiceValues(playerIndex).toMutableSet()
        if (consecutiveSixes >= ruleSet.maxConsecutiveSixes) {
            captureDiceValues.remove(6)
        }

        val nonCaptureValues = candidateValues.filter { !captureDiceValues.contains(it) }

        // If no capture values exist, or if all candidates are capture values (Edge Case F), use normal/standard dice behavior
        if (captureDiceValues.isEmpty() || nonCaptureValues.isEmpty()) {
            return rollNormalDice(
                playerIndex = playerIndex,
                consecutiveSixes = consecutiveSixes,
                engine = engine,
                ruleSet = ruleSet,
                diceMode = diceMode,
                randomSource = randomSource,
                candidateValues = candidateValues
            )
        }

        // Enforce 25% final probability cap for capture values, 75% for non-capture values
        val isCaptureBranch = randomSource.nextDouble() < 0.25
        return if (isCaptureBranch) {
            val sortedCaptures = captureDiceValues.toList().sorted()
            sortedCaptures[randomSource.nextInt(sortedCaptures.size)]
        } else {
            // 75% non-capture branch (strictly excludes captureDiceValues)
            if (diceMode == DiceMode.STANDARD_RANDOM) {
                nonCaptureValues.random(randomSource)
            } else {
                val playerTokens = engine.getGameState().tokens.filter { it.playerIndex == playerIndex && it.state == TokenState.ON_TRACK }
                val forbiddenDistances = mutableSetOf<Int>()
                for (t1 in playerTokens) {
                    for (t2 in playerTokens) {
                        if (t1 == t2) continue
                        val dist = (t2.trackPosition - t1.trackPosition + engine.topology.totalTrackCells) % engine.topology.totalTrackCells
                        if (dist in 1..6) {
                            forbiddenDistances.add(dist)
                        }
                    }
                }
                val allowedNonCapture = nonCaptureValues.filter { !forbiddenDistances.contains(it) }
                if (allowedNonCapture.isNotEmpty()) {
                    allowedNonCapture.random(randomSource)
                } else {
                    nonCaptureValues.random(randomSource)
                }
            }
        }
    }

    private fun rollNormalDice(
        playerIndex: Int,
        consecutiveSixes: Int,
        engine: LudoEngine,
        ruleSet: LudoRuleSet,
        diceMode: DiceMode,
        randomSource: Random,
        candidateValues: List<Int>
    ): Int {
        if (diceMode == DiceMode.STANDARD_RANDOM) {
            return candidateValues.random(randomSource)
        }

        // Own-pawn landing constraint (Smart Dice: avoids landing on own pawn if other options exist)
        val playerTokens = engine.getGameState().tokens.filter { it.playerIndex == playerIndex && it.state == TokenState.ON_TRACK }
        val forbiddenDistances = mutableSetOf<Int>()
        for (t1 in playerTokens) {
            for (t2 in playerTokens) {
                if (t1 == t2) continue
                val dist = (t2.trackPosition - t1.trackPosition + engine.topology.totalTrackCells) % engine.topology.totalTrackCells
                if (dist in 1..6) {
                    forbiddenDistances.add(dist)
                }
            }
        }

        val allowedValues = candidateValues.filter { candidate ->
            !forbiddenDistances.contains(candidate)
        }

        return if (allowedValues.isNotEmpty()) {
            allowedValues.random(randomSource)
        } else {
            candidateValues.random(randomSource)
        }
    }
}
