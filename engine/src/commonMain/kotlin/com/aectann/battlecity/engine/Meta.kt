package com.aectann.battlecity.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------- calendar

/** Milliseconds in a day; the meta features count whole UTC days, never wall-clock times. */
private const val MillisPerDay = 86_400_000L

/** Day index since the epoch, in UTC. Stable across time zones, which a streak needs. */
fun epochDay(nowMillis: Long): Int = (nowMillis / MillisPerDay).toInt()

// ------------------------------------------------------------------ daily reward

/**
 * What a claim hands over. There is no currency in this game, so the reward is lives on the
 * next run plus, at the end of a full week, a collection card.
 */
data class TanksDailyReward(
    val bonusLives: Int,
    val cardId: String?
)

@Serializable
data class TanksDailyState(
    /** Epoch day of the last claim; -1 before the first one. */
    val lastClaimDay: Int = -1,
    /** Highest epoch day ever seen, so moving the clock back cannot re-open a claim. */
    val lastSeenDay: Int = -1,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val claimedTotal: Int = 0,
    /** Lives banked for the next run, spent when a run starts. */
    val pendingBonusLives: Int = 0
)

enum class TanksDailyAvailability {
    /** A reward is waiting to be taken. */
    Claimable,

    /** Already taken today; come back tomorrow. */
    Claimed,

    /** The device clock is behind a day this save has already seen. */
    ClockBehind
}

data class TanksDailyStatus(
    val availability: TanksDailyAvailability,
    /** 1..7 position in the weekly cycle that a claim right now would land on. */
    val cycleDay: Int,
    val streak: Int,
    val bestStreak: Int,
    /** True when the last claim was more than a day ago, so claiming restarts the streak. */
    val streakWillReset: Boolean,
    val reward: TanksDailyReward
)

/**
 * Daily rewards and streaks, as pure rules over an epoch-day number.
 *
 * There is no server, so the day comes from the device. Two guards keep that honest: a save
 * remembers the furthest day it has seen, and a clock pushed forward breaks the streak rather
 * than advancing it. Neither can stop a determined player from farming rewards on a rolled
 * forward clock, and without a backend nothing can.
 */
object TanksDailyRewards {

    const val CycleLength = 7

    /** Starting lives can be topped up to this, no further. */
    const val MaxStartingLives = 6

    fun rewardFor(cycleDay: Int): TanksDailyReward {
        val day = cycleDay.coerceIn(1, CycleLength)
        val lives = when (day) {
            1, 2, 3 -> 1
            4, 5, 6 -> 2
            else -> 3
        }
        return TanksDailyReward(
            bonusLives = lives,
            cardId = if (day == CycleLength) TanksCollection.CardWeeklyStreak else null
        )
    }

    fun status(state: TanksDailyState, today: Int): TanksDailyStatus {
        val availability = when {
            today < state.lastSeenDay -> TanksDailyAvailability.ClockBehind
            today == state.lastClaimDay -> TanksDailyAvailability.Claimed
            else -> TanksDailyAvailability.Claimable
        }
        val continues = state.lastClaimDay >= 0 && today == state.lastClaimDay + 1
        val nextStreak = when {
            availability != TanksDailyAvailability.Claimable -> state.streak.coerceAtLeast(1)
            continues -> state.streak + 1
            else -> 1
        }
        val cycleDay = ((nextStreak - 1) % CycleLength) + 1
        return TanksDailyStatus(
            availability = availability,
            cycleDay = cycleDay,
            streak = state.streak,
            bestStreak = state.bestStreak,
            streakWillReset = availability == TanksDailyAvailability.Claimable &&
                !continues && state.streak > 0,
            reward = rewardFor(cycleDay)
        )
    }

    /** Returns the state after a claim, or the state unchanged when nothing is claimable. */
    fun claim(state: TanksDailyState, today: Int): TanksDailyState {
        val status = status(state, today)
        if (status.availability != TanksDailyAvailability.Claimable) {
            return state.copy(lastSeenDay = maxOf(state.lastSeenDay, today))
        }
        val newStreak = if (state.lastClaimDay >= 0 && today == state.lastClaimDay + 1) {
            state.streak + 1
        } else {
            1
        }
        return state.copy(
            lastClaimDay = today,
            lastSeenDay = maxOf(state.lastSeenDay, today),
            streak = newStreak,
            bestStreak = maxOf(state.bestStreak, newStreak),
            claimedTotal = state.claimedTotal + 1,
            pendingBonusLives = state.pendingBonusLives + status.reward.bonusLives
        )
    }

