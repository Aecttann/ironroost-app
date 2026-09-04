package com.aectann.classicgames.battlecity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TanksAttemptTokenCost = 5
private const val StartingLives = 3

enum class TanksPhase {
    Loading,
    Ready,
    Playing,
    Paused,
    StageCleared,
    GameOver,
    Error
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
    val carriedLives: Int = StartingLives,
    val highestCompletedStage: Int = 0,
    /** True once the current run has been paid for; cleared only when a new run starts. */
    val charged: Boolean = false,
    val stageInfos: Map<Int, BattleCityStageInfo> = emptyMap(),
    val summary: TanksStageSummary? = null,
    val errorMessage: String? = null
)

sealed interface TanksMessage {
    data object NotEnoughTokens : TanksMessage
}

/**
 * Owns a tank run: which stage is loaded, whether it is paid for and running, and the
 * campaign totals carried between stages. Survives configuration changes, which is what
 * keeps a rotation from restarting the stage under the player.
 */
class TanksViewModel(
    private val repository: BattleCityRepository,
    private val wallet: TanksWallet,
    private val progress: TanksProgressStore,
    private val analytics: TanksAnalytics,
    private val allStagesUnlocked: Boolean,
    private val seedProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _session = MutableStateFlow(
        TanksSession(highestCompletedStage = progress.highestCompletedStage())
    )
    val session: StateFlow<TanksSession> = _session.asStateFlow()

    private val _render = MutableStateFlow<BattleCityRenderState?>(null)
    val render: StateFlow<BattleCityRenderState?> = _render.asStateFlow()

    private val _messages = MutableSharedFlow<TanksMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<TanksMessage> = _messages.asSharedFlow()

    private var engine: BattleCityEngine? = null

    init {
        loadStage(_session.value.stage, resetCampaign = true)
        viewModelScope.launch {
            val infos = withContext(Dispatchers.IO) { repository.stageInfos() }
            _session.value = _session.value.copy(stageInfos = infos)
        }
    }

    // ------------------------------------------------------------------ loading

    private fun loadStage(stage: Int, resetCampaign: Boolean) {
        _session.value = _session.value.copy(
            stage = stage,
            phase = TanksPhase.Loading,
            summary = null,
            errorMessage = null,
            campaignScore = if (resetCampaign) 0 else _session.value.campaignScore,
            carriedLives = if (resetCampaign) StartingLives else _session.value.carriedLives
        )

        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { repository.loadLevel(stage) }
            }
            loaded.onSuccess { level ->
                val current = _session.value
                val created = BattleCityEngine(
                    stageNumber = stage,
                    level = level,
                    seed = seedProvider(),
                    initialLives = current.carriedLives,
                    initialScoreForExtraLife = current.campaignScore
                )
                engine = created
                // The constructor already laid the stage out; resetting again would rebuild it.
                _render.value = created.currentState()
                _session.value = current.copy(phase = TanksPhase.Ready)
            }.onFailure { error ->
                engine = null
                _render.value = null
                _session.value = _session.value.copy(
                    phase = TanksPhase.Error,
                    errorMessage = error.message ?: error::class.java.simpleName
                )
            }
        }
    }

    // ------------------------------------------------------------------ controls

    /** Starts a paid run, or resumes a paused one. */
    fun startOrResume() {
        val current = _session.value
        if (current.phase != TanksPhase.Ready && current.phase != TanksPhase.Paused) return

        if (!current.charged) {
            if (!wallet.spend(TanksAttemptTokenCost)) {
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

    /** Clearing a stage keeps the run paid for: the next stage of a winning streak is free. */
    fun advanceToNextStage() {
        val current = _session.value
        if (current.phase != TanksPhase.StageCleared) return
        val next = if (current.stage >= BattleCityMaxStage) 1 else current.stage + 1
        val wrapped = next == 1
        loadStage(next, resetCampaign = wrapped)
    }

    /** Restarting after a loss costs a fresh entry fee and resets the campaign totals. */
    fun retryAfterLoss() {
        val current = _session.value
        if (current.phase != TanksPhase.GameOver) return
        if (!wallet.spend(TanksAttemptTokenCost)) {
            _messages.tryEmit(TanksMessage.NotEnoughTokens)
            return
        }
        val attempts = progress.recordAttempt()
        analytics.onAttemptStarted(attempts)
        _session.value = current.copy(charged = true)
        loadStage(current.stage, resetCampaign = true)
    }

    fun selectStage(stage: Int) {
        if (!isStageUnlocked(stage)) return
        _session.value = _session.value.copy(charged = false)
        loadStage(stage, resetCampaign = true)
    }

    /** Leaving mid-run forfeits the entry fee, so the screen asks first. */
    fun abandonRun() {
        _session.value = _session.value.copy(charged = false, phase = TanksPhase.Ready)
    }

    fun retryLoad() = loadStage(_session.value.stage, resetCampaign = true)

    fun isStageUnlocked(stage: Int): Boolean {
        if (stage !in 1..BattleCityMaxStage) return false
        if (allStagesUnlocked) return true
        return stage <= maxOf(1, _session.value.highestCompletedStage)
    }

    fun unlockedStage(): Int =
        if (allStagesUnlocked) BattleCityMaxStage else maxOf(1, _session.value.highestCompletedStage)

    fun stageInfo(stage: Int): BattleCityStageInfo? = _session.value.stageInfos[stage]

    // -------------------------------------------------------------------- ticking

    /** Advances the simulation. Returns the sounds the caller should play for this frame. */
    fun advance(deltaSeconds: Float, input: BattleCityInput): List<BattleCitySoundEvent> {
        if (_session.value.phase != TanksPhase.Playing) return emptyList()
        val current = engine ?: return emptyList()

        val step = current.step(deltaSeconds, input)
        _render.value = step.state

        when (step.state.status) {
            BattleCityStatus.Won -> onStageCleared(step.state)
            BattleCityStatus.Lost -> onRunLost()
            BattleCityStatus.Running -> Unit
        }
        return step.events
    }

    private fun onStageCleared(state: BattleCityRenderState) {
        val current = _session.value
        if (current.phase != TanksPhase.Playing) return

        val campaignScore = current.campaignScore + state.stageScore
        val highest = maxOf(current.highestCompletedStage, state.stageNumber)
        progress.saveHighestCompletedStage(state.stageNumber)
        val wins = progress.recordStageCleared()
        analytics.onStageCleared(state.stageNumber, wins, highest)

        _session.value = current.copy(
            phase = TanksPhase.StageCleared,
            campaignScore = campaignScore,
            carriedLives = state.lives,
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

    private fun onRunLost() {
        val current = _session.value
        if (current.phase != TanksPhase.Playing) return
        _session.value = current.copy(phase = TanksPhase.GameOver, charged = false)
    }

    private fun pointsForType(type: String): Int = when (type) {
        "fast" -> 200
        "power" -> 300
        "armor" -> 400
        else -> 100
    }
}
