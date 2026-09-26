package com.aectann.battlecity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aectann.battlecity.PlatformBackHandler
import com.aectann.battlecity.TanksAssets
import com.aectann.battlecity.TanksClip
import com.aectann.battlecity.TanksPhase
import com.aectann.battlecity.TanksPlatform
import com.aectann.battlecity.TanksResources
import com.aectann.battlecity.TanksStageSummary
import com.aectann.battlecity.TanksStrings
import com.aectann.battlecity.TanksAds
import com.aectann.battlecity.NoopTanksAds
import androidx.compose.ui.text.style.TextAlign
import com.aectann.battlecity.ResurrectionAdAvailability
import com.aectann.battlecity.ResurrectionAdResult
import com.aectann.battlecity.TanksViewModel
import com.aectann.battlecity.engine.BattleCityDirection
import com.aectann.battlecity.engine.BattleCityEffectKind
import com.aectann.battlecity.engine.BattleCityInput
import com.aectann.battlecity.engine.BattleCityInputs
import com.aectann.battlecity.engine.BattleCityMaxDifficulty
import com.aectann.battlecity.engine.BattleCityMaxStage
import com.aectann.battlecity.engine.BattleCityRenderState
import com.aectann.battlecity.engine.BattleCityStageInfo
import com.aectann.battlecity.engine.BattleCityTankRenderState
import com.aectann.battlecity.engine.BattleCityTileSnapshot
import com.aectann.battlecity.engine.TanksUpgrade
import com.aectann.battlecity.engine.TanksUpgradeLoadout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * How long the card between stages holds the board.
 *
 * The first one of a visit is the only place the controls are ever spelled out, and a second and
 * a half is not enough to read them — especially the co-op line, which names two key sets. Every
 * card after it is just a stage number the player already expects, so it keeps the original's
 * brisk beat rather than taxing every transition with reading time nobody needs twice.
 */
private const val FirstStageCardMillis = 3600L
private const val StageCardMillis = 1500L

/** The start/pause button is meaningless once a run has ended. */
private fun startButtonVisible(phase: TanksPhase): Boolean =
    phase == TanksPhase.Playing || phase == TanksPhase.Paused || phase == TanksPhase.Ready

/**
 * The game itself. The platform, view model and sprite set are supplied by the caller, so a
 * host app that embeds this game can plug in its own wallet and storage.
 */
