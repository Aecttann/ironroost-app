package com.aectann.classicgames.battlecity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine has no Android dependencies, so the whole simulation can be exercised here.
 * Each test pins one of the behaviours that used to be broken or missing.
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
        gridSize = BattleCityGridSize(cols = grid.first().length, rows = grid.size, unitTilePx = 16),
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
    ) = BattleCityEngine(stageNumber = 1, level = data, seed = seed, initialLives = lives)

    private fun BattleCityEngine.run(
        seconds: Float,
        input: BattleCityInput = BattleCityInput(null, false)
    ): BattleCityRenderState {
        var last = step(0f, input).state
        var elapsed = 0f
        while (elapsed < seconds) {
            last = step(1f / 60f, input).state
            elapsed += 1f / 60f
        }
        return last
    }

    private val idle = BattleCityInput(null, false)

    /** Holds the trigger without driving, so the tank keeps its spawn facing and position. */
    private val fireOnly = BattleCityInput(null, true)
    private val fireUp = BattleCityInput(BattleCityDirection.Up, true)

    // -------------------------------------------------------------------- timing

    @Test
    fun `same seed and inputs produce the same state`() {
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
    fun `a long stall does not teleport tanks`() {
        val subject = engine(level(arena()))
        val settled = subject.run(1.4f)
        val playerBefore = settled.player
        assertNotNull("player never spawned", playerBefore)

        // Five seconds of wall clock arriving as a single frame is clamped, not simulated in full.
        val after = subject.step(5f, BattleCityInput(BattleCityDirection.Left, false)).state
        val moved = kotlin.math.abs(after.player!!.x - playerBefore!!.x)
        assertTrue("player jumped $moved cells in one frame", moved <= 1.2f)
    }

    // -------------------------------------------------------------------- bricks

    @Test
    fun `a brick cell takes two shots and loses half at a time`() {
        val subject = engine(level(arena()))
        val start = subject.step(0f, idle).state
        assertEquals(0b1111, start.tiles.quartersAt(3, 3))

        // One shot only: the fire cooldown is 0.34 s, so a shorter burst cannot fire twice.
        subject.run(0.25f, fireOnly)
        val afterFirst = subject.step(0f, idle).state
        val remaining = afterFirst.tiles.quartersAt(3, 3)
        assertNotEquals("first shot did nothing", 0b1111, remaining)
        assertNotEquals("first shot cleared the whole cell", 0, remaining)
        assertEquals('B', afterFirst.tiles.tileAt(3, 3))

        subject.run(0.5f, fireOnly)
        val afterSecond = subject.step(0f, idle).state
        assertEquals(0, afterSecond.tiles.quartersAt(3, 3))
        assertEquals('.', afterSecond.tiles.tileAt(3, 3))
    }

    @Test
    fun `steel survives a level one shell`() {
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
    fun `reset restores bricks destroyed in the previous run`() {
        val subject = engine(level(arena()))
        subject.run(1.2f, fireUp)
        assertEquals('.', subject.step(0f, idle).state.tiles.tileAt(3, 3))

        val restored = subject.reset()
        assertEquals('B', restored.tiles.tileAt(3, 3))
        assertEquals(0b1111, restored.tiles.quartersAt(3, 3))
    }

    // --------------------------------------------------------------- stage status

    @Test
    fun `shooting the base ends the run`() {
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
    fun `a stage with no enemies is won immediately`() {
        val subject = engine(level(arena(), enemies = emptyList()))
        val state = subject.run(0.1f)
        assertEquals(BattleCityStatus.Won, state.status)
    }

    @Test
    fun `the tile snapshot only changes when the map does`() {
        val subject = engine(level(arena()))
        val first = subject.step(0f, idle).state.tiles
        val second = subject.run(0.4f).tiles
        assertEquals("snapshot was rebuilt without a map change", first.version, second.version)

        subject.run(1.2f, fireUp)
        assertNotEquals(first.version, subject.step(0f, idle).state.tiles.version)
    }

    // -------------------------------------------------------------------- layout

    @Test
    fun `board size comes from the level, not a constant`() {
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
    fun `a tank cannot be driven off the board`() {
        val subject = engine(level(arena()))
        val state = subject.run(3f, BattleCityInput(BattleCityDirection.Left, false))
        val player = state.player
        assertNotNull(player)
        assertTrue("player left the board at x=${player!!.x}", player.x >= 0f)
    }

    // --------------------------------------------------------------- enemy waves

    @Test
    fun `enemy groups are spread across the wave instead of queued in blocks`() {
        val queue = interleaveEnemyGroups(
            listOf(
                BattleCityEnemyGroup("basic", 4),
                BattleCityEnemyGroup("armor", 4)
            )
        )
        assertEquals(8, queue.size)

        // With even groups the types must alternate rather than arrive as two blocks.
        val firstHalf = queue.take(4)
        assertTrue("first half is a single block: $queue", firstHalf.toSet().size > 1)
    }

    @Test
    fun `empty groups are ignored`() {
        val queue = interleaveEnemyGroups(
            listOf(
                BattleCityEnemyGroup("basic", 0),
                BattleCityEnemyGroup("fast", 2)
            )
        )
        assertEquals(listOf("fast", "fast"), queue)
    }

    @Test
    fun `enemies do not enter the field before the spawn animation finishes`() {
        val subject = engine(level(arena(), enemies = listOf(BattleCityEnemyGroup("basic", 3))))
        val atStart = subject.step(0f, idle).state
        assertEquals("tanks appeared before their spawn effect", 0, atStart.enemies.size)
        assertTrue(atStart.effects.any { it.kind == BattleCityEffectKind.Spawn })

        val later = subject.run(1.4f)
        assertTrue("no enemy ever entered the field", later.enemies.isNotEmpty())
    }

    @Test
    fun `total enemy count stays stable while the wave drains`() {
        val subject = engine(level(arena(), enemies = listOf(BattleCityEnemyGroup("basic", 5))))
        val first = subject.run(0.2f)
        val later = subject.run(2f)
        assertEquals(first.totalEnemies, later.totalEnemies)
    }
}
