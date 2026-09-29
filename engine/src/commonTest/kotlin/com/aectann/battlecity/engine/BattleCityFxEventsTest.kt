package com.aectann.battlecity.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The screen's effects are placed from these events, so what matters is that each one arrives
 * when the thing happens, and where it happened.
 */
class BattleCityFxEventsTest {

    private fun level(
        grid: List<String>,
        player: List<Int>,
        base: List<Int>,
        enemySpawns: List<List<Int>> = listOf(listOf(0, 0)),
        enemies: Int = 1
    ) = BattleCityLevelData(
        id = "fx-test",
        note = null,
        gridSize = BattleCityGridSize(cols = grid.first().length, rows = grid.size),
        spawnPoints = BattleCitySpawnPoints(enemy = enemySpawns, player1 = player, player2 = null, base = base),
        tilesLegend = null,
        grid = grid,
        enemyGroups = listOf(BattleCityEnemyGroup("basic", enemies)),
        difficulty = 1
    )

    /** Steps [input] until [kind] turns up, collecting every effect on the way. */
    private fun BattleCityEngine.until(
        kind: BattleCityFxKind,
        input: BattleCityInput,
        maxSteps: Int = 600
    ): Pair<BattleCityFxEvent, List<BattleCityFxEvent>> {
        val seen = mutableListOf<BattleCityFxEvent>()
        repeat(maxSteps) {
            val step = step(BattleCityFixedStepSeconds, BattleCityInputs(input))
            seen += step.fx
            step.fx.firstOrNull { it.kind == kind }?.let { return it to seen }
        }
        error("no $kind within $maxSteps steps; saw ${seen.map { it.kind }.distinct()}")
    }

    @Test
    fun aShotIsPlacedAtTheMuzzleAndAHitBrickWhereTheShellStopped() {
        val engine = BattleCityEngine(
            stageNumber = 1,
            level = level(
                grid = listOf(
                    ".......",
                    ".......",
                    ".......",
                    "...B...",
                    ".......",
                    ".......",
                    "...H..."
                ),
                player = listOf(3, 5),
                base = listOf(3, 6),
                enemySpawns = listOf(listOf(6, 0))
            ),
            seed = 1L
        )
        val fire = BattleCityInput(null, firePressed = true)
        val (hit, seen) = engine.until(BattleCityFxKind.BrickHit, fire)

        val shot = assertNotNull(seen.firstOrNull { it.kind == BattleCityFxKind.Shot }, "the shot itself")
        assertEquals(BattleCityDirection.Up, shot.direction)
        assertTrue(abs(shot.x - 3.5f) < 0.01f, "the muzzle is on the tank's centre line, at x=${shot.x}")
        assertTrue(shot.y < 5.5f, "the muzzle is ahead of the tank's centre, at y=${shot.y}")

        assertTrue(abs(hit.x - 3.5f) < 0.2f, "the hit is on the shell's line, at x=${hit.x}")
        assertTrue(hit.y in 3f..4f, "the hit is in the brick's cell, at y=${hit.y}")
    }

    @Test
    fun destroyingAnEnemyCarriesItsPointsAndItsPlace() {
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
                player = listOf(3, 5),
                base = listOf(3, 6),
                enemySpawns = listOf(listOf(3, 0))
            ),
            seed = 1L
        )
        val (destroyed, _) = engine.until(BattleCityFxKind.EnemyDestroyed, BattleCityInput(null, firePressed = true))
        assertEquals(100, destroyed.points, "a basic enemy is worth 100")
        assertTrue(abs(destroyed.x - 3.5f) < 0.6f, "destroyed in the player's column, at x=${destroyed.x}")
        assertEquals(1, engine.currentState().destroyedEnemies)
    }

    @Test
    fun losingTheBaseIsPlacedOnTheBase() {
        // Nothing but the player and the base: turning down and firing is the only way it goes.
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
                player = listOf(3, 3),
                base = listOf(3, 6),
                enemySpawns = listOf(listOf(0, 0))
            ),
            seed = 1L
        )
        val (lost, _) = engine.until(BattleCityFxKind.BaseDestroyed, BattleCityInput(BattleCityDirection.Down, firePressed = true))
        assertEquals(3.5f, lost.x)
        assertEquals(6.5f, lost.y)
        assertEquals(BattleCityStatus.Lost, engine.currentState().status)
    }

    @Test
    fun effectsDoNotCarryOverFromOneStepToTheNext() {
        val engine = BattleCityEngine(
            stageNumber = 1,
            level = level(
                grid = listOf(".......", ".......", ".......", "...B...", ".......", ".......", "...H..."),
                player = listOf(3, 5),
                base = listOf(3, 6),
                enemySpawns = listOf(listOf(6, 0))
            ),
            seed = 1L
        )
        engine.until(BattleCityFxKind.Shot, BattleCityInput(null, firePressed = true))
        val next = engine.step(BattleCityFixedStepSeconds, BattleCityInputs(BattleCityInput.Idle))
        assertTrue(next.fx.none { it.kind == BattleCityFxKind.Shot }, "the shot was reported again")
    }
}
