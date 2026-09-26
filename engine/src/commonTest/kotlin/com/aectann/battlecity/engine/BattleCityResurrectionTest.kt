package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleCityResurrectionTest {
    private fun level(baseInFiringLane: Boolean = false) = BattleCityLevelData(
        id = "resurrection-test",
        note = null,
        gridSize = BattleCityGridSize(7, 7),
        spawnPoints = BattleCitySpawnPoints(
            enemy = listOf(listOf(1, 1)),
            player1 = listOf(1, 5),
            player2 = listOf(5, 5),
            base = if (baseInFiringLane) listOf(1, 3) else listOf(3, 6)
        ),
        tilesLegend = null,
        grid = listOf(
            "SSSSSSS", "S.SSS.S", "S.SSS.S",
            if (baseInFiringLane) "SHSSS.S" else "S.SSS.S",
            "S.SSS.S", "S.SSS.S", "SSSHSSS"
        ),
        enemyGroups = listOf(BattleCityEnemyGroup("basic", 20)),
        difficulty = 1
    )

    @Test
    fun resurrectionPreservesTheBoardAndGivesExactlyOneProtectedLife() {
        val engine = BattleCityEngine(1, level(), seed = 42L, initialLives = listOf(1))
        var state = engine.currentState()
        repeat(18_000) {
            if (state.status == BattleCityStatus.Running) state = engine.step(1f / 60f, BattleCityInputs.Idle).state
        }
        assertEquals(BattleCityStatus.Lost, state.status)
        assertFalse(state.baseDestroyed)
        assertTrue(engine.canResurrect)
        val restored = assertNotNull(engine.resurrect())
        assertEquals(BattleCityStatus.Running, restored.status)
        assertEquals(1, restored.lives)
        assertEquals(state.stageScore, restored.stageScore)
        assertEquals(state.killsByType, restored.killsByType)
        assertEquals(state.tiles, restored.tiles)
        assertEquals(state.enemies, restored.enemies)
        assertEquals(state.playerDeaths, restored.playerDeaths)
        assertNotNull(restored.player)
        assertNull(engine.resurrect())
        val protected = engine.step(0.1f, BattleCityInputs.Idle).state
        assertEquals(1, protected.lives)
    }

    @Test
    fun destroyedBaseCannotBeResurrected() {
        val engine = BattleCityEngine(1, level(baseInFiringLane = true), seed = 42L)
        repeat(120) {
            engine.step(1f / 60f, BattleCityInputs(BattleCityInput(null, true)))
        }
        assertTrue(engine.currentState().baseDestroyed)
        assertFalse(engine.canResurrect)
        assertNull(engine.resurrect())
    }

    @Test
    fun aRunningOrClearedStageCannotGrantAnotherLife() {
        val engine = BattleCityEngine(1, level(), seed = 42L)
        assertNull(engine.resurrect())
        assertEquals(3, engine.currentState().lives)
        val cleared = BattleCityEngine(1, level().copy(enemyGroups = emptyList()), seed = 42L)
        cleared.step(0.1f, BattleCityInputs.Idle)
        assertEquals(BattleCityStatus.Won, cleared.currentState().status)
        assertNull(cleared.resurrect())
    }

    @Test
    fun coopRevivesOnlyPlayerOneAfterBothSeatsHaveNoLives() {
        val engine = BattleCityEngine(1, level(), seed = 42L, initialLives = listOf(1, 0), endless = true)
        var state = engine.currentState()
        repeat(18_000) {
            if (state.status == BattleCityStatus.Running) state = engine.step(1f / 60f, BattleCityInputs.Idle).state
        }
        assertEquals(BattleCityStatus.Lost, state.status)
        val restored = assertNotNull(engine.resurrect())
        assertEquals(listOf(1, 0), restored.players.map { it.lives })
        assertEquals(state.wave, restored.wave)
        assertEquals(state.loadout, restored.loadout)
    }
}
