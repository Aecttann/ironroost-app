package com.aectann.battlecity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aectann.battlecity.engine.BattleCityEngine
import com.aectann.battlecity.engine.BattleCityInput
import com.aectann.battlecity.engine.BattleCityInputs
import com.aectann.battlecity.engine.BattleCityLevelData
import com.aectann.battlecity.engine.BattleCityMaxStage
import com.aectann.battlecity.engine.BattleCityRenderState
import com.aectann.battlecity.engine.BattleCitySoundEvent
import com.aectann.battlecity.engine.BattleCityStageInfo
import com.aectann.battlecity.engine.BattleCityStatus
import com.aectann.battlecity.engine.TanksAnalytics
import com.aectann.battlecity.engine.TanksProgressStore
import com.aectann.battlecity.engine.TanksCollection
import com.aectann.battlecity.engine.TanksDailyAvailability
import com.aectann.battlecity.engine.TanksDailyStatus
import com.aectann.battlecity.engine.TanksEndlessWaves
import com.aectann.battlecity.engine.TanksLeaderboard
import com.aectann.battlecity.engine.TanksMetaStats
import com.aectann.battlecity.engine.TanksUpgrade
import com.aectann.battlecity.engine.TanksUpgradeLoadout
import com.aectann.battlecity.engine.TanksWallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val StartingLives = 3

/** Local co-op is two tanks on one keyboard; there is no third seat. */
const val TanksMaxPlayers = 2

internal fun highestUnlockedStage(highestCompletedStage: Int, allStagesUnlocked: Boolean): Int {
    if (allStagesUnlocked) return BattleCityMaxStage
    return (highestCompletedStage.coerceAtLeast(0) + 1).coerceIn(1, BattleCityMaxStage)
}

enum class TanksPhase {
    Loading,
    Ready,
    Playing,
    Paused,
    StageCleared,

    /** An endless wave is beaten and the board is held still for the upgrade pick. */
    WaveCleared,
    GameOver,
    Error
}

/**
 * Which game a run is.
 *
 * The campaign is the 35 stages, finite and gated by progress. Endless is one arena, escalating
 * waves and a build — it exists because a campaign a player finishes once cannot hold a session
 * open, and the score it produces is the only number worth ranking.
 */
enum class TanksRunMode {
    Campaign,
    Endless
}

data class TanksKillRow(
    val type: String,
    val count: Int,
    val points: Int
)

data class TanksStageSummary(
    val stage: Int,
    val stageScore: Int,
    val campaignScore: Int,
    val livesLeft: Int,
    val kills: List<TanksKillRow>
)

data class TanksSession(
    val stage: Int = 1,
    val phase: TanksPhase = TanksPhase.Loading,
    val campaignScore: Int = 0,
    /** Lives each player carries into the next stage; the size is how many are playing. */
    val carriedLives: List<Int> = listOf(StartingLives),
    val highestCompletedStage: Int = 0,
    /** True once the current run has been paid for; free play sets it on the first start. */
    val charged: Boolean = false,
    val soundEnabled: Boolean = true,
    val stageInfos: Map<Int, BattleCityStageInfo> = emptyMap(),
    val summary: TanksStageSummary? = null,
    val errorMessage: String? = null,
    val mode: TanksRunMode = TanksRunMode.Campaign,
    /** Endless wave being fought. Stays 1 in the campaign. */
    val wave: Int = 1,
    /** What the endless run has banked so far. */
    val loadout: TanksUpgradeLoadout = TanksUpgradeLoadout(),
    /**
     * What the pick screen is offering. Empty while playing, and also empty at a wave rollover
     * once every upgrade is maxed — the picker is skipped rather than shown with no cards.
     */
    val upgradeChoices: List<TanksUpgrade> = emptyList(),
    val canResurrect: Boolean = false,
    val resurrectionInProgress: Boolean = false,
    val resurrectionResult: ResurrectionAdResult? = null
) {
    val playerCount: Int get() = carriedLives.size
    val isCoop: Boolean get() = playerCount > 1
    val isEndless: Boolean get() = mode == TanksRunMode.Endless
}

/** Everything the meta screens read, recomputed from the save after each change. */
data class TanksMetaUi(
    val daily: TanksDailyStatus,
    val stats: TanksMetaStats,
    val awardedCards: Set<String>,
    val leaderboard: TanksLeaderboard,
    /** Endless runs rank separately; see [TanksMetaSave.endlessLeaderboard] for why. */
    val endlessLeaderboard: TanksLeaderboard,
    val nickname: String,
    val collectionUnlocked: Int,
    val collectionTotal: Int,
    val pendingBonusLives: Int,
    /**
     * True when the host has a ranking of its own that finished runs are sent to. The board
     * itself cannot be read back — see [TanksPortal.submitScore] — so this only decides whether
     * the records screen says a run also left the device.
     */
    val hasPortalLeaderboard: Boolean
)

