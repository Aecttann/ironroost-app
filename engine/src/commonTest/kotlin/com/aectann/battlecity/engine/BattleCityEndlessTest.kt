package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The endless loop itself, driven through the real simulation.
 *
 * [TanksUpgradesTest] covers the pure draft and wave-curve rules; nothing there touches the
 * engine, so none of it would notice a wave that fails to roll over or an upgrade that does not
 * reach the board. These tests clear an actual wave to get at that.
 */
class BattleCityEndlessTest {

    // ------------------------------------------------------------------ fixtures

    /**
     * A one-tank-wide shooting gallery. Enemies can only come down the middle column and the
     * player only has to hold the trigger, which makes clearing a wave deterministic instead of
     * a question of how the wander AI rolled.
     *
     * The eagle keeps a brick wall, as every shipped map does. That detail matters: the wave
     * repair pass keys off what the original map had as brick, so a fixture that walled the base
     * in steel would quietly stop testing the interaction between repair and Bulwark.
     */
    private fun corridor() = listOf(
        "S.S",
        "S.S",
        "S.S",
        "S.S",
        "S.S",
        "SBS",
        "BHB"
    )

    private fun corridorLevel() = BattleCityLevelData(
        id = "endless-test",
        note = null,
        gridSize = BattleCityGridSize(cols = 3, rows = 7),
        spawnPoints = BattleCitySpawnPoints(
            enemy = listOf(listOf(1, 0)),
            player1 = listOf(1, 4),
            player2 = null,
            base = listOf(1, 6)
        ),
        tilesLegend = null,
        // Ignored in endless — the engine generates its own wave — but the parser contract
        // still wants a non-empty group.
        grid = corridor(),
        enemyGroups = listOf(BattleCityEnemyGroup("basic", 1)),
        difficulty = 1
    )

    private fun endlessEngine(seed: Long = 7L, lives: Int = 9) = BattleCityEngine(
        stageNumber = TanksEndlessWaves.ArenaStage,
        level = corridorLevel(),
        seed = seed,
        initialLives = listOf(lives),
        endless = true
    )

    /** Holds the trigger without steering, so the tank keeps its spawn facing up the corridor. */
    private val fireOnly = BattleCityInputs(BattleCityInput(null, true))

    /**
     * Runs until the wave gate goes up, or gives up after [limitSeconds]. Returns null when the
     * run ended instead, so a caller can say which of the two happened.
     */
    private fun BattleCityEngine.runUntilWaveCleared(
        limitSeconds: Float = 240f
    ): BattleCityRenderState? {
        var elapsed = 0f
        var state = step(0f, fireOnly).state
        while (elapsed < limitSeconds) {
            state = step(1f / 60f, fireOnly).state
            elapsed += 1f / 60f
            if (state.waveCleared) return state
            if (state.status != BattleCityStatus.Running) return null
        }
        return null
    }

    // --------------------------------------------------------------------- gating

    @Test
    fun anEndlessRunNeverReportsTheStageAsWon() {
        val engine = endlessEngine()
        var state = engine.step(0f, fireOnly).state
        var elapsed = 0f
        while (elapsed < 120f) {
            state = engine.step(1f / 60f, fireOnly).state
            elapsed += 1f / 60f
            assertNotEquals(
                BattleCityStatus.Won,
                state.status,
                "endless reported Won; the campaign's end-of-stage path would fire"
            )
            if (state.status != BattleCityStatus.Running) break
        }
    }

    @Test
    fun aClearedWaveRaisesTheGateAndHoldsTheBoardStill() {
        val engine = endlessEngine()
        val cleared = engine.runUntilWaveCleared()
            ?: error("wave one was never cleared in the corridor; the fixture cannot test rollover")

        assertTrue(cleared.waveCleared, "the gate should be up")
        assertEquals(1, cleared.wave, "still on wave one until the next is asked for")
        assertEquals(BattleCityStatus.Running, cleared.status)

        // The board must not advance while the pick is on screen.
        val before = engine.currentState()
        val after = engine.step(1f / 60f, fireOnly).state
        assertEquals(before.tiles.version, after.tiles.version, "the map moved during the pick")
        assertEquals(before.bullets.size, after.bullets.size, "bullets flew during the pick")
    }

    @Test
    fun theNextWaveRefillsTheQueueAndClearsTheGate() {
        val engine = endlessEngine()
        engine.runUntilWaveCleared() ?: error("wave one was never cleared")

        val next = engine.beginNextWave(null)

        assertEquals(2, next.wave)
        assertFalse(next.waveCleared, "the gate should drop once the wave is sent in")
        assertEquals(BattleCityStatus.Running, next.status)
        assertTrue(
            next.enemiesPending >= TanksEndlessWaves.enemyCount(2),
            "wave two should be queued; pending was ${next.enemiesPending}"
        )
    }

    // ------------------------------------------------------------------- upgrades

    @Test
    fun anUpgradeTakenBetweenWavesIsRecordedOnTheRun() {
        val engine = endlessEngine()
        engine.runUntilWaveCleared() ?: error("wave one was never cleared")

        val next = engine.beginNextWave(TanksUpgrade.RapidFire)

        assertEquals(1, next.loadout.levelOf(TanksUpgrade.RapidFire))
        assertTrue(next.loadout.taken().isNotEmpty(), "the HUD needs something to show")
    }

    @Test
    fun salvagePaysItsLifeOutImmediately() {
        val engine = endlessEngine(lives = 3)
        val cleared = engine.runUntilWaveCleared() ?: error("wave one was never cleared")
        val before = cleared.lives

        val next = engine.beginNextWave(TanksUpgrade.Salvage)

        assertEquals(before + 1, next.lives, "Salvage is the one pick that pays out at once")
    }

    /**
     * Bulwark turns the eagle's wall to steel at every rollover. The wave's brick repair pass
     * runs in the same call, and it restores anything the original map had as brick — which is
     * exactly what those cells are. Without an explicit carve-out the repair immediately downgrades
     * the steel the player just paid for.
     */
    @Test
    fun bulwarkLeavesTheBaseWallsAsSteelRatherThanRepairingThemBackToBrick() {
        val engine = endlessEngine()
        engine.runUntilWaveCleared() ?: error("wave one was never cleared")

        val next = engine.beginNextWave(TanksUpgrade.Bulwark)

        // The fortress is the ring of cells around the base, minus the base itself.
        val base = corridorLevel().spawnPoints.base
        val walls = buildList {
            for (dy in -1..0) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val x = base[0] + dx
                    val y = base[1] + dy
                    if (x in 0 until next.tiles.cols && y in 0 until next.tiles.rows) add(x to y)
                }
            }
        }

        val brick = walls.filter { (x, y) -> next.tiles.tileAt(x, y) == 'B' }
        assertTrue(
            brick.isEmpty(),
            "Bulwark was taken but $brick came back as brick; the repair pass undid the upgrade"
        )
    }
}
