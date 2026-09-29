package com.aectann.battlecity.engine

/**
 * Identifier the engine gives a player's tank. The renderer keys the tank's colour off it, so it
 * is part of the contract rather than a debugging label.
 */
fun battleCityPlayerId(index: Int): String = "player_${index + 1}"

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

/**
 * One player's tank plus the counters the HUD shows beside it. The slot outlives the tank: a
 * player waiting to respawn has no [tank] but is still [active], and the run only ends when no
 * slot is.
 */
data class BattleCityPlayerRenderState(
    val index: Int,
    val tank: BattleCityTankRenderState?,
    val lives: Int,
    val level: Int,
    /** Points this player scored on this stage. The stage total is the sum across players. */
    val score: Int,
    val active: Boolean,
    /** True while a teammate's shell has this player stopped. */
    val stunned: Boolean = false
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
 * Immutable snapshot of the destructible layer. The instance only changes when the map itself
 * changes, so a renderer can cache work keyed on [version].
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
    /** One entry per player in the run: one normally, two in local co-op. */
    val players: List<BattleCityPlayerRenderState>,
    val enemies: List<BattleCityTankRenderState>,
    val bullets: List<BattleCityBulletRenderState>,
    val powerUps: List<BattleCityPowerUpRenderState>,
    val effects: List<BattleCityEffectRenderState>,
    /** Points scored on this stage only, across every player. */
    val stageScore: Int,
    val destroyedEnemies: Int,
    val totalEnemies: Int,
    /** Enemies still to be dealt with, for the queue indicator. */
    val enemiesPending: Int,
    val enemiesFrozen: Boolean,
    val status: BattleCityStatus,
    val baseDestroyed: Boolean,
    /** Kills of this stage broken down by enemy type, for the stage summary. */
    val killsByType: Map<String, Int>,
    /** Power-ups picked up on this stage, keyed the same way as the collection cards. */
    val powerUpsCollected: Map<String, Int>,
    /** Tanks lost during this stage. Extra lives must not hide a deathless-clear failure. */
    val playerDeaths: Int = 0,
    /** Endless wave being fought, counting from one. Always 1 on a campaign stage. */
    val wave: Int = 1,
    /**
     * True while an endless wave has been beaten and the next one is waiting on the upgrade
     * pick. The board is held still until the caller calls `beginNextWave`.
     */
    val waveCleared: Boolean = false,
    /** Upgrades the endless run has banked. Empty in the campaign. */
    val loadout: TanksUpgradeLoadout = TanksUpgradeLoadout()
) {
    /** Player one's tank — what a single-player screen, and every board test, actually means. */
    val player: BattleCityTankRenderState? get() = players.firstOrNull()?.tank

    /** Lives left in the run, counting every player. */
    val lives: Int get() = players.sumOf { it.lives }

    /** The strongest tank on the board, which is what the meta records as a personal best. */
    val playerLevel: Int get() = players.maxOfOrNull { it.level } ?: 1
}

data class BattleCityStep(
    val state: BattleCityRenderState,
    val events: List<BattleCitySoundEvent>,
    /** The same moments with a place on the board, for the screen to dress up. */
    val fx: List<BattleCityFxEvent> = emptyList()
)

/** What happened, for the screen's effects. The sounds say that something did; this says where. */
enum class BattleCityFxKind {
    /** A shell leaves a barrel: at the muzzle, flying [BattleCityFxEvent.direction]. */
    Shot,

    /** A shell chips a brick wall. */
    BrickHit,

    /** A shell stops on steel, the board's edge, another shell or a shield. */
    SteelHit,

    /** An armoured enemy took a hit and is still standing. */
    ArmorHit,

    /** An enemy destroyed, for [BattleCityFxEvent.points]. */
    EnemyDestroyed,

    /** A player's tank destroyed. */
    PlayerDestroyed,

    /** The base destroyed: the run is lost. */
    BaseDestroyed,

    /** A power-up picked up, for [BattleCityFxEvent.points]. */
    PowerUpTaken
}

/** One moment for the screen's effects, at [x], [y] in board cells (fractional, not top-left). */
data class BattleCityFxEvent(
    val kind: BattleCityFxKind,
    val x: Float,
    val y: Float,
    val points: Int = 0,
    val direction: BattleCityDirection? = null
)
