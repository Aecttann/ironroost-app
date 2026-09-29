package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The attract-mode pilot plays the menu's background battle. These pin what makes it watchable:
 * it fights, it keeps moving, and it never ends the show by shelling its own base.
 */
class TanksAttractPilotTest {

    private fun level(
        grid: List<String>,
        player: List<Int>,
        base: List<Int>,
        enemySpawns: List<List<Int>>,
        enemies: Int = 1,
        player2: List<Int>? = null
    ) = BattleCityLevelData(
        id = "attract-test",
        note = null,
        gridSize = BattleCityGridSize(cols = grid.first().length, rows = grid.size),
        spawnPoints = BattleCitySpawnPoints(enemy = enemySpawns, player1 = player, player2 = player2, base = base),
        tilesLegend = null,
        grid = grid,
        enemyGroups = listOf(BattleCityEnemyGroup("basic", enemies)),
        difficulty = 1
    )

    /** Steps until an enemy has finished spawning and is on the board, or fails. */
    private fun BattleCityEngine.untilEnemyOnBoard(): BattleCityRenderState {
        repeat(600) {
            val state = step(BattleCityFixedStepSeconds, BattleCityInputs.Idle).state
            if (state.enemies.isNotEmpty()) return state
        }
        error("no enemy ever finished spawning")
    }

    @Test
    fun turnsToAnEnemyInLineAndFires() {
        val engine = BattleCityEngine(
            stageNumber = 1,
            level = level(
                grid = listOf(
                    ".......",
                    ".......",
                    ".......",
                    ".......",
                    ".......",
                    ".......",
                    "...H..."
                ),
                player = listOf(3, 4),
                base = listOf(3, 6),
                enemySpawns = listOf(listOf(3, 0))
            ),
            seed = 7L
        )
        val state = engine.untilEnemyOnBoard()
        val enemy = state.enemies.first()
        val tank = assertNotNull(state.players.first().tank)
        assertTrue(kotlin.math.abs(enemy.x - tank.x) < 0.4f, "the fixture should line the enemy up in the tank's column")

        val input = TanksAttractPilot(seed = 1L).inputs(state, BattleCityFixedStepSeconds).first
        assertEquals(BattleCityDirection.Up, input.direction)
        assertTrue(input.firePressed)
    }

    @Test
    fun holdsFireWhenItsOwnBaseIsInTheWay() {
        // The enemy is below the player with the base between them: turning to it is fine,
        // firing would put a shell through the base.
        val engine = BattleCityEngine(
            stageNumber = 1,
            level = level(
                grid = listOf(
                    ".......",
                    ".......",
                    ".......",
                    ".......",
                    "...H...",
                    ".......",
                    "......."
                ),
                player = listOf(3, 1),
                base = listOf(3, 4),
                enemySpawns = listOf(listOf(3, 6))
            ),
            seed = 7L
        )
        val state = engine.untilEnemyOnBoard()
        val input = TanksAttractPilot(seed = 1L).inputs(state, BattleCityFixedStepSeconds).first
        assertEquals(BattleCityDirection.Down, input.direction)
        assertFalse(input.firePressed)
    }

    @Test
    fun aLongDemoKeepsMovingFiringAndScoring() {
        // Two piloted tanks and a wave of eight, for a minute and a half of simulated time. The
        // base is walled in steel so the run cannot end early on a lucky enemy shell; what is
        // being checked is that the pilots make a battle of it rather than sitting still.
        val grid = listOf(
            ".............",
            ".............",
            "..B..B.B..B..",
            "..B..B.B..B..",
            ".............",
            "...BB...BB...",
            ".............",
            "..B.......B..",
            "..B..BBB..B..",
            ".............",
            ".............",
            ".....SSS.....",
            ".....SHS....."
        )
        val engine = BattleCityEngine(
            stageNumber = 1,
            level = level(
                grid = grid,
                player = listOf(4, 12),
                player2 = listOf(8, 12),
                base = listOf(6, 12),
                enemySpawns = listOf(listOf(0, 0), listOf(6, 0), listOf(12, 0)),
                enemies = 8
            ),
            seed = 11L,
            initialLives = listOf(9, 9)
        )
        val pilot = TanksAttractPilot(seed = 3L)
        var state = engine.currentState()
        val start = state.players.map { it.tank?.x to it.tank?.y }
        var playerShots = 0
        repeat(60 * 90) {
            val step = engine.step(BattleCityFixedStepSeconds, pilot.inputs(state, BattleCityFixedStepSeconds))
            playerShots += step.state.bullets.count { it.isPlayerBullet }.coerceAtMost(1)
            state = step.state
            if (state.status != BattleCityStatus.Running) return@repeat
        }
        assertTrue(state.destroyedEnemies > 0, "ninety seconds of demo and nobody was hit")
        assertTrue(playerShots > 0, "the pilots never fired")
        val end = state.players.map { it.tank?.x to it.tank?.y }
        assertTrue(start != end, "the pilots never moved")
    }
}
