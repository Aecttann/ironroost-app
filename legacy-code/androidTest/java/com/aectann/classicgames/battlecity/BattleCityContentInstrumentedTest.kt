package com.aectann.classicgames.battlecity

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the assets actually shipped in the APK: every stage file parses and is playable,
 * and every sprite the renderer asks for exists. A renamed or missing asset fails here
 * instead of on a player's device.
 */
@RunWith(AndroidJUnit4::class)
class BattleCityContentInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyStageParsesAndMatchesItsDeclaredSize() {
        val repository = BattleCityRepository(context)
        for (stage in 1..BattleCityMaxStage) {
            val level = repository.loadLevel(stage)
            assertEquals("stage $stage row count", level.gridSize.rows, level.grid.size)
            level.grid.forEachIndexed { index, row ->
                assertEquals("stage $stage row $index width", level.gridSize.cols, row.length)
            }
            assertTrue("stage $stage has no enemies", level.enemyGroups.sumOf { it.count } > 0)
        }
    }

    @Test
    fun everyStageHasAReachableBaseAndClearSpawns() {
        val repository = BattleCityRepository(context)
        for (stage in 1..BattleCityMaxStage) {
            val level = repository.loadLevel(stage)
            val (baseX, baseY) = level.spawnPoints.base
            assertEquals("stage $stage base tile", 'H', level.grid[baseY][baseX])

            val (playerX, playerY) = level.spawnPoints.player1
            assertEquals("stage $stage player spawn", '.', level.grid[playerY][playerX])

            level.spawnPoints.enemy.forEach { spawn ->
                assertEquals(
                    "stage $stage enemy spawn ${spawn.joinToString()}",
                    '.',
                    level.grid[spawn[1]][spawn[0]]
                )
            }
        }
    }

    @Test
    fun stageDifficultyNeverGoesBackwards() {
        val infos = BattleCityRepository(context).stageInfos()
        var previous = 0
        for (stage in 1..BattleCityMaxStage) {
            val difficulty = infos.getValue(stage).difficulty
            assertTrue(
                "stage $stage difficulty $difficulty dropped below $previous",
                difficulty >= previous
            )
            assertTrue("stage $stage difficulty out of range", difficulty in 1..BattleCityMaxDifficulty)
            previous = difficulty
        }
        assertEquals("last stage should be the hardest tier", BattleCityMaxDifficulty, previous)
    }

    @Test
    fun everyStageGridIsUnique() {
        val repository = BattleCityRepository(context)
        val seen = mutableMapOf<String, Int>()
        for (stage in 1..BattleCityMaxStage) {
            val key = repository.loadLevel(stage).grid.joinToString("")
            val duplicate = seen.put(key, stage)
            assertTrue("stage $stage repeats the map of stage $duplicate", duplicate == null)
        }
        assertEquals(BattleCityMaxStage, seen.size)
    }

    @Test
    fun everyStageRunsWithoutError() {
        val repository = BattleCityRepository(context)
        for (stage in 1..BattleCityMaxStage) {
            val engine = BattleCityEngine(stage, repository.loadLevel(stage), seed = stage.toLong())
            var state = engine.reset()
            repeat(180) {
                state = engine.step(1f / 60f, BattleCityInput(BattleCityDirection.Up, true)).state
            }
            assertNotNull("stage $stage produced no state", state)
            assertEquals("stage $stage board width", 13, state.tiles.cols)
        }
    }

    @Test
    fun everySpriteTheRendererAsksForIsPresent() {
        val assets = BattleCityAssets.load(context)

        assertNotNull("brick sprite", assets.brick())
        assertNotNull("life icon", assets.lifeIcon())
        assertNotNull("stage flag", assets.flagIcon())
        assertNotNull("enemy queue icon", assets.enemyQueueIcon())

        val snapshot = BattleCityTileSnapshot(
            cols = 1,
            rows = 1,
            tiles = charArrayOf('.'),
            brickQuarters = intArrayOf(0),
            version = 0,
            baseDestroyed = false,
            baseFortified = false
        )
        "BSWIFH".forEach { tile ->
            assertNotNull("tile sprite '$tile'", assets.tile(tile, 0, snapshot))
            assertNotNull("tile sprite '$tile' alternate frame", assets.tile(tile, 1, snapshot))
        }

        repeat(4) { frame ->
            assertNotNull("explosion frame $frame", assets.explosion(frame))
            assertNotNull("spawn frame $frame", assets.spawn(frame))
        }
        repeat(2) { frame -> assertNotNull("shield frame $frame", assets.shield(frame)) }

        BattleCityPowerUpType.entries.forEach { type ->
            assertNotNull("power-up ${type.name}", assets.powerUp(type))
        }

        BattleCityDirection.entries.forEach { direction ->
            assertNotNull("bullet ${direction.name}", assets.bullet(direction))

            (1..4).forEach { level ->
                assertNotNull(
                    "player level $level ${direction.name}",
                    assets.tank(tank(direction, "player", isPlayer = true, level = level))
                )
            }
            listOf("basic", "fast", "power", "armor").forEach { type ->
                assertNotNull(
                    "enemy $type ${direction.name}",
                    assets.tank(tank(direction, type, isPlayer = false))
                )
            }
            assertNotNull(
                "damaged armour ${direction.name}",
                assets.tank(tank(direction, "armor", isPlayer = false, damaged = true))
            )
            assertNotNull(
                "flashing bonus tank ${direction.name}",
                assets.tank(tank(direction, "basic", isPlayer = false, bonus = true))
            )
        }
    }

    private fun tank(
        direction: BattleCityDirection,
        type: String,
        isPlayer: Boolean,
        level: Int = 1,
        damaged: Boolean = false,
        bonus: Boolean = false
    ) = BattleCityTankRenderState(
        id = "t",
        type = type,
        x = 0f,
        y = 0f,
        direction = direction,
        isPlayer = isPlayer,
        isBonus = bonus,
        bonusFlashOn = bonus,
        level = level,
        isDamaged = damaged
    )
}
