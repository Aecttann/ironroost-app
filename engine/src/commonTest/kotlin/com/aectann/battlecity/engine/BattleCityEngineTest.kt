package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * The engine is pure Kotlin, so the whole simulation runs here on every target.
 * Each test pins one behaviour that used to be broken or missing.
 */
class BattleCityEngineTest {

    // ------------------------------------------------------------------ fixtures

    private fun level(
        grid: List<String>,
        enemies: List<BattleCityEnemyGroup> = listOf(BattleCityEnemyGroup("basic", 1)),
        player: List<Int> = listOf(3, 5),
        base: List<Int> = listOf(3, 6),
        enemySpawns: List<List<Int>> = listOf(listOf(0, 0))
    ) = BattleCityLevelData(
        id = "test",
        note = null,
        gridSize = BattleCityGridSize(cols = grid.first().length, rows = grid.size),
        spawnPoints = BattleCitySpawnPoints(
            enemy = enemySpawns,
            player1 = player,
            player2 = null,
            base = base
        ),
        tilesLegend = null,
        grid = grid,
        enemyGroups = enemies,
        difficulty = 1
    )

    /** A quiet 7x7 arena: brick straight above the player, base below it. */
    private fun arena() = listOf(
        ".......",
        ".......",
        ".......",
        "...B...",
        ".......",
        ".......",
        "...H..."
    )

    private fun engine(
        data: BattleCityLevelData,
        seed: Long = 42L,
        lives: Int = 3
    ) = BattleCityEngine(stageNumber = 1, level = data, seed = seed, initialLives = listOf(lives))

    /** Single-player shorthand: these tests drive player one and leave the co-op slot idle. */
    private fun BattleCityEngine.step(elapsedSeconds: Float, input: BattleCityInput) =
        step(elapsedSeconds, BattleCityInputs(input))

    private fun BattleCityEngine.run(
        seconds: Float,
        input: BattleCityInput = BattleCityInput.Idle
    ): BattleCityRenderState {
        var last = step(0f, input).state
        var elapsed = 0f
        while (elapsed < seconds) {
            last = step(1f / 60f, input).state
            elapsed += 1f / 60f
        }
        return last
    }

    private val idle = BattleCityInput.Idle

    /** Holds the trigger without driving, so the tank keeps its spawn facing and position. */
    private val fireOnly = BattleCityInput(null, true)
    private val fireUp = BattleCityInput(BattleCityDirection.Up, true)

    // -------------------------------------------------------------------- timing

    @Test
    fun sameSeedAndInputsProduceTheSameState() {
        val first = engine(level(arena()))
        val second = engine(level(arena()))

        // Deliberately uneven frame times: the fixed step must absorb them identically.
        val deltas = listOf(0.016f, 0.008f, 0.033f, 0.011f, 0.021f)
        repeat(20) { round ->
            val delta = deltas[round % deltas.size]
            first.step(delta, idle)
            second.step(delta, idle)
        }

        val a = first.step(0f, idle).state
        val b = second.step(0f, idle).state
        assertEquals(a.player?.x, b.player?.x)
        assertEquals(a.player?.y, b.player?.y)
        assertEquals(a.enemies.size, b.enemies.size)
        assertEquals(a.stageScore, b.stageScore)
    }

    @Test
    fun aLongStallDoesNotTeleportTanks() {
        val subject = engine(level(arena()))
        val settled = subject.run(1.4f)
        val playerBefore = settled.player
        assertNotNull(playerBefore, "player never spawned")

        // Five seconds of wall clock arriving as a single frame is clamped, not simulated in full.
        val after = subject.step(5f, BattleCityInput(BattleCityDirection.Left, false)).state
        val moved = abs(after.player!!.x - playerBefore.x)
        assertTrue(moved <= 1.2f, "player jumped $moved cells in one frame")
    }

    // -------------------------------------------------------------------- bricks

