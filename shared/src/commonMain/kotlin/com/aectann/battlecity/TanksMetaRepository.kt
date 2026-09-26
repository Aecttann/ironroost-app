package com.aectann.battlecity

import com.aectann.battlecity.engine.TanksDailyRewards
import com.aectann.battlecity.engine.TanksDailyState
import com.aectann.battlecity.engine.TanksDailyStatus
import com.aectann.battlecity.engine.TanksLeaderboard
import com.aectann.battlecity.engine.TanksLeaderboards
import com.aectann.battlecity.engine.TanksMetaCodec
import com.aectann.battlecity.engine.TanksMetaSave
import com.aectann.battlecity.engine.TanksMetaStats
import com.aectann.battlecity.engine.TanksNickname
import com.aectann.battlecity.engine.TanksPendingRunLoss
import com.aectann.battlecity.engine.TanksScoreEntry
import com.aectann.battlecity.engine.epochDay

private const val MetaKey = "tanks_meta_save_v1"

/**
 * Reads and writes the one blob the meta features live in, and hands the pure rules in
 * `:engine` the day number they need.
 *
 * Everything is local. There is no backend, so the leaderboard is a personal table and the
 * daily calendar trusts the device clock as far as the guards in [TanksDailyRewards] allow.
 */
class TanksMetaRepository(
    private val store: TanksKeyValueStore,
    private val clock: () -> Long
) {
    private var cached: TanksMetaSave = TanksMetaCodec.decode(store.getString(MetaKey))

    init {
        // A process restart cannot resume the engine that owned an unresolved resurrection offer.
        finishPendingLoss()
    }

    fun today(): Int = epochDay(clock())

    fun save(): TanksMetaSave = cached

    /** Notes that this day has been seen, so a later clock rollback is detectable. */
    fun touch(): TanksMetaSave = mutate { it.copy(daily = TanksDailyRewards.touch(it.daily, today())) }

    fun dailyStatus(): TanksDailyStatus = TanksDailyRewards.status(cached.daily, today())

    /** Claims today's reward. Returns the save afterwards; unchanged when nothing was due. */
    fun claimDaily(): TanksMetaSave = mutate { current ->
        val status = TanksDailyRewards.status(current.daily, today())
        val claimed = TanksDailyRewards.claim(current.daily, today())
        val cards = status.reward.cardId
            ?.takeIf { claimed.claimedTotal > current.daily.claimedTotal }
            ?.let { current.awardedCards + it }
            ?: current.awardedCards
        current.copy(
            daily = claimed,
            awardedCards = cards,
            stats = current.stats.copy(bestDailyStreak = claimed.bestStreak)
        )
    }

    /** Lives the next run should start with, including anything the dailies banked. */
    fun startingLives(base: Int): Int = TanksDailyRewards.startingLives(base, cached.daily)

    fun consumeBonusLives(): TanksMetaSave =
        mutate { it.copy(daily = TanksDailyRewards.spendBonusLives(it.daily)) }

    /** Folds one finished stage into the cumulative counters the collection reads. */
    fun recordStageCleared(
        stage: Int,
        kills: Map<String, Int>,
        powerUps: Map<String, Int>,
        playerLevel: Int,
        livesLost: Int
    ): TanksMetaSave = mutate { current ->
        val stats = current.stats
            .plusKills(kills)
            .plusPowerUps(powerUps)
            .let {
                it.copy(
                    stagesCleared = it.stagesCleared + 1,
                    highestStageCleared = maxOf(it.highestStageCleared, stage),
                    bestPlayerLevel = maxOf(it.bestPlayerLevel, playerLevel),
                    deathlessClears = it.deathlessClears + if (livesLost == 0) 1 else 0
                )
            }
        current.copy(stats = stats)
    }

    /** Folds a stage that ended in defeat: the kills still count toward the collection. */
    fun recordRunLost(
        kills: Map<String, Int>,
        powerUps: Map<String, Int>,
        playerLevel: Int
    ): TanksMetaSave = mutate { current ->
        current.copy(
            stats = lostStageStats(current.stats, kills, powerUps, playerLevel)
        )
    }

    fun prepareRunLoss(
        kills: Map<String, Int>,
        powerUps: Map<String, Int>,
        playerLevel: Int,
        score: Int,
        stageOrWave: Int,
        endless: Boolean
    ) = mutate { current ->
        current.copy(pendingRunLoss = TanksPendingRunLoss(
            kills = kills,
            powerUps = powerUps,
            playerLevel = playerLevel,
            entry = TanksScoreEntry(current.nickname, score, stageOrWave, today()),
            endless = endless
        ))
    }

    /** Stats, score, and removal of the pending record are persisted in one save mutation. */
    fun finishPendingLoss(): TanksPendingRunLoss? {
        val loss = cached.pendingRunLoss ?: return null
        mutate { current ->
            val stats = lostStageStats(current.stats, loss.kills, loss.powerUps, loss.playerLevel)
            current.copy(
                pendingRunLoss = null,
                stats = if (loss.endless) stats.copy(bestEndlessWave = maxOf(stats.bestEndlessWave, loss.entry.stage)) else stats,
                leaderboard = if (loss.endless) current.leaderboard else TanksLeaderboards.insert(current.leaderboard, loss.entry),
                endlessLeaderboard = if (loss.endless) TanksLeaderboards.insert(current.endlessLeaderboard, loss.entry) else current.endlessLeaderboard
            )
        }
        return loss
    }

    fun discardPendingLoss() = mutate { it.copy(pendingRunLoss = null) }

    private fun lostStageStats(
        stats: TanksMetaStats,
        kills: Map<String, Int>,
        powerUps: Map<String, Int>,
        playerLevel: Int
    ) = stats.plusKills(kills).plusPowerUps(powerUps)
        .let { it.copy(bestPlayerLevel = maxOf(it.bestPlayerLevel, playerLevel)) }

    fun submitScore(score: Int, stage: Int): TanksMetaSave = mutate { current ->
        current.copy(
            leaderboard = TanksLeaderboards.insert(
                current.leaderboard,
                TanksScoreEntry(
                    name = current.nickname,
                    score = score,
                    stage = stage,
                    day = today()
                )
            )
        )
    }

    /**
     * Files a finished endless run. [wave] rides in [TanksScoreEntry.stage]: on this board the
     * number a run is measured by is the wave it died on, and the two never appear together.
     */
    fun submitEndlessScore(score: Int, wave: Int): TanksMetaSave = mutate { current ->
        current.copy(
            endlessLeaderboard = TanksLeaderboards.insert(
                current.endlessLeaderboard,
                TanksScoreEntry(
                    name = current.nickname,
                    score = score,
                    stage = wave,
                    day = today()
                )
            ),
            stats = current.stats.copy(
                bestEndlessWave = maxOf(current.stats.bestEndlessWave, wave)
            )
        )
    }

    fun leaderboard(): TanksLeaderboard = cached.leaderboard

    fun endlessLeaderboard(): TanksLeaderboard = cached.endlessLeaderboard

    fun nickname(): String = cached.nickname

    fun setNickname(raw: String): TanksMetaSave =
        mutate { it.copy(nickname = TanksNickname.sanitize(raw)) }

    fun stats(): TanksMetaStats = cached.stats

    /** Clears the meta save. Kept separate from stage progress so one reset is not the other. */
    fun reset(): TanksMetaSave = mutate { TanksMetaSave(nickname = it.nickname) }

    private inline fun mutate(block: (TanksMetaSave) -> TanksMetaSave): TanksMetaSave {
        val updated = block(cached)
        if (updated != cached) {
            cached = updated
            store.putString(MetaKey, TanksMetaCodec.encode(updated))
        }
        return updated
    }
}

/** Key-value store backed by a map, for tests and previews. */
class InMemoryKeyValueStore : TanksKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getInt(key: String, fallback: Int): Int = values[key]?.toIntOrNull() ?: fallback

    override fun putInt(key: String, value: Int) {
        values[key] = value.toString()
    }

    override fun getBoolean(key: String, fallback: Boolean): Boolean = when (values[key]) {
        "true" -> true
        "false" -> false
        else -> fallback
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value.toString()
    }

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
