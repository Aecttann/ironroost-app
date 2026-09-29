package com.aectann.battlecity

import com.aectann.battlecity.engine.BattleCityDirection
import com.aectann.battlecity.engine.BattleCityEngine
import com.aectann.battlecity.engine.BattleCityInput
import com.aectann.battlecity.engine.BattleCityInputs
import com.aectann.battlecity.engine.BattleCityLevelParser
import com.aectann.battlecity.engine.BattleCityMaxDifficulty
import com.aectann.battlecity.engine.BattleCityMaxStage
import com.aectann.battlecity.engine.BattleCityStatus
import com.aectann.battlecity.engine.TanksAttractPilot
import com.aectann.battlecity.engine.TanksEndlessWaves
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Checks the files that actually ship: every stage parses and is playable, and every sprite
 * and clip the code asks for is present. Reads them straight off disk so the check is a fast
 * JVM test rather than something that only fails on a device.
 */
class PackedResourcesTest {

    private val resourcesRoot: File by lazy {
        val candidates = listOf(
            "src/commonMain/composeResources",
            "shared/src/commonMain/composeResources",
            "../shared/src/commonMain/composeResources"
        )
        candidates.map { File(it) }.firstOrNull { it.isDirectory }
            ?: error(
                "composeResources not found from ${File(".").absolutePath}; " +
                    "tried ${candidates.joinToString()}"
            )
    }

    private fun resource(path: String) = File(resourcesRoot, path)

    @Test
    fun everyStageParsesAndPassesItsInvariants() {
        for (stage in 1..BattleCityMaxStage) {
            val file = resource(BattleCityLevelParser.stagePath(stage))
            assertTrue(file.isFile, "missing stage file: ${file.path}")

            val level = BattleCityLevelParser.parse(file.readText())
            val (baseX, baseY) = level.spawnPoints.base
            assertEquals('H', level.grid[baseY][baseX], "stage $stage base tile")

            val (playerX, playerY) = level.spawnPoints.player1
            assertEquals('.', level.grid[playerY][playerX], "stage $stage player spawn")

            level.spawnPoints.enemy.forEach { spawn ->
                assertEquals(
                    '.',
                    level.grid[spawn[1]][spawn[0]],
                    "stage $stage enemy spawn ${spawn.joinToString()}"
                )
            }
            assertEquals(20, level.enemyGroups.sumOf { it.count }, "stage $stage wave size")
        }
    }

    @Test
    fun stageDifficultyNeverGoesBackwards() {
        var previous = 0
        for (stage in 1..BattleCityMaxStage) {
            val level = BattleCityLevelParser.parse(resource(BattleCityLevelParser.stagePath(stage)).readText())
            val difficulty = level.difficulty ?: 1
            assertTrue(
                difficulty >= previous,
                "stage $stage difficulty $difficulty dropped below $previous"
            )
            assertTrue(difficulty in 1..BattleCityMaxDifficulty, "stage $stage difficulty out of range")
            previous = difficulty
        }
        assertEquals(BattleCityMaxDifficulty, previous, "last stage should be the hardest tier")
    }

    @Test
    fun everyStageGridIsUnique() {
        val seen = mutableMapOf<String, Int>()
        for (stage in 1..BattleCityMaxStage) {
            val level = BattleCityLevelParser.parse(resource(BattleCityLevelParser.stagePath(stage)).readText())
            val duplicate = seen.put(level.grid.joinToString(""), stage)
            assertTrue(duplicate == null, "stage $stage repeats the map of stage $duplicate")
        }
        assertEquals(BattleCityMaxStage, seen.size)
    }

    @Test
    fun everyStageRunsWithoutError() {
        for (stage in 1..BattleCityMaxStage) {
            val level = BattleCityLevelParser.parse(resource(BattleCityLevelParser.stagePath(stage)).readText())
            val engine = BattleCityEngine(stage, level, seed = stage.toLong())
            var state = engine.reset()
            repeat(180) {
                state = engine.step(
                    1f / 60f,
                    BattleCityInputs(BattleCityInput(BattleCityDirection.Up, true))
                ).state
            }
            assertEquals(13, state.tiles.cols, "stage $stage board width")
            assertEquals(13, state.tiles.rows, "stage $stage board height")
        }
    }

    /**
     * The menu plays a demo battle on its own map behind the buttons. It is not a stage, so none of
     * the per-stage checks reach it: this is the one that would catch it failing to parse, or
     * seating a tank inside a wall, before a player saw an empty menu.
     */
    @Test
    fun theMenuBattlefieldParsesAndPlaysItselfForTwoSeats() {
        val file = resource("files/battle_city/data/levels/menu_demo.json")
        assertTrue(file.isFile, "the menu battlefield is not packed")
        val level = BattleCityLevelParser.parse(file.readText())
        val (baseX, baseY) = level.spawnPoints.base
        assertEquals('H', level.grid[baseY][baseX], "menu battlefield base tile")
        listOfNotNull(level.spawnPoints.player1, level.spawnPoints.player2).forEach { (x, y) ->
            assertEquals('.', level.grid[y][x], "menu battlefield player spawn $x,$y")
        }

        val engine = BattleCityEngine(stageNumber = 0, level = level, seed = 1L, initialLives = listOf(9, 9))
        val pilot = TanksAttractPilot(seed = 1L)
        var state = engine.currentState()
        repeat(60 * 20) {
            state = engine.step(1f / 60f, pilot.inputs(state, 1f / 60f)).state
        }
        assertEquals(2, state.players.size, "both demo seats should be on the board")
        assertTrue(state.bullets.isNotEmpty() || state.destroyedEnemies > 0, "twenty seconds of demo with no shooting")
    }