    @Test
    fun aBrickCellTakesTwoShotsAndLosesHalfAtATime() {
        val subject = engine(level(arena()))
        val start = subject.step(0f, idle).state
        assertEquals(0b1111, start.tiles.quartersAt(3, 3))

        // One shot only: the fire cooldown is 0.34 s, so a shorter burst cannot fire twice.
        subject.run(0.25f, fireOnly)
        val afterFirst = subject.step(0f, idle).state
        val remaining = afterFirst.tiles.quartersAt(3, 3)
        assertNotEquals(0b1111, remaining, "first shot did nothing")
        assertNotEquals(0, remaining, "first shot cleared the whole cell")
        assertEquals('B', afterFirst.tiles.tileAt(3, 3))

        subject.run(0.5f, fireOnly)
        val afterSecond = subject.step(0f, idle).state
        assertEquals(0, afterSecond.tiles.quartersAt(3, 3))
        assertEquals('.', afterSecond.tiles.tileAt(3, 3))
    }

    @Test
    fun steelSurvivesALevelOneShell() {
        val grid = listOf(
            ".......",
            ".......",
            ".......",
            "...S...",
            ".......",
            ".......",
            "...H..."
        )
        val subject = engine(level(grid))
        subject.run(1.5f, fireUp)
        assertEquals('S', subject.step(0f, idle).state.tiles.tileAt(3, 3))
    }

    @Test
    fun resetRestoresBricksDestroyedInThePreviousRun() {
        val subject = engine(level(arena()))
        subject.run(1.2f, fireUp)
        assertEquals('.', subject.step(0f, idle).state.tiles.tileAt(3, 3))

        val restored = subject.reset()
        assertEquals('B', restored.tiles.tileAt(3, 3))
        assertEquals(0b1111, restored.tiles.quartersAt(3, 3))
    }

    // --------------------------------------------------------------- stage status

    @Test
    fun shootingTheBaseEndsTheRun() {
        val grid = listOf(
            ".......",
            ".......",
            ".......",
            ".......",
            "...H...",
            ".......",
            "......."
        )
        val subject = engine(level(grid, player = listOf(3, 6), base = listOf(3, 4)))
        subject.run(1.5f, fireUp)

        val state = subject.step(0f, idle).state
        assertEquals(BattleCityStatus.Lost, state.status)
        assertTrue(state.baseDestroyed)
    }

    @Test
    fun aStageWithNoEnemiesIsWonImmediately() {
        val subject = engine(level(arena(), enemies = listOf(BattleCityEnemyGroup("basic", 0))))
        val state = subject.run(0.1f)
        assertEquals(BattleCityStatus.Won, state.status)
    }

    @Test
    fun theTileSnapshotOnlyChangesWhenTheMapDoes() {
        val subject = engine(level(arena()))
        val first = subject.step(0f, idle).state.tiles
        val second = subject.run(0.4f).tiles
        assertEquals(first.version, second.version, "snapshot was rebuilt without a map change")

        subject.run(1.2f, fireUp)
        assertNotEquals(first.version, subject.step(0f, idle).state.tiles.version)
    }

    // -------------------------------------------------------------------- layout

    @Test
    fun boardSizeComesFromTheLevelNotAConstant() {
        val grid = MutableList(9) { ".".repeat(9) }
            .also { it[8] = "....H...." }
        val subject = engine(
            level(grid, player = listOf(2, 8), base = listOf(4, 8), enemySpawns = listOf(listOf(0, 0)))
        )
        val state = subject.run(0.2f)
        assertEquals(9, state.tiles.cols)
        assertEquals(9, state.tiles.rows)
        assertEquals('H', state.tiles.tileAt(4, 8))
    }

    @Test
    fun aTankCannotBeDrivenOffTheBoard() {
        val subject = engine(level(arena()))
        val state = subject.run(3f, BattleCityInput(BattleCityDirection.Left, false))
        val player = state.player
        assertNotNull(player)
        assertTrue(player.x >= 0f, "player left the board at x=${player.x}")
    }