    /** Records that this day was seen, so a later rollback is detectable. */
    fun touch(state: TanksDailyState, today: Int): TanksDailyState =
        if (today > state.lastSeenDay) state.copy(lastSeenDay = today) else state

    /** Lives a run should start with, spending whatever the dailies banked. */
    fun startingLives(base: Int, state: TanksDailyState): Int =
        (base + state.pendingBonusLives).coerceAtMost(MaxStartingLives)

    fun spendBonusLives(state: TanksDailyState): TanksDailyState =
        if (state.pendingBonusLives == 0) state else state.copy(pendingBonusLives = 0)
}

// -------------------------------------------------------------------- collection

/** One collectible, described well enough for the UI to draw it without a lookup table. */
data class TanksCard(
    val id: String,
    val group: TanksCardGroup,
    /** Sprite key the renderer already knows, or null for a text-only badge. */
    val spriteKey: String?,
    /** What the player has to do, as a number the UI can show as "3 / 10". */
    val requirement: Int
)

enum class TanksCardGroup {
    Enemies,
    PowerUps,
    Milestones,
    Feats
}

/** Counters the collection is derived from. Everything here is cumulative across runs. */
@Serializable
data class TanksMetaStats(
    val enemyKills: Map<String, Int> = emptyMap(),
    val powerUps: Map<String, Int> = emptyMap(),
    val stagesCleared: Int = 0,
    val highestStageCleared: Int = 0,
    val bestPlayerLevel: Int = 1,
    val deathlessClears: Int = 0,
    val bestDailyStreak: Int = 0,
    /** Furthest endless wave ever survived. Zero before the first endless run. */
    val bestEndlessWave: Int = 0
) {
    fun plusKills(kills: Map<String, Int>): TanksMetaStats {
        if (kills.isEmpty()) return this
        val merged = enemyKills.toMutableMap()
        kills.forEach { (type, count) -> merged[type] = (merged[type] ?: 0) + count }
        return copy(enemyKills = merged)
    }

    fun plusPowerUps(collected: Map<String, Int>): TanksMetaStats {
        if (collected.isEmpty()) return this
        val merged = powerUps.toMutableMap()
        collected.forEach { (type, count) -> merged[type] = (merged[type] ?: 0) + count }
        return copy(powerUps = merged)
    }
}

/**
 * The card set and the rule for each one. Kept as data rather than scattered conditions so a
 * screen can render progress toward a locked card, not just its silhouette.
 */
object TanksCollection {

    const val CardWeeklyStreak = "feat_weekly_streak"

    private val enemyTargets = mapOf(
        "basic" to 25,
        "fast" to 15,
        "power" to 15,
        "armor" to 10
    )

    private val powerUpKeys = listOf(
        "star", "gun", "helmet", "timer", "shovel", "grenade", "tank_life", "boat"
    )

    private val milestoneStages = listOf(1, 5, 10, 20, BattleCityMaxStage)

    val cards: List<TanksCard> = buildList {
        enemyTargets.forEach { (type, target) ->
            // Armour has no plain sprite; its art is the green variant.
            val sprite = if (type == "armor") "enemy_armor_green_up" else "enemy_" + type + "_up"
            add(TanksCard("enemy_" + type, TanksCardGroup.Enemies, sprite, target))
        }
        powerUpKeys.forEach { key ->
            add(TanksCard("powerup_$key", TanksCardGroup.PowerUps, "powerup_$key", 1))
        }
        milestoneStages.forEach { stage ->
            add(TanksCard("stage_$stage", TanksCardGroup.Milestones, "ui_flag", stage))
        }
        add(TanksCard("feat_max_tank", TanksCardGroup.Feats, "player_green_level4_up", 4))
        add(TanksCard("feat_deathless", TanksCardGroup.Feats, "ui_life", 1))
        add(TanksCard(CardWeeklyStreak, TanksCardGroup.Feats, null, TanksDailyRewards.CycleLength))
    }