sealed interface TanksMessage {
    data object NotEnoughTokens : TanksMessage
    data class DailyClaimed(val bonusLives: Int, val cardId: String?) : TanksMessage
}

/**
 * Owns a run: which stage is loaded, whether it is running, and the campaign totals carried
 * between stages. Surviving configuration changes is what keeps a rotation from restarting
 * the stage under the player.
 *
 * @param attemptCost what a run costs through [wallet]. Zero for the standalone game; a real
 *        price when the game is embedded in a host app with its own economy.
 */
class TanksViewModel(
    private val wallet: TanksWallet,
    private val progress: TanksProgressStore,
    private val analytics: TanksAnalytics,
    private val metaRepository: TanksMetaRepository,
    private val portal: TanksPortal = NoopTanksPortal,
    private val allStagesUnlocked: Boolean = false,
    private val attemptCost: Int = 0,
    private val seedProvider: () -> Long = { Random.nextLong() },
    private val resurrectionEnabled: Boolean = false,
    private val levelLoader: suspend (Int) -> BattleCityLevelData = TanksResources::loadLevel,
    private val stageInfosLoader: suspend () -> Map<Int, BattleCityStageInfo> = TanksResources::loadStageInfos
) : ViewModel() {

    private val _session = MutableStateFlow(
        TanksSession(
            highestCompletedStage = progress.highestCompletedStage(),
            soundEnabled = progress.isSoundEnabled()
        )
    )
    val session: StateFlow<TanksSession> = _session.asStateFlow()

    private val _render = MutableStateFlow<BattleCityRenderState?>(null)
    val render: StateFlow<BattleCityRenderState?> = _render.asStateFlow()

    private val _messages = MutableSharedFlow<TanksMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<TanksMessage> = _messages.asSharedFlow()

    private val _meta = MutableStateFlow(readMeta())
    val meta: StateFlow<TanksMetaUi> = _meta.asStateFlow()

    private var engine: BattleCityEngine? = null
    private var loadGeneration = 0
    private var retryConsumesDailyBonus = false
    private var pendingLoss: BattleCityRenderState? = null

    init {
        // Record today even without a claim, so a later clock rollback is detectable.
        metaRepository.touch()
        refreshMeta()
        loadStage(_session.value.stage, resetCampaign = true)
        viewModelScope.launch {
            val infos = stageInfosLoader()
            _session.value = _session.value.copy(stageInfos = infos)
        }
    }

    // ------------------------------------------------------------------ loading

    private fun loadStage(
        stage: Int,
        resetCampaign: Boolean,
        playerCount: Int = -1,
        mode: TanksRunMode = _session.value.mode,
        consumeDailyBonus: Boolean = false
    ) {
        finishLostRun()
        val request = ++loadGeneration
        val previous = _session.value
        val players = if (playerCount > 0) playerCount else previous.playerCount
        val shouldConsumeDailyBonus = consumeDailyBonus &&
            metaRepository.save().daily.pendingBonusLives > 0
        retryConsumesDailyBonus = consumeDailyBonus
        _session.value = previous.copy(
            stage = stage,
            mode = mode,
            phase = TanksPhase.Loading,
            summary = null,
            errorMessage = null,
            canResurrect = false,
            resurrectionInProgress = false,
            resurrectionResult = null,
            wave = 1,
            loadout = TanksUpgradeLoadout(),
            upgradeChoices = emptyList(),
            campaignScore = if (resetCampaign) 0 else previous.campaignScore,
            carriedLives = when {
                resetCampaign -> startingLivesForNewCampaign(
                    playerCount = players,
                    includeDailyBonus = shouldConsumeDailyBonus
                )
                // A player who joined or left mid-campaign still needs a seat of the right size.
                previous.carriedLives.size != players ->
                    List(players) { index -> previous.carriedLives.getOrElse(index) { 0 } }

                else -> previous.carriedLives
            }
        )

        viewModelScope.launch {
            val loaded = runCatching { levelLoader(stage) }
            loaded.onSuccess { level ->
                if (request != loadGeneration) return@onSuccess
                val current = _session.value
                if (shouldConsumeDailyBonus) {
                    metaRepository.consumeBonusLives()
                    refreshMeta()
                }
                val created = BattleCityEngine(
                    stageNumber = stage,
                    level = level,
                    seed = seedProvider(),
                    initialLives = current.carriedLives,
                    initialScoreForExtraLife = current.campaignScore,
                    endless = mode == TanksRunMode.Endless
                )
                engine = created
                retryConsumesDailyBonus = false
                // The constructor already laid the stage out; resetting again would rebuild it.
                _render.value = created.currentState()
                _session.value = current.copy(phase = TanksPhase.Ready)
            }.onFailure { error ->
                if (request != loadGeneration) return@onFailure
                engine = null
                _render.value = null
                _session.value = _session.value.copy(
                    phase = TanksPhase.Error,
                    errorMessage = error.message ?: "unknown error"
                )
            }
        }
    }

    /**
     * Starts an endless run on the fixed arena.
     *
     * Endless deliberately ignores campaign progress: it is not gated behind clearing stages,
     * because a player who arrives from a portal listing and bounces off stage three should
     * still have the mode that keeps them playing.
     */
    fun startEndless(playerCount: Int = _session.value.playerCount) {
        _session.value = _session.value.copy(charged = false)
        loadStage(
            stage = TanksEndlessWaves.ArenaStage,
            resetCampaign = true,
            playerCount = playerCount.coerceIn(1, TanksMaxPlayers),
            mode = TanksRunMode.Endless,
            consumeDailyBonus = true
        )
    }

    /**
     * Banks the pick and sends in the next wave. Passing null takes nothing, which is both the
     * skip button and what a fully maxed run does automatically.
     */
    fun chooseUpgrade(upgrade: TanksUpgrade?) {
        val current = _session.value
        if (current.phase != TanksPhase.WaveCleared) return
        val running = engine ?: return

        val state = running.beginNextWave(upgrade)
        _render.value = state
        _session.value = current.copy(
            phase = TanksPhase.Playing,
            wave = state.wave,
            loadout = state.loadout,
            upgradeChoices = emptyList()
        )
    }

    // ------------------------------------------------------------------ controls

    /** Starts a run, or resumes a paused one. */
    fun startOrResume() {
        val current = _session.value
        if (current.phase != TanksPhase.Ready && current.phase != TanksPhase.Paused) return

        if (!current.charged) {
            if (!wallet.spend(attemptCost)) {
                _messages.tryEmit(TanksMessage.NotEnoughTokens)
                return
            }
            val attempts = progress.recordAttempt()
            analytics.onAttemptStarted(attempts)
            _session.value = current.copy(charged = true, phase = TanksPhase.Playing)
            return
        }
        _session.value = current.copy(phase = TanksPhase.Playing)
    }

    fun pause() {
        if (_session.value.phase == TanksPhase.Playing) {
            _session.value = _session.value.copy(phase = TanksPhase.Paused)
        }
    }

    fun togglePause() {
        when (_session.value.phase) {
            TanksPhase.Playing -> pause()
            TanksPhase.Ready, TanksPhase.Paused -> startOrResume()
            else -> Unit
        }
    }

    /** Clearing a stage keeps the run going: the next stage of a winning streak is free. */
    fun advanceToNextStage() {
        val current = _session.value
        if (current.phase != TanksPhase.StageCleared) return
        val next = if (current.stage >= BattleCityMaxStage) 1 else current.stage + 1
        if (next == 1) {
            // The campaign is finished, so this run's total belongs on the board. A run that
            // ends in defeat is recorded in onRunLost instead; between them every run counts once.
            submitRun(current.campaignScore, current.stage)
        }
        loadStage(next, resetCampaign = next == 1)
    }

    /**
     * Restarting after a loss starts a fresh run and resets the totals. An endless run restarts
     * as endless — being dropped back into the campaign after a good run would read as a bug.
     */
    fun retryAfterLoss() {
        val current = _session.value
        if (current.phase != TanksPhase.GameOver || current.resurrectionInProgress) return
        finishLostRun()
        if (!wallet.spend(attemptCost)) {
            _messages.tryEmit(TanksMessage.NotEnoughTokens)
            return
        }
        val attempts = progress.recordAttempt()
        analytics.onAttemptStarted(attempts)
        _session.value = current.copy(charged = true)
        loadStage(current.stage, resetCampaign = true, mode = current.mode)
    }

    /** Restarts the current stage from the pause menu, keeping the run paid for. */
    fun restartStage() {
        val current = _session.value
        loadStage(current.stage, resetCampaign = true, mode = current.mode)
    }

    /** Picking a stage is a campaign act, so it also drops an endless run back to the campaign. */
    fun selectStage(stage: Int, playerCount: Int = _session.value.playerCount) {
        if (!isStageUnlocked(stage)) return
        _session.value = _session.value.copy(charged = false)
        loadStage(
            stage = stage,
            resetCampaign = true,
            playerCount = playerCount.coerceIn(1, TanksMaxPlayers),
            mode = TanksRunMode.Campaign,
            consumeDailyBonus = true
        )
    }

    fun retryLoad() = loadStage(
        stage = _session.value.stage,
        resetCampaign = true,
        consumeDailyBonus = retryConsumesDailyBonus
    )

    fun setSoundEnabled(enabled: Boolean) {
        progress.setSoundEnabled(enabled)
        _session.value = _session.value.copy(soundEnabled = enabled)
    }

    /** Locks every stage again and drops the current run back to stage one. */
    fun resetProgress() {
        progress.resetProgress()
        _session.value = _session.value.copy(highestCompletedStage = 0, charged = false)
        loadStage(1, resetCampaign = true, mode = TanksRunMode.Campaign)
    }

    fun isStageUnlocked(stage: Int): Boolean {
        if (stage !in 1..BattleCityMaxStage) return false
        return stage <= unlockedStage()
    }

    fun unlockedStage(): Int = highestUnlockedStage(
        highestCompletedStage = _session.value.highestCompletedStage,
        allStagesUnlocked = allStagesUnlocked
    )

    // -------------------------------------------------------------------- ticking

    /** Advances the simulation. Returns the sounds the caller should play for this frame. */
    fun advance(deltaSeconds: Float, inputs: BattleCityInputs): List<BattleCitySoundEvent> {
        if (_session.value.phase != TanksPhase.Playing) return emptyList()
        val current = engine ?: return emptyList()

        val step = current.step(deltaSeconds, inputs)
        _render.value = step.state

        when (step.state.status) {
            BattleCityStatus.Won -> onStageCleared(step.state)
            BattleCityStatus.Lost -> onRunLost(step.state)
            // Endless never reports Won; a drained wave raises waveCleared and the engine holds
            // the board still until chooseUpgrade sends the next one in.
            BattleCityStatus.Running -> if (step.state.waveCleared) onWaveCleared(step.state)
        }
        return step.events
    }

    private fun onWaveCleared(state: BattleCityRenderState) {
        val current = _session.value
        if (current.phase != TanksPhase.Playing) return

        val offer = engine?.upgradeOffer().orEmpty()
        _session.value = current.copy(
            phase = TanksPhase.WaveCleared,
            wave = state.wave,
            loadout = state.loadout,
            carriedLives = state.players.map { it.lives },
            upgradeChoices = offer
        )

        // A run that has taken everything gets no card to look at, so it rolls straight on
        // rather than staring at an empty picker.
        if (offer.isEmpty()) chooseUpgrade(null)
    }

    private fun onStageCleared(state: BattleCityRenderState) {
        val current = _session.value
        if (current.phase != TanksPhase.Playing) return

        val campaignScore = current.campaignScore + state.stageScore
        val highest = maxOf(current.highestCompletedStage, state.stageNumber)
        progress.saveHighestCompletedStage(state.stageNumber)
        val wins = progress.recordStageCleared()
        analytics.onStageCleared(state.stageNumber, wins, highest)

        metaRepository.recordStageCleared(
            stage = state.stageNumber,
            kills = state.killsByType,
            powerUps = state.powerUpsCollected,
            playerLevel = state.playerLevel,
            livesLost = state.playerDeaths
        )
        refreshMeta()

        _session.value = current.copy(
            phase = TanksPhase.StageCleared,
            campaignScore = campaignScore,
            carriedLives = state.players.map { it.lives },
            highestCompletedStage = highest,
            summary = TanksStageSummary(
                stage = state.stageNumber,
                stageScore = state.stageScore,
                campaignScore = campaignScore,
                livesLeft = state.lives,
                kills = state.killsByType.map { (type, count) ->
                    TanksKillRow(type, count, count * pointsForType(type))
                }
            )
        )
    }

    private fun onRunLost(state: BattleCityRenderState) {
        val current = _session.value
        if (current.phase != TanksPhase.Playing) return

        pendingLoss = state
        metaRepository.prepareRunLoss(
            kills = state.killsByType,
            powerUps = state.powerUpsCollected,
            playerLevel = state.playerLevel,
            score = if (current.isEndless) state.stageScore else current.campaignScore + state.stageScore,
            stageOrWave = if (current.isEndless) state.wave else state.stageNumber,
            endless = current.isEndless
        )
        val canResurrect = resurrectionEnabled && engine?.canResurrect == true
        _session.value = current.copy(
            phase = TanksPhase.GameOver,
            charged = false,
            wave = state.wave,
            loadout = state.loadout,
            canResurrect = canResurrect,
            resurrectionResult = null
        )
        if (!canResurrect) finishLostRun()
    }

    /** Commits a declined loss once, before leaving or replacing the run. */
    fun finishLostRun() {
        if (pendingLoss == null) return
        pendingLoss = null
        val current = _session.value
        _session.value = current.copy(canResurrect = false)
        val loss = metaRepository.finishPendingLoss()
        if (loss?.endless == true) portal.submitScore(loss.entry.score)
        refreshMeta()
    }

    fun resurrectWithAd(ads: TanksAds) {
        val current = _session.value
        val running = engine ?: return
        val loss = pendingLoss ?: return
        if (current.phase != TanksPhase.GameOver || !current.canResurrect ||
            current.resurrectionInProgress || !ads.supportsResurrection) return

        _session.value = current.copy(resurrectionInProgress = true, resurrectionResult = null)
        viewModelScope.launch {
            try {
                val result = try {
                    ads.showResurrection()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ResurrectionAdResult.Failed
                }
                // A reward belongs only to the engine and loss that initiated the request.
                if (engine !== running || pendingLoss !== loss) return@launch
                val restored = if (result == ResurrectionAdResult.Earned) running.resurrect() else null
                if (restored != null) {
                    metaRepository.discardPendingLoss()
                    pendingLoss = null
                    _render.value = restored
                    _session.value = _session.value.copy(
                        phase = TanksPhase.Paused,
                        charged = true,
                        carriedLives = restored.players.map { it.lives },
                        canResurrect = false,
                        resurrectionResult = null
                    )
                } else {
                    _session.value = _session.value.copy(resurrectionResult = result)
                }
            } finally {
                if (engine === running) {
                    _session.value = _session.value.copy(resurrectionInProgress = false)
                }
            }
        }
    }

    override fun onCleared() {
        finishLostRun()
        super.onCleared()
    }

    /**
     * A finished campaign run goes to the game's own table only.
     *
     * It deliberately does not reach the host's board: that board is one list, and a campaign
     * score capped by 35 stages ranked against an uncapped endless score would make both
     * meaningless. Endless is what the portal gets through [finishLostRun].
     */
    private fun submitRun(score: Int, stage: Int) {
        metaRepository.submitScore(score = score, stage = stage)
        refreshMeta()
    }

    private fun pointsForType(type: String): Int = when (type) {
        "fast" -> 200
        "power" -> 300
        "armor" -> 400
        else -> 100
    }

    // --------------------------------------------------------------------- meta

    /** Takes today's daily reward, if one is due. */
    fun claimDaily() {
        val before = metaRepository.dailyStatus()
        if (before.availability != TanksDailyAvailability.Claimable) return
        metaRepository.claimDaily()
        refreshMeta()
        _messages.tryEmit(
            TanksMessage.DailyClaimed(before.reward.bonusLives, before.reward.cardId)
        )
    }

    fun setNickname(raw: String) {
        metaRepository.setNickname(raw)
        refreshMeta()
    }

    /**
     * Lives a fresh campaign starts with. Anything the dailies banked is spent here, which is
     * what makes the reward feel like it belongs to the next run rather than to every run.
     */
    private fun startingLivesForNewCampaign(
        playerCount: Int,
        includeDailyBonus: Boolean
    ): List<Int> {
        val lives = if (includeDailyBonus) {
            metaRepository.startingLives(StartingLives)
        } else {
            StartingLives
        }
        // The banked lives are the save's, not one seat's, so both players start on the same count.
        return List(playerCount.coerceIn(1, TanksMaxPlayers)) { lives }
    }

    private fun refreshMeta() {
        _meta.value = readMeta()
    }

    private fun readMeta(): TanksMetaUi {
        val save = metaRepository.save()
        return TanksMetaUi(
            daily = metaRepository.dailyStatus(),
            stats = save.stats,
            awardedCards = save.awardedCards,
            leaderboard = save.leaderboard,
            endlessLeaderboard = save.endlessLeaderboard,
            nickname = save.nickname,
            collectionUnlocked = TanksCollection.unlockedCount(save.stats, save.awardedCards),
            collectionTotal = TanksCollection.cards.size,
            pendingBonusLives = save.daily.pendingBonusLives,
            hasPortalLeaderboard = portal.hasLeaderboard
        )
    }
}
