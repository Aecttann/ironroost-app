package com.aectann.battlecity

import androidx.compose.runtime.Composable
import com.aectann.battlecity.engine.TanksProgressStore

/** Plays the game's short clips. Loading is common; playback is platform work. */
interface TanksSoundPlayer {
    fun setEnabled(enabled: Boolean)
    fun play(clip: TanksClip)

    /** Loops the engine rumble while the tank is being driven. */
    fun setEngineRunning(running: Boolean)
    fun release()
}

object SilentSoundPlayer : TanksSoundPlayer {
    override fun setEnabled(enabled: Boolean) = Unit
    override fun play(clip: TanksClip) = Unit
    override fun setEngineRunning(running: Boolean) = Unit
    override fun release() = Unit
}

/** Portal-facing lifecycle signals. Standalone mobile hosts intentionally use the no-op version. */
interface TanksPortal {
    fun setGameplayActive(active: Boolean)
    fun setStage(stage: Int)
    fun reportProgress(completedStage: Int, totalStages: Int)
    fun clearContext()

    /**
     * Offers a finished run to the host's own ranking.
     *
     * This is a one-way sink, and not by choice: the CrazyGames HTML5 SDK exposes exactly one
     * leaderboard call to page code, `user.submitScore`. There is no client-side read — scores
     * come back only through a server-side API keyed with a secret that cannot ship in a page,
     * and the portal draws the board itself in its own chrome around the game. So the local
     * table in [TanksLeaderboards] stays the one the records screen reads, on every platform.
     */
    fun submitScore(score: Int)

    /**
     * Whether the host actually has a board behind [submitScore]. False until CrazyGames invites
     * the game to a leaderboard and its key is stamped into the page, so the records screen can
     * avoid promising a public ranking that does not exist yet.
     */
    val hasLeaderboard: Boolean get() = false
}

object NoopTanksPortal : TanksPortal {
    override fun setGameplayActive(active: Boolean) = Unit
    override fun setStage(stage: Int) = Unit
    override fun reportProgress(completedStage: Int, totalStages: Int) = Unit
    override fun clearContext() = Unit
    override fun submitScore(score: Int) = Unit
}

/** The narrow slice of each platform the game actually needs. */
interface TanksPlatform {
    val progressStore: TanksProgressStore

    /** Backing store the meta features persist their save in. */
    val keyValueStore: TanksKeyValueStore

    /** Marketing version of the host app, shown on the About screen. */
    val appVersion: String

    /** Wall-clock milliseconds since the epoch, for the daily reward calendar. */
    fun nowEpochMillis(): Long

    /** Metrics/context bridge supplied by a web portal host. */
    val portal: TanksPortal get() = NoopTanksPortal

    fun createSoundPlayer(clips: Map<TanksClip, ByteArray>, enabled: Boolean): TanksSoundPlayer

    /** Keeps the display awake while a run is in progress. */
    fun setKeepAwake(enabled: Boolean)

    /** Hides system chrome so the board gets the whole screen. */
    fun setImmersive(enabled: Boolean)
}

@Composable
expect fun rememberTanksPlatform(): TanksPlatform

/** Minimal persistence contract; each platform backs it with its own key-value store. */
interface TanksKeyValueStore {
    fun getInt(key: String, fallback: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, fallback: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)

    /** Used for the meta save, which is one JSON blob. */
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * Progress rules live here once, so Android and iOS only supply storage.
 * Keeping them out of the statistics counters is deliberate: wiping stats must never
 * re-lock the stages a player has already cleared.
 */
class StoredTanksProgress(private val store: TanksKeyValueStore) : TanksProgressStore {

    override fun highestCompletedStage(): Int = store.getInt(KeyHighestStage, 0)

    override fun saveHighestCompletedStage(stage: Int) {
        if (stage > highestCompletedStage()) store.putInt(KeyHighestStage, stage)
    }

    override fun recordAttempt(): Int {
        val next = store.getInt(KeyAttempts, 0) + 1
        store.putInt(KeyAttempts, next)
        return next
    }

    override fun recordStageCleared(): Int {
        val next = store.getInt(KeyClears, 0) + 1
        store.putInt(KeyClears, next)
        return next
    }

    override fun isSoundEnabled(): Boolean = store.getBoolean(KeySoundEnabled, true)

    override fun setSoundEnabled(enabled: Boolean) = store.putBoolean(KeySoundEnabled, enabled)

    override fun resetProgress() {
        store.putInt(KeyHighestStage, 0)
        store.putInt(KeyAttempts, 0)
        store.putInt(KeyClears, 0)
    }

    private companion object {
        const val KeyHighestStage = "tanks_highest_completed_stage"
        const val KeyAttempts = "tanks_attempts"
        const val KeyClears = "tanks_stage_clears"
        const val KeySoundEnabled = "tanks_sound_enabled"
    }
}

/**
 * System back, where the platform has one. Android maps it to the predictive-back handler;
 * iOS has no equivalent for a full-screen game, so it does nothing there.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
