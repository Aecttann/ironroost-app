package com.aectann.classicgames.battlecity

/**
 * Everything the tank game needs from the host application, expressed as narrow ports.
 *
 * The game never touches the app's view models, preferences or backend directly, so it can
 * be lifted into its own module (or its own project) by supplying new implementations.
 */
interface TanksWallet {
    fun tokens(): Int

    /** Returns false when the player cannot afford [amount]; nothing is deducted then. */
    fun spend(amount: Int): Boolean
}

interface TanksProgressStore {
    fun highestCompletedStage(): Int
    fun saveHighestCompletedStage(stage: Int)
    fun recordAttempt(): Int
    fun recordStageCleared(): Int
    fun isSoundEnabled(): Boolean
}

interface TanksAnalytics {
    fun onAttemptStarted(totalAttempts: Int)
    fun onStageCleared(stage: Int, totalWins: Int, highestCompletedStage: Int)
}

/** No-op analytics, useful for tests and for a standalone build with no backend. */
object NoopTanksAnalytics : TanksAnalytics {
    override fun onAttemptStarted(totalAttempts: Int) = Unit
    override fun onStageCleared(stage: Int, totalWins: Int, highestCompletedStage: Int) = Unit
}
