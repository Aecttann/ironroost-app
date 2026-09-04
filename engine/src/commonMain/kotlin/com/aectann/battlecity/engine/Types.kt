package com.aectann.battlecity.engine

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
) {
    companion object {
        val Idle = BattleCityInput(null, false)
    }
}

/**
 * What every player is doing this frame. Co-op is two tanks on one keyboard, so this is a fixed
 * pair rather than a list — nothing in the game opens a third slot, and a list would invite
 * callers to pass the wrong length.
 */
data class BattleCityInputs(
    val first: BattleCityInput = BattleCityInput.Idle,
    val second: BattleCityInput = BattleCityInput.Idle
) {
    /** Index 0 is player one. Anything else is player two, so a one-player run ignores it. */
    operator fun get(index: Int): BattleCityInput = if (index == 0) first else second

    companion object {
        val Idle = BattleCityInputs()
    }
}
