package com.aectann.battlecity.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A modifier picked between endless waves.
 *
 * The set is deliberately small and mechanical: every entry changes a number the simulation
 * already reads, so what a pick did is visible on the board rather than only in a tooltip.
 *
 * Upgrades belong to the run, not to a tank. Dying costs a life and the star level the
 * power-ups granted, never the build — that asymmetry is what makes a long endless run feel
 * like it accumulates instead of resetting every time a shell gets through.
 */
enum class TanksUpgrade(val id: String, val maxLevel: Int) {
    /** Shorter reload. */
    RapidFire("rapid_fire", 3),

    /** One more of your shells in the air at a time. */
    TwinShot("twin_shot", 3),

    /** Your tank soaks an extra hit per life. */
    Armor("armor", 3),

    /** Your shells deflect off steel and the board edge instead of dying there. */
    Ricochet("ricochet", 3),

    /** Faster tank. */
    Treads("treads", 3),

    /** Your shells break steel, the way a fully starred tank does. */
    Piercing("piercing", 2),

    /** The base walls come back as steel at the start of every wave. */
    Bulwark("bulwark", 3),

    /** One life, right now. */
    Salvage("salvage", 5);

    companion object {
        fun byId(id: String): TanksUpgrade? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Levels taken so far. Stored by [TanksUpgrade.id] rather than by enum name so a save written
 * by an older build survives renaming or reordering the enum.
 */
@Serializable
data class TanksUpgradeLoadout(val levels: Map<String, Int> = emptyMap()) {

    fun levelOf(upgrade: TanksUpgrade): Int =
        (levels[upgrade.id] ?: 0).coerceIn(0, upgrade.maxLevel)

    fun isMaxed(upgrade: TanksUpgrade): Boolean = levelOf(upgrade) >= upgrade.maxLevel

    val isEmpty: Boolean get() = levels.values.none { it > 0 }

    /** Taking a maxed upgrade is a no-op rather than an error; the draft should not offer it. */
    fun plus(upgrade: TanksUpgrade): TanksUpgradeLoadout {
        if (isMaxed(upgrade)) return this
        return TanksUpgradeLoadout(levels + (upgrade.id to levelOf(upgrade) + 1))
    }

    /** What the HUD shows, in a stable order, skipping anything not taken. */
    fun taken(): List<Pair<TanksUpgrade, Int>> = TanksUpgrade.entries
        .mapNotNull { upgrade -> levelOf(upgrade).takeIf { it > 0 }?.let { upgrade to it } }
}

/**
 * Picks what a wave offers.
 *
 * The offer is derived from the run seed and the wave number rather than drawn as the wave
 * ends, so reloading or re-entering the screen cannot reroll it into something better.
 */
object TanksUpgradeDraft {

    const val OfferSize = 3

    /**
     * Up to [OfferSize] distinct upgrades that are not already maxed. Returns fewer near the
     * end of a very long run, and an empty list once nothing is left to take — the caller
     * should skip the pick rather than show an empty card.
     */
    fun offer(
        loadout: TanksUpgradeLoadout,
        wave: Int,
        seed: Long
    ): List<TanksUpgrade> {
        val available = TanksUpgrade.entries.filterNot { loadout.isMaxed(it) }
        if (available.size <= OfferSize) return available
        return available.shuffled(Random(seed xor (wave.toLong() * 0x9E3779B9L))).take(OfferSize)
    }
}

/**
 * How an endless wave is populated.
 *
 * Wave one is deliberately close to a campaign stage's opening so the mode does not need a
 * tutorial, and the mix shifts from basic tanks toward armour as the run goes on rather than
 * simply adding more of everything — pressure should change shape, not just volume.
 */
object TanksEndlessWaves {

    /** The arena is fixed so endless scores are comparable between players. */
    const val ArenaStage = 12

    /** Enemies in the wave. Grows, then flattens, so a long run stays playable. */
    fun enemyCount(wave: Int): Int = (6 + wave.coerceAtLeast(1) * 2).coerceAtMost(28)

    /** How many enemies share the board at once. */
    fun maxActiveEnemies(wave: Int): Int = (4 + (wave.coerceAtLeast(1) - 1) / 4).coerceAtMost(8)

    /**
     * Star rating the wave is worth for enemy fire and decision rates. Passes 5 — the campaign
     * ceiling — around wave 13, which is where an endless run has to start hurting.
     */
    fun pressure(wave: Int): Float = 1f + (wave.coerceAtLeast(1) - 1) * 0.34f

    /** Points for surviving a wave, on top of the tanks killed. */
    fun clearBonus(wave: Int): Int = 250 * wave.coerceAtLeast(1)

    /**
     * Composition for the wave. Weights move toward the tougher types with the wave number;
     * `armor` only appears from wave four so the opening is not a wall.
     */
    fun groups(wave: Int): List<BattleCityEnemyGroup> {
        val total = enemyCount(wave)
        val safeWave = wave.coerceAtLeast(1)

        val fastShare = (0.10f + safeWave * 0.02f).coerceAtMost(0.30f)
        val powerShare = (0.05f + safeWave * 0.02f).coerceAtMost(0.28f)
        val armorShare = if (safeWave < 4) 0f else (safeWave * 0.015f).coerceAtMost(0.22f)

        val fast = (total * fastShare).toInt()
        val power = (total * powerShare).toInt()
        val armor = (total * armorShare).toInt()
        val basic = (total - fast - power - armor).coerceAtLeast(1)

        return buildList {
            add(BattleCityEnemyGroup("basic", basic))
            if (fast > 0) add(BattleCityEnemyGroup("fast", fast))
            if (power > 0) add(BattleCityEnemyGroup("power", power))
            if (armor > 0) add(BattleCityEnemyGroup("armor", armor))
        }
    }
}