    @Test
    fun localCoopCreatesTwoIndependentPlayerSlots() {
        val subject = BattleCityEngine(
            stageNumber = 1,
            level = level(arena()),
            seed = 42L,
            initialLives = listOf(3, 3)
        )
        val before = subject.currentState()

        assertEquals(2, before.players.size)
        assertEquals(listOf("player_1", "player_2"), before.players.mapNotNull { it.tank?.id })
        assertNotEquals(before.players[0].tank?.x, before.players[1].tank?.x)

        val after = subject.step(
            0.2f,
            BattleCityInputs(
                first = BattleCityInput(BattleCityDirection.Right, false),
                second = BattleCityInput(BattleCityDirection.Left, false)
            )
        ).state
        assertTrue(after.players[0].tank!!.x > before.players[0].tank!!.x)
        assertTrue(after.players[1].tank!!.x < before.players[1].tank!!.x)
    }

    // --------------------------------------------------------------- enemy waves

    @Test
    fun enemyGroupsAreSpreadAcrossTheWaveInsteadOfQueuedInBlocks() {
        val queue = interleaveEnemyGroups(
            listOf(
                BattleCityEnemyGroup("basic", 4),
                BattleCityEnemyGroup("armor", 4)
            )
        )
        assertEquals(8, queue.size)

        // With even groups the types must alternate rather than arrive as two blocks.
        val firstHalf = queue.take(4)
        assertTrue(firstHalf.toSet().size > 1, "first half is a single block: $queue")
    }

    @Test
    fun emptyGroupsAreIgnored() {
        val queue = interleaveEnemyGroups(
            listOf(
                BattleCityEnemyGroup("basic", 0),
                BattleCityEnemyGroup("fast", 2)
            )
        )
        assertEquals(listOf("fast", "fast"), queue)
    }

    @Test
    fun enemiesDoNotEnterTheFieldBeforeTheSpawnAnimationFinishes() {
        val subject = engine(level(arena(), enemies = listOf(BattleCityEnemyGroup("basic", 3))))
        val atStart = subject.step(0f, idle).state
        assertEquals(0, atStart.enemies.size, "tanks appeared before their spawn effect")
        assertTrue(atStart.effects.any { it.kind == BattleCityEffectKind.Spawn })

        val later = subject.run(1.4f)
        assertTrue(later.enemies.isNotEmpty(), "no enemy ever entered the field")
    }

    @Test
    fun totalEnemyCountStaysStableWhileTheWaveDrains() {
        val subject = engine(level(arena(), enemies = listOf(BattleCityEnemyGroup("basic", 5))))
        val first = subject.run(0.2f)
        val later = subject.run(2f)
        assertEquals(first.totalEnemies, later.totalEnemies)
    }

    // -------------------------------------------------------------------- deaths

    /**
     * Enemy fire comes straight down column 1 onto the player, who faces right down an open row
     * with the trigger held, so a shell of the player's own is in the air when the enemy's lands.
     * Steel everywhere else keeps the two lines of fire out of each other's way.
     */
    private fun crossfire() = listOf(
        "S.SSSSSSS",
        "S.SSSSSSS",
        "S.SSSSSSS",
        "S.SSSSSSS",
        "S........",
        "SSSSSSSSS",
        "SSSSHSSSS"
    )

    @Test
    fun dyingWithAShellInTheAirDoesNotBreakTheBulletPass() {
        // A death clears the dead tank's shells out of the list the bullet pass is walking. The
        // pass used to trip over that removal and throw, which on the web build ended the frame
        // loop and froze the game on the spot.
        val subject = engine(
            level(
                crossfire(),
                enemies = listOf(BattleCityEnemyGroup("basic", 4)),
                player = listOf(1, 4),
                base = listOf(4, 6),
                enemySpawns = listOf(listOf(1, 0))
            )
        )
        subject.step(1f / 60f, BattleCityInput(BattleCityDirection.Right, false))

        var previous = subject.step(0f, fireOnly).state
        var state = previous
        var elapsed = 0f
        while (state.playerDeaths == 0 && elapsed < 30f) {
            previous = state
            state = subject.step(1f / 60f, fireOnly).state
            elapsed += 1f / 60f
        }

        assertEquals(1, state.playerDeaths, "the enemy never landed a shot")
        assertTrue(
            previous.bullets.any { it.isPlayerBullet },
            "the player had no shell in the air when it died, so this did not test the removal"
        )
    }
}