    /** How far the player has got toward a card, capped at its requirement. */
    fun progress(card: TanksCard, stats: TanksMetaStats, extraCards: Set<String>): Int {
        if (card.id in extraCards) return card.requirement
        return when {
            card.id.startsWith("enemy_") ->
                stats.enemyKills[card.id.removePrefix("enemy_")] ?: 0

            card.id.startsWith("powerup_") ->
                stats.powerUps[card.id.removePrefix("powerup_")] ?: 0

            card.id.startsWith("stage_") -> stats.highestStageCleared
            card.id == "feat_max_tank" -> stats.bestPlayerLevel
            card.id == "feat_deathless" -> stats.deathlessClears
            card.id == CardWeeklyStreak -> stats.bestDailyStreak
            else -> 0
        }.coerceAtMost(card.requirement)
    }

    fun isUnlocked(card: TanksCard, stats: TanksMetaStats, extraCards: Set<String>): Boolean =
        progress(card, stats, extraCards) >= card.requirement

    fun unlockedCount(stats: TanksMetaStats, extraCards: Set<String>): Int =
        cards.count { isUnlocked(it, stats, extraCards) }
}

// ------------------------------------------------------------------- leaderboard

@Serializable
data class TanksScoreEntry(
    val name: String,
    val score: Int,
    val stage: Int,
    /** Epoch day the run ended, so the table can show when without storing a clock time. */
    val day: Int
)

@Serializable
data class TanksLeaderboard(
    val entries: List<TanksScoreEntry> = emptyList()
)

/**
 * A personal best-runs table. It is local by design: there is no backend, and without one a
 * shared ranking cannot be trusted or even collected. [TanksScoreEntry] and this object are
 * the seam a remote board would slot into later.
 */
object TanksLeaderboards {

    const val Capacity = 10

    fun insert(board: TanksLeaderboard, entry: TanksScoreEntry): TanksLeaderboard {
        if (entry.score <= 0) return board
        val merged = (board.entries + entry)
            .sortedWith(compareByDescending<TanksScoreEntry> { it.score }.thenByDescending { it.stage })
            .take(Capacity)
        return TanksLeaderboard(merged)
    }

    /** Position a score would take, 1-based, or null when it would not make the table. */
    fun placementOf(board: TanksLeaderboard, score: Int): Int? {
        if (score <= 0) return null
        val better = board.entries.count { it.score >= score }
        return if (better < Capacity) better + 1 else null
    }
}

// ---------------------------------------------------------------------- nickname

/**
 * Player-chosen name for the local table. Trimmed, length-capped and stripped of control
 * characters; anything else is left alone so non-Latin names survive intact.
 */
object TanksNickname {

    const val MaxLength = 14
    const val Fallback = "PLAYER"

    fun sanitize(raw: String): String {
        val cleaned = raw
            .trim()
            .filter { !it.isISOControl() }
            .take(MaxLength)
            .trim()
        return cleaned.ifEmpty { Fallback }
    }

    fun isAcceptable(raw: String): Boolean = sanitize(raw) != Fallback || raw.trim() == Fallback
}

// --------------------------------------------------------------------- meta save

/** Everything the meta features persist, in one serialisable blob. */
@Serializable
data class TanksMetaSave(
    val daily: TanksDailyState = TanksDailyState(),
    val stats: TanksMetaStats = TanksMetaStats(),
    val awardedCards: Set<String> = emptySet(),
    val leaderboard: TanksLeaderboard = TanksLeaderboard(),
    /**
     * Endless runs keep their own table. Mixing them with campaign runs would rank two
     * different games against each other — a campaign score is bounded by 35 stages, an
     * endless score is not — and it is the endless number that goes to the portal board.
     */
    val endlessLeaderboard: TanksLeaderboard = TanksLeaderboard(),
    val nickname: String = TanksNickname.Fallback
)

object TanksMetaCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(save: TanksMetaSave): String = json.encodeToString(save)

    /** A corrupt or older blob yields a fresh save rather than an exception. */
    fun decode(raw: String?): TanksMetaSave {
        if (raw.isNullOrBlank()) return TanksMetaSave()
        return runCatching { json.decodeFromString<TanksMetaSave>(raw) }.getOrElse { TanksMetaSave() }
    }
}