@Composable
fun TanksGameScreen(
    platform: TanksPlatform,
    viewModel: TanksViewModel,
    assets: TanksAssets?,
    onExitToMenu: () -> Unit,
    ads: TanksAds = NoopTanksAds
) {
    val session by viewModel.session.collectAsState()
    val renderState by viewModel.render.collectAsState()
    val adsState by ads.state.collectAsState()

    val sound = remember { TanksSoundBank() }
    // One holder per seat. The on-screen pad only ever drives player one: co-op is a keyboard
    // feature, and two joysticks on one phone screen is not a control scheme.
    val heldInput = remember { TanksHeldInput() }
    val secondHeldInput = remember { TanksHeldInput() }
    val isCoop = session.isCoop
    // Per visit, not per run: someone who came back to the menu and started again has read the
    // controls already, but a fresh arrival on a portal page has not.
    var controlsCardShown by remember { mutableStateOf(false) }
    var animationFrame by remember { mutableIntStateOf(0) }
    var showStageSelector by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // The pause overlay's buttons take focus while they are on screen and keep it once they
    // leave, so the board asks for it back whenever it is the only thing left. Without this the
    // keyboard stops steering after the first tap on Pause.
    var boardHasFocus by remember { mutableStateOf(false) }
    // Ready counts as well as Playing: the stage card can be dismissed with a key, and a board
    // that has not been given focus yet would never see one.
    val boardWantsFocus =
        session.phase == TanksPhase.Playing || session.phase == TanksPhase.Ready
    LaunchedEffect(boardHasFocus, session.phase, showStageSelector) {
        if (!boardHasFocus && boardWantsFocus && !showStageSelector) {
            withFrameNanos { } // Let the overlay finish leaving the composition first.
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(platform) {
        val clips = TanksResources.loadSoundBank()
        sound.install(platform.createSoundPlayer(clips, session.soundEnabled))
    }

    LaunchedEffect(session.soundEnabled, session.resurrectionInProgress, adsState.fullScreenShowing, adsState.privacyOptionsBusy) {
        sound.setEnabled(session.soundEnabled && !session.resurrectionInProgress &&
            !adsState.fullScreenShowing && !adsState.privacyOptionsBusy)
    }

    DisposableEffect(sound) {
        onDispose { sound.release() }
    }

    DisposableEffect(platform) {
        platform.setImmersive(true)
        platform.setKeepAwake(true)
        onDispose {
            platform.setKeepAwake(false)
            platform.setImmersive(false)
        }
    }

    DisposableEffect(platform.portal) {
        onDispose {
            platform.portal.setGameplayActive(false)
            platform.portal.clearContext()
        }
    }

    LaunchedEffect(platform.portal, session.stage) {
        platform.portal.setStage(session.stage)
    }

    LaunchedEffect(platform.portal, session.phase) {
        platform.portal.setGameplayActive(session.phase == TanksPhase.Playing)
    }

    // The run must stop when the app leaves the foreground, or the player comes back dead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.pause()
                sound.setEngineRunning(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A run started before the sprites are in is played blind under the loading panel, so none
    // of the three ways off the stage card — its timer, a key, a tap — starts one until they are.
    val currentAssets by rememberUpdatedState(assets)
    val currentAdsState by rememberUpdatedState(adsState)
    val startRun = {
        if (currentAssets != null && !currentAdsState.fullScreenShowing && !currentAdsState.privacyOptionsBusy) {
            controlsCardShown = true
            viewModel.startOrResume()
        }
    }

    LaunchedEffect(session.phase, session.stage) {
        if (session.phase != TanksPhase.Playing) {
            heldInput.releaseAll()
            secondHeldInput.releaseAll()
            sound.setEngineRunning(false)
        }
        if (session.phase == TanksPhase.Ready) {
            // The stage card holds the board for a beat, then the run starts by itself,
            // the way the original does between stages. The first card of a visit holds longer
            // because it is carrying the controls; see FirstStageCardMillis.
            sound.play(TanksClip.StageStart)
            delay(if (controlsCardShown) StageCardMillis else FirstStageCardMillis)
            snapshotFlow { currentAssets != null }.first { it }
            startRun()
        }
    }

    LaunchedEffect(viewModel, session.phase, adsState.fullScreenShowing, adsState.privacyOptionsBusy) {
        if (session.phase != TanksPhase.Playing || adsState.fullScreenShowing || adsState.privacyOptionsBusy) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        // Counted here rather than read back out of animationFrame: a frame callback does not
        // see its own snapshot writes on every platform, and the sprite phase must keep moving.
        var frameCount = 0
        while (true) {
            val frame = withFrameNanos { it }
            val delta = ((frame - previousFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f)
            previousFrame = frame
            val direction = heldInput.direction
            val secondDirection = secondHeldInput.direction
            val events = viewModel.advance(
                deltaSeconds = delta,
                inputs = BattleCityInputs(
                    first = BattleCityInput(direction, heldInput.firePressed),
                    second = BattleCityInput(secondDirection, secondHeldInput.firePressed)
                )
            )
            events.forEach { sound.play(TanksClip.of(it)) }
            sound.setEngineRunning(direction != null || secondDirection != null)
            animationFrame = ++frameCount
        }
    }

    // Leaving for the menu abandons the run: nothing leads back into it, and the next entry
    // rebuilds from scratch. Worth a confirmation — but only once there is something to lose,
    // because asking on a run that has scored nothing is pure friction.
    var showExitConfirm by remember { mutableStateOf(false) }
    val runWorthKeeping = session.campaignScore + (renderState?.stageScore ?: 0) > 0 ||
        session.wave > 1 ||
        !session.loadout.isEmpty

    // One gate for both ways out, or the system back button would walk straight past the dialog.
    val requestExitToMenu: () -> Unit = {
        if (runWorthKeeping) showExitConfirm = true else onExitToMenu()
    }

    PlatformBackHandler(enabled = true) {
        when {
            session.resurrectionInProgress -> Unit
            showExitConfirm -> showExitConfirm = false
            session.phase == TanksPhase.Playing -> viewModel.pause()
            else -> requestExitToMenu()
        }
    }

    // Overlays sit above the whole screen rather than inside the board. In landscape the board
    // is only as tall as the window, and a pause menu drawn inside it loses its bottom rows.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .onPreviewKeyEvent { event ->
                val steering = directionForKey(event.key, isCoop)
                val fireSeat = fireSeatForKey(event.key, isCoop)
                val playing = session.phase == TanksPhase.Playing
                when {
                    // Any key dismisses the stage card. Checked before steering so that the very
                    // key a player reaches for — a direction — is the one that starts the run,
                    // instead of being swallowed while the card waits out its timer.
                    session.phase == TanksPhase.Ready && event.type == KeyEventType.KeyDown -> {
                        startRun()
                        true
                    }

                    steering != null -> {
                        val (seat, direction) = steering
                        val held = if (seat == 0) heldInput else secondHeldInput
                        if (event.type == KeyEventType.KeyDown && playing) {
                            held.pressKey(direction)
                        } else if (event.type == KeyEventType.KeyUp) {
                            held.releaseKey(direction)
                        }
                        true
                    }

                    fireSeat != null -> {
                        val held = if (fireSeat == 0) heldInput else secondHeldInput
                        held.setKeyboardFire(event.type == KeyEventType.KeyDown && playing)
                        true
                    }

                    event.key == Key.P -> {
                        if (event.type == KeyEventType.KeyUp) {
                            sound.play(TanksClip.MenuSelect)
                            viewModel.togglePause()
                        }
                        true
                    }

                    else -> false
                }
            }
            .onFocusChanged { state ->
                boardHasFocus = state.hasFocus
                if (!state.hasFocus) {
                    // Both seats: the browser sends no key-up for keys held when focus left, so
                    // whichever player was moving would otherwise drive into a wall forever.
                    heldInput.releaseKeyboard()
                    secondHeldInput.releaseKeyboard()
                }
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TanksHud(
                state = renderState,
                stage = session.stage,
                campaignScore = session.campaignScore,
                isCoop = isCoop,
                isEndless = session.isEndless,
                assets = assets
            )

            Spacer(modifier = Modifier.height(8.dp))

            val loadedAssets = assets
            when {
                session.phase == TanksPhase.Error -> TanksMessagePanel(
                    title = stringResource(TanksStrings.errorMessage, session.errorMessage.orEmpty()),
                    actionText = stringResource(TanksStrings.retry),
                    onAction = { viewModel.retryLoad() }
                )

                loadedAssets == null || renderState == null -> TanksLoadingPanel()

                else -> TanksPlayArea(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = renderState!!,
                    assets = loadedAssets,
                    animationFrame = animationFrame,
                    activeDirection = heldInput.visibleDirection,
                    isRunning = session.phase == TanksPhase.Playing,
                    showStartButton = startButtonVisible(session.phase),
                    // Re-read on every recomposition rather than remembered: the browser bridge
                    // only learns the device is touch when a finger actually lands, and the
                    // per-frame animation tick brings the answer in within a frame of that.
                    touchControls = platform.usesTouchControls,
                    isFirePressed = heldInput.visibleFirePressed,
                    onDirectionChanged = heldInput::setPointerDirection,
                    onFirePressedChanged = heldInput::setPointerFire,
                    onStartPause = {
                        sound.play(TanksClip.MenuSelect)
                        viewModel.togglePause()
                    }
                )
            }
        }

        when (session.phase) {
            TanksPhase.Ready -> TanksStageCard(
                stage = session.stage,
                isCoop = isCoop,
                isEndless = session.isEndless,
                // Starting the run flips the phase, which cancels the effect still counting the
                // card's hold down — so the skip needs no flag of its own.
                onSkip = startRun
            )

            TanksPhase.Paused -> TanksPauseOverlay(
                soundEnabled = session.soundEnabled,
                onResume = {
                    sound.play(TanksClip.MenuSelect)
                    viewModel.togglePause()
                },
                onRestartStage = {
                    sound.play(TanksClip.MenuSelect)
                    viewModel.restartStage()
                },
                onOpenStages = {
                    sound.play(TanksClip.MenuSelect)
                    showStageSelector = true
                },
                onSoundToggled = { viewModel.setSoundEnabled(it) },
                onExitToMenu = requestExitToMenu
            )

            TanksPhase.StageCleared -> session.summary?.let { summary ->
                TanksSummaryOverlay(summary) {
                    sound.play(TanksClip.MenuSelect)
                    viewModel.advanceToNextStage()
                }
            }

            TanksPhase.WaveCleared -> TanksUpgradePicker(
                wave = session.wave,
                choices = session.upgradeChoices,
                loadout = session.loadout,
                onChoose = { upgrade ->
                    sound.play(TanksClip.MenuSelect)
                    viewModel.chooseUpgrade(upgrade)
                }
            )

            TanksPhase.GameOver -> TanksGameOverOverlay(
                campaignScore = session.campaignScore + (renderState?.stageScore ?: 0),
                endlessWave = session.wave.takeIf { session.isEndless },
                canResurrect = session.canResurrect,
                isCoop = session.isCoop,
                adAvailability = adsState.resurrection,
                resurrectionInProgress = session.resurrectionInProgress,
                resurrectionResult = session.resurrectionResult,
                onResurrect = { viewModel.resurrectWithAd(ads) },
                onRetry = {
                    sound.play(TanksClip.MenuSelect)
                    viewModel.retryAfterLoss()
                },
                onExitToMenu = onExitToMenu
            )

            else -> Unit
        }
    }

    if (showStageSelector) {
        TanksStageSelectorDialog(
            selectedStage = session.stage,
            stageInfos = session.stageInfos,
            highestCompletedStage = session.highestCompletedStage,
            unlockedStage = viewModel.unlockedStage(),
            allStagesUnlocked = viewModel.isStageUnlocked(BattleCityMaxStage),
            isUnlocked = viewModel::isStageUnlocked,
            onStageSelected = { stage ->
                sound.play(TanksClip.MenuSelect)
                viewModel.selectStage(stage)
                showStageSelector = false
            },
            onDismiss = { showStageSelector = false }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(TanksStrings.pauseExitTitle)) },
            text = { Text(stringResource(TanksStrings.pauseExitQuestion)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    sound.play(TanksClip.MenuSelect)
                    onExitToMenu()
                }) { Text(stringResource(TanksStrings.pauseExitConfirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(TanksStrings.commonCancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------- HUD

@Composable
private fun TanksHud(
    state: BattleCityRenderState?,
    stage: Int,
    campaignScore: Int,
    isCoop: Boolean,
    isEndless: Boolean,
    assets: TanksAssets?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Solo shows one strip of lives. In co-op a combined count hides the thing that
            // actually matters to two people sharing a keyboard — whose lives are running out —
            // so each seat gets its own strip in its own tank colour.
            if (isCoop) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state?.players?.forEachIndexed { index, player ->
                        if (index > 0) Spacer(modifier = Modifier.width(10.dp))
                        SeatLives(
                            seat = player.index,
                            lives = player.lives,
                            icon = assets?.lifeIcon(),
                            colour = if (player.index == 0) PlayerGreen else PlayerYellow
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconStrip(assets?.lifeIcon(), state?.lives ?: 0, PlayerGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "x${state?.lives ?: 0}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteIcon(assets?.flagIcon(), 18.dp, AccentGold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    // Endless has no stage number to show; the wave is the thing that climbs.
                    text = if (isEndless) {
                        stringResource(TanksStrings.wave, state?.wave ?: 1)
                    } else {
                        stringResource(TanksStrings.stage, stage)
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(
                    TanksStrings.score,
                    campaignScore + (state?.stageScore ?: 0)
                ),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconStrip(assets?.enemyQueueIcon(), state?.enemiesPending ?: 0, MutedText, maxIcons = 10)
            Text(
                text = stringResource(
                    TanksStrings.enemies,
                    state?.destroyedEnemies ?: 0,
                    state?.totalEnemies ?: 0
                ),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = stringResource(TanksStrings.tankLevel, state?.playerLevel ?: 1),
                color = PlayerGreen,
                fontSize = 12.sp
            )
        }

        // The build is only worth a line once there is one, so a campaign HUD never shows it.
        val taken = state?.loadout?.taken().orEmpty()
        if (taken.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            // Resolved through `map`, which is inline and so may call stringResource; the
            // joining happens afterwards on plain strings.
            val labels = taken.map { (upgrade, level) ->
                stringResource(TanksStrings.upgradeName(upgrade)) + " " + level
            }
            Text(
                text = labels.joinToString("  ·  "),
                color = AccentGold,
                fontSize = 11.sp
            )
        }
    }
}

/** One seat's lives, labelled so two players can tell the strips apart at a glance. */
@Composable
private fun SeatLives(seat: Int, lives: Int, icon: ImageBitmap?, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(TanksStrings.playerSeat, seat + 1),
            color = colour,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconStrip(icon, lives, colour, maxIcons = 3)
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = "x$lives", color = Color.White, fontSize = 12.sp)
    }
}


@Composable
private fun IconStrip(image: ImageBitmap?, count: Int, tint: Color, maxIcons: Int = 5) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(maxIcons)) {
            SpriteIcon(image, 14.dp, tint)
            Spacer(modifier = Modifier.width(2.dp))
        }
        if (count > maxIcons) {
            Text(text = "+${count - maxIcons}", color = tint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpriteIcon(image: ImageBitmap?, size: Dp, tint: Color) {
    if (image == null) {
        Box(modifier = Modifier.size(size).background(tint, RoundedCornerShape(2.dp)))
        return
    }
    Canvas(modifier = Modifier.size(size)) {
        drawImage(
            image = image,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            filterQuality = FilterQuality.None
        )
    }
}

// --------------------------------------------------------------------- play area

/**
 * Landscape puts steering under the left thumb and the trigger under the right, with the board
 * between them. Portrait stacks the board over a control strip that keeps the same handedness.
 */
@Composable
private fun TanksPlayArea(
    modifier: Modifier,
    state: BattleCityRenderState,
    assets: TanksAssets,
    animationFrame: Int,
    activeDirection: BattleCityDirection?,
    isRunning: Boolean,
    showStartButton: Boolean,
    /** Draw the on-screen stick and trigger. False on a device that steers with keys. */
    touchControls: Boolean,
    isFirePressed: Boolean,
    onDirectionChanged: (BattleCityDirection?) -> Unit,
    onFirePressedChanged: (Boolean) -> Unit,
    onStartPause: () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val isLandscape = maxWidth > maxHeight
        val spacing = 12.dp

        if (isLandscape) {
            // Without the stick and trigger the flanks only have to hold a pause button, so the
            // board takes the width they were using — which is most of the point of hiding them.
            val sideWidth = when {
                !touchControls -> 96.dp
                maxWidth < 700.dp -> 150.dp
                else -> 176.dp
            }
            val boardSize = minOf(
                maxHeight,
                (maxWidth - sideWidth * 2 - spacing * 2).coerceAtLeast(160.dp)
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (touchControls) {
                    DirectionJoystick(
                        activeDirection = activeDirection,
                        modifier = Modifier.size(sideWidth),
                        onDirectionChanged = onDirectionChanged
                    )
                } else {
                    // Balances the pause column opposite. Dropping the stick without this leaves
                    // the board sitting off-centre, which reads as a layout bug rather than as
                    // one fewer control.
                    Spacer(modifier = Modifier.width(sideWidth))
                }
                TanksBoard(
                    modifier = Modifier.size(boardSize),
                    state = state,
                    assets = assets,
                    animationFrame = animationFrame
                )
                TanksActionPanel(
                    modifier = Modifier
                        .width(sideWidth)
                        .height(if (touchControls) sideWidth + 40.dp else 72.dp),
                    isRunning = isRunning,
                    showStartButton = showStartButton,
                    showFire = touchControls,
                    isFirePressed = isFirePressed,
                    onFirePressedChanged = onFirePressedChanged,
                    onStartPause = onStartPause
                )
            }
        } else {
            val controlsHeight = when {
                !touchControls -> 64.dp
                maxHeight < 580.dp -> 170.dp
                else -> 192.dp
            }
            val joystickSize = if (maxHeight < 580.dp) 140.dp else 152.dp
            val boardSize = minOf(maxWidth, (maxHeight - controlsHeight - spacing).coerceAtLeast(160.dp))
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically)
            ) {
                TanksBoard(
                    modifier = Modifier.size(boardSize),
                    state = state,
                    assets = assets,
                    animationFrame = animationFrame
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(controlsHeight).padding(8.dp),
                    horizontalArrangement = if (touchControls) {
                        Arrangement.SpaceBetween
                    } else {
                        // One child left, so it centres instead of hugging the left edge.
                        Arrangement.Center
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (touchControls) {
                        DirectionJoystick(
                            activeDirection = activeDirection,
                            modifier = Modifier.size(joystickSize),
                            onDirectionChanged = onDirectionChanged
                        )
                    }
                    TanksActionPanel(
                        modifier = Modifier.width(150.dp).height(controlsHeight - 16.dp),
                        isRunning = isRunning,
                        showStartButton = showStartButton,
                        showFire = touchControls,
                        isFirePressed = isFirePressed,
                        onFirePressedChanged = onFirePressedChanged,
                        onStartPause = onStartPause
                    )
                }
            }
        }
    }
}

@Composable
private fun TanksBoard(
    modifier: Modifier,
    state: BattleCityRenderState,
    assets: TanksAssets,
    animationFrame: Int
) {
    Canvas(modifier = modifier.aspectRatio(1f).background(Color.Black)) {
        val snapshot = state.tiles
        val boardPx = size.minDimension
        val tileSize = boardPx / maxOf(snapshot.cols, snapshot.rows)
        val offset = Offset(
            (size.width - tileSize * snapshot.cols) / 2f,
            (size.height - tileSize * snapshot.rows) / 2f
        )
        val frame = animationFrame / 24

        drawRect(
            color = BoardBackground,
            topLeft = offset,
            size = Size(tileSize * snapshot.cols, tileSize * snapshot.rows)
        )

        drawTiles(snapshot, assets, frame, tileSize, offset, forestPass = false)

        state.powerUps.forEach { powerUp ->
            if (!powerUp.visible) return@forEach
            assets.powerUp(powerUp.type)?.let { image ->
                drawSprite(image, powerUp.x, powerUp.y, 1f, tileSize, offset)
            }
        }

        state.players.forEach { player ->
            player.tank?.let { drawTank(it, assets, tileSize, offset) }
        }
        state.enemies.forEach { drawTank(it, assets, tileSize, offset) }

        state.bullets.forEach { bullet ->
            assets.bullet(bullet.direction)?.let { image ->
                val bulletSize = (tileSize * 0.42f).roundToInt().coerceAtLeast(1)
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        (offset.x + bullet.x * tileSize - bulletSize / 2f).roundToInt(),
                        (offset.y + bullet.y * tileSize - bulletSize / 2f).roundToInt()
                    ),
                    dstSize = IntSize(bulletSize, bulletSize),
                    filterQuality = FilterQuality.None
                )
            }
        }

        drawTiles(snapshot, assets, frame, tileSize, offset, forestPass = true)

        state.effects.forEach { effect ->
            val image = when (effect.kind) {
                BattleCityEffectKind.Explosion -> assets.explosion(effect.frame)
                BattleCityEffectKind.Spawn -> assets.spawn(effect.frame)
            } ?: return@forEach
            drawSprite(image, effect.x, effect.y, effect.sizeCells, tileSize, offset)
        }
    }
}

private fun DrawScope.drawTiles(
    snapshot: BattleCityTileSnapshot,
    assets: TanksAssets,
    frame: Int,
    tileSize: Float,
    offset: Offset,
    forestPass: Boolean
) {
    val brick = assets.brick()
    for (y in 0 until snapshot.rows) {
        for (x in 0 until snapshot.cols) {
            val tile = snapshot.tileAt(x, y)
            if (tile == '.') continue
            if ((tile == 'F') != forestPass) continue

            if (tile == 'B' && brick != null) {
                drawBrickCell(brick, snapshot.quartersAt(x, y), x, y, tileSize, offset)
                continue
            }

            assets.tile(tile, frame, snapshot)?.let { image ->
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        (offset.x + x * tileSize).roundToInt(),
                        (offset.y + y * tileSize).roundToInt()
                    ),
                    dstSize = IntSize(
                        ceil(tileSize).toInt().coerceAtLeast(1),
                        ceil(tileSize).toInt().coerceAtLeast(1)
                    ),
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}

/** Draws only the surviving quarters of a brick cell, which is what makes tunnels visible. */
private fun DrawScope.drawBrickCell(
    brick: ImageBitmap,
    mask: Int,
    cellX: Int,
    cellY: Int,
    tileSize: Float,
    offset: Offset
) {
    if (mask == 0) return
    val half = tileSize / 2f
    val srcHalfWidth = (brick.width / 2).coerceAtLeast(1)
    val srcHalfHeight = (brick.height / 2).coerceAtLeast(1)
    val destSize = IntSize(
        ceil(half).toInt().coerceAtLeast(1),
        ceil(half).toInt().coerceAtLeast(1)
    )

    for (quarterY in 0..1) {
        for (quarterX in 0..1) {
            if (mask and (1 shl (quarterY * 2 + quarterX)) == 0) continue
            drawImage(
                image = brick,
                srcOffset = IntOffset(quarterX * srcHalfWidth, quarterY * srcHalfHeight),
                srcSize = IntSize(srcHalfWidth, srcHalfHeight),
                dstOffset = IntOffset(
                    (offset.x + cellX * tileSize + quarterX * half).roundToInt(),
                    (offset.y + cellY * tileSize + quarterY * half).roundToInt()
                ),
                dstSize = destSize,
                filterQuality = FilterQuality.None
            )
        }
    }
}

private fun DrawScope.drawTank(
    tank: BattleCityTankRenderState,
    assets: TanksAssets,
    tileSize: Float,
    offset: Offset
) {
    assets.tank(tank)?.let { drawSprite(it, tank.x, tank.y, 1f, tileSize, offset) }
    if (tank.hasShield) {
        assets.shield(tank.shieldFrame)?.let { drawSprite(it, tank.x, tank.y, 1f, tileSize, offset) }
    }
}

private fun DrawScope.drawSprite(
    image: ImageBitmap,
    cellX: Float,
    cellY: Float,
    sizeCells: Float,
    tileSize: Float,
    offset: Offset
) {
    val pixels = ceil(tileSize * sizeCells).toInt().coerceAtLeast(1)
    val centerOffset = (sizeCells - 1f) / 2f
    drawImage(
        image = image,
        dstOffset = IntOffset(
            (offset.x + (cellX - centerOffset) * tileSize).roundToInt(),
            (offset.y + (cellY - centerOffset) * tileSize).roundToInt()
        ),
        dstSize = IntSize(pixels, pixels),
        filterQuality = FilterQuality.None
    )
}

// ----------------------------------------------------------------------- overlays

/** Shared chrome so every full-screen overlay scrolls rather than clipping on short windows. */
@Composable
private fun FullScreenOverlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE6000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

/**
 * The grey card the original shows between stages, covering the field while it is built.
 *
 * Tapping it starts the run immediately. The card is the only place the controls are written
 * down, so it has to hold long enough to read — and a hold nobody can shorten is exactly the
 * forced delay the platform's quality guidelines tell you to remove. Being skippable is what
 * lets it be generous.
 */
@Composable
private fun TanksStageCard(stage: Int, isCoop: Boolean, isEndless: Boolean, onSkip: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF6B6B6B))
            .pointerInput(Unit) { detectTapGestures { onSkip() } },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isEndless) {
                    stringResource(TanksStrings.wave, 1)
                } else {
                    stringResource(TanksStrings.stage, stage)
                },
                color = Color(0xFF1A1A1A),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                // This card is the only place a second player is told which keys are theirs, so
                // it has to name both seats when there are two.
                text = if (isCoop) {
                    stringResource(TanksStrings.controlsHintCoop)
                } else {
                    stringResource(TanksStrings.controlsHint)
                },
                color = Color(0xFF242424),
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Which seat a steering key belongs to, and where it points.
 *
 * Solo, both key sets drive the one tank — a player should not have to discover which half of
 * the keyboard the game wants. In co-op they split the conventional way for a shared keyboard:
 * WASD on the left, arrows on the right, so the two players are not reaching over each other.
 *
 * `Z` and `Q` are the AZERTY positions of `W` and `A`; CrazyGames asks for that mapping because
 * a French keyboard would otherwise have no left hand controls at all.
 */
private fun directionForKey(key: Key, isCoop: Boolean): Pair<Int, BattleCityDirection>? {
    val wasd = when (key) {
        Key.W, Key.Z -> BattleCityDirection.Up
        Key.S -> BattleCityDirection.Down
        Key.A, Key.Q -> BattleCityDirection.Left
        Key.D -> BattleCityDirection.Right
        else -> null
    }
    if (wasd != null) return 0 to wasd

    val arrows = when (key) {
        Key.DirectionUp -> BattleCityDirection.Up
        Key.DirectionDown -> BattleCityDirection.Down
        Key.DirectionLeft -> BattleCityDirection.Left
        Key.DirectionRight -> BattleCityDirection.Right
        else -> null
    } ?: return null

    return (if (isCoop) 1 else 0) to arrows
}

/**
 * Which seat a fire key belongs to, or null when the key does not fire.
 *
 * Space stays player one's in co-op and both players' solo. Player two gets Enter and the
 * numpad's zero — the keys that fall under the right hand already resting on the arrows.
 */
private fun fireSeatForKey(key: Key, isCoop: Boolean): Int? = when (key) {
    Key.Spacebar -> 0
    Key.Enter, Key.NumPadEnter, Key.NumPad0 -> if (isCoop) 1 else 0
    else -> null
}

/**
 * The between-waves pick.
 *
 * Shown over a board the engine has frozen, so the cards can sit above the arena the player is
 * about to go back into rather than replacing it. Skip is always available: a run that wants
 * nothing on offer should not be forced to take the least bad card.
 */
@Composable
private fun TanksUpgradePicker(
    wave: Int,
    choices: List<TanksUpgrade>,
    loadout: TanksUpgradeLoadout,
    onChoose: (TanksUpgrade?) -> Unit
) {
    FullScreenOverlay {
        Text(
            text = stringResource(TanksStrings.upgradeTitle, wave),
            color = AccentGold,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(TanksStrings.upgradeSubtitle),
            color = MutedText,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        choices.forEach { upgrade ->
            UpgradeCard(
                upgrade = upgrade,
                currentLevel = loadout.levelOf(upgrade),
                onClick = { onChoose(upgrade) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { onChoose(null) }) {
            Text(stringResource(TanksStrings.upgradeSkip), color = MutedText)
        }
    }
}

@Composable
private fun UpgradeCard(
    upgrade: TanksUpgrade,
    currentLevel: Int,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 260.dp).fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(TanksStrings.upgradeName(upgrade)),
                    color = AccentGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                // What a card is worth depends on what the run already holds, so the level it
                // would move to is on the card rather than buried in the HUD.
                Text(
                    text = if (currentLevel + 1 >= upgrade.maxLevel) {
                        stringResource(TanksStrings.upgradeMax)
                    } else {
                        stringResource(TanksStrings.upgradeLevel, currentLevel + 1)
                    },
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(TanksStrings.upgradeDescription(upgrade)),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TanksPauseOverlay(
    soundEnabled: Boolean,
    onResume: () -> Unit,
    onRestartStage: () -> Unit,
    onOpenStages: () -> Unit,
    onSoundToggled: (Boolean) -> Unit,
    onExitToMenu: () -> Unit
) {
    FullScreenOverlay {
        Text(
            text = stringResource(TanksStrings.pause),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        val buttonWidth = Modifier.widthIn(min = 240.dp)

        Button(onClick = onResume, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.resume))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRestartStage, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.restart))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOpenStages, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.selectStage))
        }
        Spacer(modifier = Modifier.height(8.dp))
        // A labelled button rather than a bare switch: on a dark board the switch was easy to
        // miss and its state was not obvious at a glance.
        OutlinedButton(onClick = { onSoundToggled(!soundEnabled) }, modifier = buttonWidth) {
            Text(
                stringResource(TanksStrings.sound) + ": " +
                    stringResource(if (soundEnabled) TanksStrings.commonOn else TanksStrings.commonOff)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onExitToMenu, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.commonBack))
        }
    }
}

@Composable
private fun TanksSummaryOverlay(summary: TanksStageSummary, onNextStage: () -> Unit) {
    FullScreenOverlay {
        Text(
            text = stringResource(TanksStrings.summaryTitle, summary.stage),
            color = AccentGold,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))

        summary.kills.forEach { row ->
            Row(
                modifier = Modifier.widthIn(min = 260.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(TanksStrings.enemyName(row.type)),
                    color = Color.White,
                    fontSize = 13.sp
                )
                Text(
                    text = "${row.count} x = ${row.points}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))
        SummaryRow(stringResource(TanksStrings.summaryStagePoints), summary.stageScore)
        SummaryRow(stringResource(TanksStrings.summaryTotalPoints), summary.campaignScore)
        SummaryRow(stringResource(TanksStrings.summaryLivesLeft), summary.livesLeft)

        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onNextStage, modifier = Modifier.widthIn(min = 240.dp)) {
            Text(stringResource(TanksStrings.nextStage))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(
        modifier = Modifier.widthIn(min = 260.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MutedText, fontSize = 13.sp)
        Text(text = value.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(3.dp))
}

@Composable
private fun TanksGameOverOverlay(
    campaignScore: Int,
    endlessWave: Int?,
    canResurrect: Boolean,
    isCoop: Boolean,
    adAvailability: ResurrectionAdAvailability,
    resurrectionInProgress: Boolean,
    resurrectionResult: ResurrectionAdResult?,
    onResurrect: () -> Unit,
    onRetry: () -> Unit,
    onExitToMenu: () -> Unit
) {
    FullScreenOverlay {
        Text(
            text = stringResource(
                if (endlessWave != null) TanksStrings.endlessOverTitle else TanksStrings.gameOver
            ),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        if (endlessWave != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(TanksStrings.endlessOverWave, endlessWave),
                color = AccentGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(TanksStrings.score, campaignScore),
            color = MutedText,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        if (canResurrect) {
            Text(
                stringResource(if (isCoop) TanksStrings.resurrectionOfferCoop else TanksStrings.resurrectionOffer),
                color = MutedText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onResurrect,
                enabled = adAvailability == ResurrectionAdAvailability.Ready && !resurrectionInProgress,
                modifier = Modifier.widthIn(min = 240.dp)
            ) {
                Text(stringResource(if (resurrectionInProgress) TanksStrings.resurrectionWatching else TanksStrings.resurrectionWatch))
            }
            val message = when {
                resurrectionInProgress -> null
                resurrectionResult == ResurrectionAdResult.NotEarned -> TanksStrings.resurrectionNotEarned
                resurrectionResult == ResurrectionAdResult.Failed -> TanksStrings.resurrectionFailed
                adAvailability == ResurrectionAdAvailability.Loading -> TanksStrings.resurrectionLoading
                adAvailability == ResurrectionAdAvailability.Unavailable -> TanksStrings.resurrectionUnavailable
                else -> null
            }
            if (message != null) {
                Text(stringResource(message), color = MutedText, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onRetry, enabled = !resurrectionInProgress, modifier = Modifier.widthIn(min = 240.dp)) {
            Text(stringResource(TanksStrings.playAgain))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onExitToMenu, enabled = !resurrectionInProgress, modifier = Modifier.widthIn(min = 240.dp)) {
            Text(stringResource(TanksStrings.commonBack))
        }
    }
}

@Composable
private fun TanksLoadingPanel() {
    Box(
        modifier = Modifier.fillMaxSize().background(ScreenBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(TanksStrings.loading), color = Color.White)
        }
    }
}

@Composable
private fun TanksMessagePanel(title: String, actionText: String, onAction: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(ScreenBackground).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}

// ----------------------------------------------------------------------- controls

@Composable
private fun DirectionJoystick(
    activeDirection: BattleCityDirection?,
    modifier: Modifier,
    onDirectionChanged: (BattleCityDirection?) -> Unit
) {
    GlassPanel(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Reacts on touch down, not only after the drag slop, so a tap steers too.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onDirectionChanged(directionFromJoystickPosition(down.position, size))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            onDirectionChanged(directionFromJoystickPosition(change.position, size))
                            change.consume()
                        }
                        onDirectionChanged(null)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(joystickKnobAlignment(activeDirection))
                    .padding(22.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.36f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.50f), CircleShape)
            )
        }
    }
}

/**
 * Pause and fire.
 *
 * Fire belongs to touch and disappears with the rest of the pad. Pause does not: it is the only
 * visible way to stop a run on any device, and the `P` shortcut is only ever spelled out on the
 * stage card, which is gone a second and a half in.
 */
@Composable
private fun TanksActionPanel(
    modifier: Modifier,
    isRunning: Boolean,
    showStartButton: Boolean,
    showFire: Boolean,
    isFirePressed: Boolean,
    onFirePressedChanged: (Boolean) -> Unit,
    onStartPause: () -> Unit
) {
    // With neither button there is nothing to frame, and an empty glass panel floating beside
    // the board looks like a rendering fault.
    if (!showStartButton && !showFire) return

    GlassPanel(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val compact = maxHeight < 160.dp
            val fireSize = if (compact) 84.dp else 94.dp
            val buttonGap = if (compact) 10.dp else 16.dp
            val startButtonHeight = if (compact) 36.dp else 40.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(buttonGap, Alignment.CenterVertically)
            ) {
                if (showStartButton) {
                    ControlTapButton(
                        text = if (isRunning) {
                            stringResource(TanksStrings.pause)
                        } else {
                            stringResource(TanksStrings.start)
                        },
                        modifier = Modifier.size(94.dp, startButtonHeight),
                        onClick = onStartPause
                    )
                }
                if (showFire) {
                    ControlHoldButton(
                        text = stringResource(TanksStrings.fire),
                        isActive = isFirePressed,
                        modifier = Modifier.size(fireSize),
                        shape = CircleShape,
                        color = Color(0xFFD13D2F).copy(alpha = 0.82f),
                        activeColor = Color(0xFFFF5A45).copy(alpha = 0.92f),
                        onPressedChanged = onFirePressedChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
            .padding(8.dp)
    ) { content() }
}

@Composable
private fun ControlHoldButton(
    text: String,
    isActive: Boolean,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color = Color.White.copy(alpha = 0.14f),
    activeColor: Color = Color.White.copy(alpha = 0.34f),
    onPressedChanged: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .background(color = if (isActive) activeColor else color, shape = shape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .pointerInput(text) {
                detectTapGestures(
                    onPress = {
                        onPressedChanged(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            onPressedChanged(false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ControlTapButton(
    text: String,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color = Color.White.copy(alpha = 0.16f),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(color, shape)
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            .pointerInput(text) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun directionFromJoystickPosition(position: Offset, size: IntSize): BattleCityDirection? {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val dx = position.x - centerX
    val dy = position.y - centerY
    val threshold = minOf(size.width, size.height) * 0.12f

    if (abs(dx) < threshold && abs(dy) < threshold) return null

    return if (abs(dx) > abs(dy)) {
        if (dx > 0f) BattleCityDirection.Right else BattleCityDirection.Left
    } else {
        if (dy > 0f) BattleCityDirection.Down else BattleCityDirection.Up
    }
}

private fun joystickKnobAlignment(direction: BattleCityDirection?): Alignment = when (direction) {
    BattleCityDirection.Up -> Alignment.TopCenter
    BattleCityDirection.Right -> Alignment.CenterEnd
    BattleCityDirection.Down -> Alignment.BottomCenter
    BattleCityDirection.Left -> Alignment.CenterStart
    null -> Alignment.Center
}

// ------------------------------------------------------------------------ dialogs

@Composable
private fun TanksStageSelectorDialog(
    selectedStage: Int,
    stageInfos: Map<Int, BattleCityStageInfo>,
    highestCompletedStage: Int,
    unlockedStage: Int,
    allStagesUnlocked: Boolean,
    isUnlocked: (Int) -> Boolean,
    onStageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(TanksStrings.selectStageTitle)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (allStagesUnlocked) {
                        stringResource(TanksStrings.allStagesAvailable)
                    } else {
                        stringResource(TanksStrings.highestCompletedStage, highestCompletedStage)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                (1..BattleCityMaxStage).chunked(4).forEach { rowStages ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowStages.forEach { stage ->
                            StageTile(
                                modifier = Modifier.weight(1f),
                                stage = stage,
                                info = stageInfos[stage],
                                isSelected = stage == selectedStage,
                                isAvailable = isUnlocked(stage),
                                onClick = { onStageSelected(stage) }
                            )
                        }
                        repeat(4 - rowStages.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }

                if (!allStagesUnlocked && unlockedStage < BattleCityMaxStage) {
                    Text(
                        text = stringResource(TanksStrings.lockedStagesHint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(TanksStrings.close)) }
        }
    )
}

@Composable
private fun StageTile(
    modifier: Modifier,
    stage: Int,
    info: BattleCityStageInfo?,
    isSelected: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> AccentGold
        isAvailable -> Color.White.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Column(
        modifier = modifier
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(4.dp)
            .then(
                if (isAvailable) {
                    Modifier.pointerInput(stage) { detectTapGestures(onTap = { onClick() }) }
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StageMiniMap(
            grid = info?.grid.orEmpty(),
            dimmed = !isAvailable,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )
        Text(
            text = stage.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isAvailable) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
        Text(
            text = difficultyStars(info?.difficulty ?: 1),
            fontSize = 9.sp,
            color = if (isAvailable) AccentGold else Color.Gray
        )
    }
}

/** The thumbnail is drawn from the level data itself, so it can never fall out of sync. */
@Composable
private fun StageMiniMap(grid: List<String>, dimmed: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0C0C0C), RoundedCornerShape(3.dp))) {
        if (grid.isEmpty()) return@Canvas
        val rows = grid.size
        val cols = grid[0].length
        if (cols == 0) return@Canvas
        val cell = minOf(size.width / cols, size.height / rows)
        val originX = (size.width - cell * cols) / 2f
        val originY = (size.height - cell * rows) / 2f
        val alpha = if (dimmed) 0.35f else 1f

        grid.forEachIndexed { y, row ->
            row.forEachIndexed { x, tile ->
                val color = when (tile) {
                    'B' -> BrickRed
                    'S' -> Color(0xFFC9C9C9)
                    'W' -> Color(0xFF2A4FB4)
                    'F' -> Color(0xFF2E8B34)
                    'I' -> Color(0xFFBFE3EC)
                    'H' -> AccentGold
                    else -> null
                } ?: return@forEachIndexed
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(originX + x * cell, originY + y * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}

private fun difficultyStars(difficulty: Int): String {
    val filled = difficulty.coerceIn(1, BattleCityMaxDifficulty)
    return "[" + "*".repeat(filled) + "-".repeat(BattleCityMaxDifficulty - filled) + "]"
}