    /**
     * Endless picks its arena by number, and the engine tests run it on a synthetic corridor, so
     * nothing else checks that the constant points at a real stage that co-op can actually start
     * on. A wrong number would only surface when a player opened the mode.
     */
    @Test
    fun theEndlessArenaIsAPackedStageThatSeatsTwoPlayers() {
        val file = resource(BattleCityLevelParser.stagePath(TanksEndlessWaves.ArenaStage))
        assertTrue(file.isFile, "endless arena stage ${TanksEndlessWaves.ArenaStage} is not packed")

        val level = BattleCityLevelParser.parse(file.readText())
        val secondSpawn = level.spawnPoints.player2
        assertTrue(
            secondSpawn != null && secondSpawn.size == 2,
            "the endless arena has no second spawn, so co-op would mirror player one"
        )

        val engine = BattleCityEngine(
            stageNumber = TanksEndlessWaves.ArenaStage,
            level = level,
            seed = 1L,
            initialLives = listOf(3, 3),
            endless = true
        )
        var state = engine.currentState()
        repeat(600) {
            state = engine.step(1f / 60f, BattleCityInputs.Idle).state
        }
        assertEquals(2, state.players.size, "both seats should be on the board")
        assertNotEquals(
            BattleCityStatus.Won,
            state.status,
            "endless must never report the stage as won"
        )
    }

    /**
     * A floor on how fast stage one can be lost, not a statement about how hard it should be.
     * Actual difficulty needs playtesting; what this catches is the pathological case, where a
     * tuning change lets the wave take the base before a player could plausibly react. It once
     * fell in eight seconds because every tank drilled every wall and sniped the base across the
     * whole board. Stage one is also where a first visit lands, controls still being learned, so
     * its centre lane is capped with steel and brick, fewer of its tanks go for the base
     * (baseSeekerShare), and the floor is sixteen seconds, not eight.
     */
    @Test
    fun stageOneGivesThePlayerTimeToReact() {
        val level = BattleCityLevelParser.parse(resource(BattleCityLevelParser.stagePath(1)).readText())
        val fastest = (1L..8L).minOf { seed ->
            val engine = BattleCityEngine(1, level, seed = seed)
            engine.reset()
            var state = engine.currentState()
            var elapsed = 0f
            while (elapsed < 30f && !state.baseDestroyed) {
                state = engine.step(1f / 60f, BattleCityInputs.Idle).state
                elapsed += 1f / 60f
            }
            elapsed
        }
        assertTrue(
            fastest >= 16f,
            "an idle player lost the base after only %.1fs; the wave is too base-focused".format(fastest)
        )
    }

    /**
     * The other half of the reaction window: the wave must not camp the respawn point. When
     * every tank drifted toward the base, four of them settled next to the player's spawn and
     * emptied three lives in eight seconds.
     */
    @Test
    fun stageOneDoesNotCampThePlayerSpawn() {
        val level = BattleCityLevelParser.parse(resource(BattleCityLevelParser.stagePath(1)).readText())
        val fastestWipe = (1L..8L).minOf { seed ->
            val engine = BattleCityEngine(1, level, seed = seed)
            engine.reset()
            var state = engine.currentState()
            var elapsed = 0f
            while (elapsed < 30f && state.lives > 0 && !state.baseDestroyed) {
                state = engine.step(1f / 60f, BattleCityInputs.Idle).state
                elapsed += 1f / 60f
            }
            if (state.lives > 0) 30f else elapsed
        }
        assertTrue(
            fastestWipe >= 12f,
            "an idle player lost every life in %.1fs; the wave is camping the spawn".format(fastestWipe)
        )
    }

    @Test
    fun everySpriteTheRendererAsksForIsPacked() {
        val missing = TanksAssets.spritePaths()
            .filterValues { !resource(it).isFile }
            .map { (key, path) -> "$key -> $path" }
        assertTrue(missing.isEmpty(), "missing sprites:\n" + missing.joinToString("\n"))
    }

    @Test
    fun everySoundClipIsPacked() {
        val missing = TanksClip.entries.filter { !resource(it.path).isFile }.map { it.name }
        assertTrue(missing.isEmpty(), "missing clips: $missing")
    }

    @Test
    fun everyLocaleDefinesTheSameStringKeys() {
        val keyPattern = Regex("""<string name="([^"]+)"""")
        val locales = resourcesRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.associate { dir -> dir.name to keyPattern.findAll(File(dir, "strings.xml").readText()).map { it.groupValues[1] }.toSet() }
            ?: emptyMap()

        assertTrue(locales.size >= 8, "expected the full locale set, found ${locales.keys}")
        val reference = locales.getValue("values")
        locales.forEach { (locale, keys) ->
            assertEquals(reference, keys, "$locale does not define the same keys as values")
        }
    }
}
