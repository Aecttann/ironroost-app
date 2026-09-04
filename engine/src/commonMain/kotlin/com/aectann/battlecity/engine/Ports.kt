package com.aectann.battlecity.engine

/**
 * Everything the game needs from its host, expressed as narrow ports.
 *
 * The standalone app plugs in free play and local storage; embedding the game in a larger
 * app (as it once lived inside ClassicGames) means supplying different implementations,
 * not editing the game.
 */
interface TanksWallet {
    fun tokens(): Int

    /** Returns false when the player cannot afford [amount]; nothing is deducted then. */
    fun spend(amount: Int): Boolean
}

/** Free play: the standalone game charges nothing to start a run. */
object FreePlayWallet : TanksWallet {
    override fun tokens(): Int = 0
    override fun spend(amount: Int): Boolean = true
}

interface TanksProgressStore {
    fun highestCompletedStage(): Int
    fun saveHighestCompletedStage(stage: Int)
    fun recordAttempt(): Int
    fun recordStageCleared(): Int
    fun isSoundEnabled(): Boolean
    fun setSoundEnabled(enabled: Boolean)

    /** Locks every stage again and clears the counters. The sound setting is kept. */
    fun resetProgress()
}

/** In-memory progress, for tests and previews. */
class InMemoryProgressStore(
    private var highest: Int = 0,
    private var soundEnabled: Boolean = true
) : TanksProgressStore {
    private var attempts = 0
    private var wins = 0

    override fun highestCompletedStage(): Int = highest

    override fun saveHighestCompletedStage(stage: Int) {
        if (stage > highest) highest = stage
    }

    override fun recordAttempt(): Int = ++attempts
    override fun recordStageCleared(): Int = ++wins
    override fun isSoundEnabled(): Boolean = soundEnabled

    override fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    override fun resetProgress() {
        highest = 0
        attempts = 0
        wins = 0
    }
}

interface TanksAnalytics {
    fun onAttemptStarted(totalAttempts: Int)
    fun onStageCleared(stage: Int, totalWins: Int, highestCompletedStage: Int)
}

object NoopTanksAnalytics : TanksAnalytics {
    override fun onAttemptStarted(totalAttempts: Int) = Unit
    override fun onStageCleared(stage: Int, totalWins: Int, highestCompletedStage: Int) = Unit
}
