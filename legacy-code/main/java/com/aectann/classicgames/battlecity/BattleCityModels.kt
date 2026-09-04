package com.aectann.classicgames.battlecity

import android.content.Context
import com.google.gson.Gson

private const val BattleCityAssetRoot = "battle_city"

/** Number of stages shipped in assets/battle_city/data/levels/prototype_13x13. */
const val BattleCityMaxStage = 35

/** Highest star rating a stage can carry. */
const val BattleCityMaxDifficulty = 5

data class BattleCityLevelData(
    val id: String,
    val note: String?,
    val gridSize: BattleCityGridSize,
    val spawnPoints: BattleCitySpawnPoints,
    val tilesLegend: Map<String, String>?,
    val grid: List<String>,
    val enemyGroups: List<BattleCityEnemyGroup>,
    val difficulty: Int?
)

data class BattleCityGridSize(
    val cols: Int,
    val rows: Int,
    val unitTilePx: Int
)

data class BattleCitySpawnPoints(
    val enemy: List<List<Int>>,
    val player1: List<Int>,
    val player2: List<Int>?,
    val base: List<Int>
)

data class BattleCityEnemyGroup(
    val enemy: String,
    val count: Int
)

enum class BattleCityDirection(
    val dx: Int,
    val dy: Int,
    val assetSuffix: String
) {
    Up(0, -1, "up"),
    Right(1, 0, "right"),
    Down(0, 1, "down"),
    Left(-1, 0, "left");

    fun opposite(): BattleCityDirection = when (this) {
        Up -> Down
        Down -> Up
        Left -> Right
        Right -> Left
    }
}

enum class BattleCityStatus {
    Running,
    Won,
    Lost
}

enum class BattleCitySoundEvent {
    Shoot,
    ExplosionBig,
    ExplosionSmall,
    HitBrick,
    HitSteel,
    PowerUp,
    ExtraLife,
    GameOver,
    StageStart
}

/** Bonuses dropped by the flashing enemy tanks. */
enum class BattleCityPowerUpType(val assetKey: String) {
    Star("star"),
    Gun("gun"),
    Helmet("helmet"),
    Timer("timer"),
    Shovel("shovel"),
    Grenade("grenade"),
    Life("tank_life"),
    Boat("boat")
}

enum class BattleCityEffectKind {
    Explosion,
    Spawn
}

data class BattleCityInput(
    val direction: BattleCityDirection?,
    val firePressed: Boolean
)

data class BattleCityTankRenderState(
    val id: String,
    val type: String,
    val x: Float,
    val y: Float,
    val direction: BattleCityDirection,
    val isPlayer: Boolean,
    val isBonus: Boolean,
    /** Bonus tanks alternate between their own sprite and the flashing one. */
    val bonusFlashOn: Boolean = false,
    /** 1..4 for the player, ignored for enemies. */
    val level: Int = 1,
    /** Armour tanks switch to the grey sprite once they are past half health. */
    val isDamaged: Boolean = false,
    val hasShield: Boolean = false,
    val shieldFrame: Int = 0
)

data class BattleCityBulletRenderState(
    val x: Float,
    val y: Float,
    val direction: BattleCityDirection,
    val isPlayerBullet: Boolean
)

data class BattleCityPowerUpRenderState(
    val x: Float,
    val y: Float,
    val type: BattleCityPowerUpType,
    /** Power-ups blink for the last seconds of their life. */
    val visible: Boolean
)

data class BattleCityEffectRenderState(
    val x: Float,
    val y: Float,
    val kind: BattleCityEffectKind,
    val frame: Int,
    val sizeCells: Float
)

/**
 * Immutable snapshot of the destructible layer. The instance only changes when the
 * map itself changes, so the renderer can cache an offscreen layer keyed on [version].
 */
class BattleCityTileSnapshot(
    val cols: Int,
    val rows: Int,
    /** Row-major tile characters. */
    val tiles: CharArray,
    /** Row-major brick quarter masks: bit 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
    val brickQuarters: IntArray,
    val version: Int,
    val baseDestroyed: Boolean,
    /** True while a shovel power-up has turned the base walls into steel. */
    val baseFortified: Boolean
) {
    fun tileAt(x: Int, y: Int): Char =
        if (x in 0 until cols && y in 0 until rows) tiles[y * cols + x] else 'S'

    fun quartersAt(x: Int, y: Int): Int =
        if (x in 0 until cols && y in 0 until rows) brickQuarters[y * cols + x] else 0
}

data class BattleCityRenderState(
    val stageNumber: Int,
    val difficulty: Int,
    val tiles: BattleCityTileSnapshot,
    val player: BattleCityTankRenderState?,
    val enemies: List<BattleCityTankRenderState>,
    val bullets: List<BattleCityBulletRenderState>,
    val powerUps: List<BattleCityPowerUpRenderState>,
    val effects: List<BattleCityEffectRenderState>,
    /** Points scored on this stage only. */
    val stageScore: Int,
    val lives: Int,
    val playerLevel: Int,
    val destroyedEnemies: Int,
    val totalEnemies: Int,
    /** Enemies still waiting to enter the field, for the queue indicator. */
    val enemiesPending: Int,
    val enemiesFrozen: Boolean,
    val status: BattleCityStatus,
    val baseDestroyed: Boolean,
    /** Kills of this stage broken down by enemy type, for the stage summary. */
    val killsByType: Map<String, Int>
)

data class BattleCityStep(
    val state: BattleCityRenderState,
    val events: List<BattleCitySoundEvent>
)

class BattleCityRepository(private val context: Context) {
    private val gson = Gson()

    fun loadLevel(stageNumber: Int): BattleCityLevelData {
        val normalizedStage = stageNumber.coerceIn(1, BattleCityMaxStage)
        val fileName = "prototype_stage_${normalizedStage.toString().padStart(2, '0')}.json"
        val path = "$BattleCityAssetRoot/data/levels/prototype_13x13/$fileName"
        val json = context.assets.open(path).bufferedReader().use { it.readText() }
        val level = gson.fromJson(json, BattleCityLevelData::class.java)
        require(level.grid.size == level.gridSize.rows) {
            "${level.id}: expected ${level.gridSize.rows} rows, got ${level.grid.size}"
        }
        level.grid.forEachIndexed { index, row ->
            require(row.length == level.gridSize.cols) {
                "${level.id}: row $index has ${row.length} cells, expected ${level.gridSize.cols}"
            }
        }
        return level
    }

    /**
     * Star rating and map of every stage, read from the level files so the stage picker
     * never duplicates the difficulty curve or ships a thumbnail that can go stale.
     */
    fun stageInfos(): Map<Int, BattleCityStageInfo> = (1..BattleCityMaxStage).associateWith { stage ->
        runCatching {
            val level = loadLevel(stage)
            BattleCityStageInfo(
                difficulty = (level.difficulty ?: 1).coerceIn(1, BattleCityMaxDifficulty),
                grid = level.grid
            )
        }.getOrDefault(BattleCityStageInfo(1, emptyList()))
    }
}

data class BattleCityStageInfo(
    val difficulty: Int,
    val grid: List<String>
)
