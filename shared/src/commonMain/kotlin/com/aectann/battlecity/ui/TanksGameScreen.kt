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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.KeyEvent
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
import com.aectann.battlecity.RewardedAdAvailability
import com.aectann.battlecity.RewardedAdResult
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

/**
 * Which keys are down, and which of them were already down when the run stopped for an overlay.
 *
 * Those belong to the run. A trigger still held as the stage ends keeps auto-repeating, and each
 * repeat is a fresh key-down: left alone, one would press whatever button the overlay opened on.
 * Plain fields, like the held input — only the key handler reads them, and it notices the switch
 * to an overlay itself, before counting the event that arrived, so not even the first repeat
 * slips through.
 */
private class RunKeys {
    private val down = mutableSetOf<Key>()
    private val fromRun = mutableSetOf<Key>()
    private var overlay = false

    fun track(event: KeyEvent, overlayUp: Boolean) {
        if (overlayUp != overlay) {
            overlay = overlayUp
            fromRun.clear()
            if (overlayUp) fromRun.addAll(down)
        }
        if (event.type == KeyEventType.KeyDown) down += event.key else if (event.type == KeyEventType.KeyUp) down -= event.key
    }

    /** True for a key held over from the run; it stops counting as one once released. */
    fun heldOverFromRun(event: KeyEvent): Boolean {
        if (event.key !in fromRun) return false
        if (event.type == KeyEventType.KeyUp) fromRun -= event.key
        return true
    }
}

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

    // The app's shared player when there is one; a host that embeds only this screen gets one
    // owned here, loaded and released with the screen as before.
    val appSound = LocalTanksSound.current
    val sound = appSound ?: remember { TanksSoundBank() }
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

    if (appSound == null) {
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
    } else {
        // The shared player outlives the screen; only the engine loop has to stop with it.
        DisposableEffect(sound) {
            onDispose { sound.setEngineRunning(false) }
        }
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

    val menuPhase = session.phase != TanksPhase.Playing && session.phase != TanksPhase.Ready
    val runKeys = remember { RunKeys() }

    CompositionLocalProvider(LocalTanksSound provides sound) {
        // Overlays sit above the whole screen rather than inside the board. In landscape the board
        // is only as tall as the window, and a pause menu drawn inside it loses its bottom rows.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBackground)
                .onPreviewKeyEvent { event ->
                    runKeys.track(event, menuPhase)
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

                        // A key held over from the run is swallowed until it is let go.
                        runKeys.heldOverFromRun(event) -> true

                        // With an overlay up the keys are the overlay's: arrows and WASD move its
                        // cursor, Enter and Space press its buttons (pixelMenuKeys, PixelBlockButton).
                        menuPhase && event.key != Key.P -> false

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
            // The fortress wall the board is set into, on the board's own grid (see TanksPlayfield).
            BoxWithConstraints(Modifier.fillMaxSize()) {
                BrickWall(assets = assets, fieldSide = minOf(maxWidth, maxHeight), modifier = Modifier.fillMaxSize(), dim = 0.84f)
            }

            val loadedAssets = assets
            val state = renderState
            when {
                session.phase == TanksPhase.Error -> TanksMessagePanel(
                    title = stringResource(TanksStrings.errorMessage, session.errorMessage.orEmpty()),
                    actionText = stringResource(TanksStrings.retry),
                    onAction = { viewModel.retryLoad() }
                )

                loadedAssets == null || state == null -> TanksLoadingPanel()

                else -> TanksPlayfield(
                    modifier = Modifier.fillMaxSize().safeContentPadding().padding(10.dp),
                    state = state,
                    assets = loadedAssets,
                    animationFrame = animationFrame,
                    stage = session.stage,
                    campaignScore = session.campaignScore,
                    isCoop = isCoop,
                    isEndless = session.isEndless,
                    activeDirection = heldInput.visibleDirection,
                    isRunning = session.phase == TanksPhase.Playing,
                    showStartButton = startButtonVisible(session.phase),
                    // Re-read on every recomposition rather than remembered: the browser bridge
                    // only learns the device is touch when a finger actually lands, and the
                    // per-frame animation tick brings the answer in within a frame of that.
                    touchControls = platform.usesTouchControls,
                    keyboardHints = platform.hasPhysicalKeyboard,
                    isFirePressed = heldInput.visibleFirePressed,
                    onDirectionChanged = heldInput::setPointerDirection,
                    onFirePressedChanged = heldInput::setPointerFire,
                    // The pause button clicks for itself (PixelBlockButton).
                    onStartPause = { viewModel.togglePause() }
                )
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

                // The overlays' buttons click for themselves (PixelBlockButton).
                TanksPhase.Paused -> TanksPauseOverlay(
                    soundEnabled = session.soundEnabled,
                    onResume = { viewModel.togglePause() },
                    onRestartStage = { viewModel.restartStage() },
                    onOpenStages = { showStageSelector = true },
                    onSoundToggled = { viewModel.setSoundEnabled(it) },
                    onExitToMenu = requestExitToMenu,
                    // Closing the stage list or the exit question hands focus back to the pause
                    // menu, which would otherwise be left with none and deaf to the keys.
                    dialogOpen = showStageSelector || showExitConfirm
                )

                TanksPhase.StageCleared -> session.summary?.let { summary ->
                    TanksSummaryOverlay(summary) { viewModel.advanceToNextStage() }
                }

                TanksPhase.WaveCleared -> TanksUpgradePicker(
                    wave = session.wave,
                    choices = session.upgradeChoices,
                    loadout = session.loadout,
                    onChoose = { upgrade -> viewModel.chooseUpgrade(upgrade) }
                )

                TanksPhase.GameOver -> TanksGameOverOverlay(
                    campaignScore = session.campaignScore + (renderState?.stageScore ?: 0),
                    endlessWave = session.wave.takeIf { session.isEndless },
                    canResurrect = session.canResurrect,
                    isCoop = session.isCoop,
                    adAvailability = adsState.resurrection,
                    adCooldownSeconds = adsState.resurrectionCooldownSeconds,
                    resurrectionInProgress = session.resurrectionInProgress,
                    resurrectionResult = session.resurrectionResult,
                    onResurrect = { viewModel.resurrectWithAd(ads) },
                    onRetry = { viewModel.retryAfterLoss() },
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
                    viewModel.selectStage(stage)
                    showStageSelector = false
                },
                onDismiss = { showStageSelector = false }
            )
        }

        if (showExitConfirm) {
            // Staying is the safe answer, so it is the one Enter gives.
            val stay = rememberInitialFocus()
            PixelDialog(
                title = stringResource(TanksStrings.pauseExitTitle),
                onDismiss = { showExitConfirm = false },
                buttons = {
                    PixelButton(
                        text = stringResource(TanksStrings.commonCancel),
                        onClick = { showExitConfirm = false },
                        material = PixelMaterial.Steel,
                        focusRequester = stay
                    )
                    PixelButton(
                        text = stringResource(TanksStrings.pauseExitConfirm),
                        onClick = {
                            showExitConfirm = false
                            onExitToMenu()
                        }
                    )
                }
            ) {
                Text(
                    text = stringResource(TanksStrings.pauseExitQuestion),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun TanksBoard(
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

/**
 * Shared chrome for the overlays over the board: a dark veil, a steel plate that scrolls inside
 * rather than clipping on a short window, and the keys to move between its buttons.
 */
@Composable
private fun FullScreenOverlay(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xD9000000)).pixelMenuKeys(),
        contentAlignment = Alignment.Center
    ) {
        PixelPanel(
            modifier = Modifier.safeContentPadding().padding(16.dp).widthIn(max = 560.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
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
    // The first card, or the skip when a maxed-out build has nothing left to offer.
    val first = rememberInitialFocus(choices)
    FullScreenOverlay {
        PixelTitle(stringResource(TanksStrings.upgradeTitle, wave))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(TanksStrings.upgradeSubtitle),
            color = MutedText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        choices.forEachIndexed { index, upgrade ->
            UpgradeCard(
                upgrade = upgrade,
                currentLevel = loadout.levelOf(upgrade),
                focusRequester = first.takeIf { index == 0 },
                onClick = { onChoose(upgrade) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        PixelButton(
            text = stringResource(TanksStrings.upgradeSkip),
            onClick = { onChoose(null) },
            material = PixelMaterial.Steel,
            focusRequester = first.takeIf { choices.isEmpty() }
        )
    }
}

@Composable
private fun UpgradeCard(
    upgrade: TanksUpgrade,
    currentLevel: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    PixelBlockButton(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 280.dp, max = 460.dp).fillMaxWidth(),
        material = PixelMaterial.Steel,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(start = 22.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(TanksStrings.upgradeName(upgrade)),
                    color = GoldLight,
                    style = LocalPixelType.current.label.shadowed()
                )
                // What a card is worth depends on what the run already holds, so the level it
                // would move to is on the card rather than buried in the HUD.
                Text(
                    text = if (currentLevel + 1 >= upgrade.maxLevel) {
                        stringResource(TanksStrings.upgradeMax)
                    } else {
                        stringResource(TanksStrings.upgradeLevel, currentLevel + 1)
                    },
                    color = Color.White,
                    style = LocalPixelType.current.caption.shadowed()
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(TanksStrings.upgradeDescription(upgrade)),
                color = Color.White,
                style = LocalPixelType.current.body.shadowed()
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
    onExitToMenu: () -> Unit,
    /** The stage list or the exit question is open over this menu. */
    dialogOpen: Boolean
) {
    // Resume takes focus as the menu opens and again when a dialog over it closes.
    val resume = rememberInitialFocus(key = dialogOpen, enabled = !dialogOpen)
    FullScreenOverlay {
        PixelTitle(
            text = stringResource(TanksStrings.pause),
            color = Color.White,
            style = LocalPixelType.current.title
        )
        Spacer(modifier = Modifier.height(20.dp))

        PixelButtonColumn(Modifier.width(280.dp)) {
            val wide = Modifier.fillMaxWidth()
            PixelButton(stringResource(TanksStrings.resume), onResume, wide, focusRequester = resume)
            PixelButton(stringResource(TanksStrings.restart), onRestartStage, wide)
            PixelButton(stringResource(TanksStrings.selectStage), onOpenStages, wide, material = PixelMaterial.Steel)
            // A labelled button rather than a bare switch: on a dark board the switch was easy
            // to miss and its state was not obvious at a glance.
            PixelButton(
                text = stringResource(TanksStrings.sound) + ": " +
                    stringResource(if (soundEnabled) TanksStrings.commonOn else TanksStrings.commonOff),
                onClick = { onSoundToggled(!soundEnabled) },
                modifier = wide,
                material = PixelMaterial.Steel
            )
            PixelButton(stringResource(TanksStrings.commonBack), onExitToMenu, wide, material = PixelMaterial.Steel)
        }
    }
}

@Composable
private fun TanksSummaryOverlay(summary: TanksStageSummary, onNextStage: () -> Unit) {
    val next = rememberInitialFocus()
    FullScreenOverlay {
        PixelTitle(stringResource(TanksStrings.summaryTitle, summary.stage))
        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.width(300.dp).pixelInset().padding(horizontal = 14.dp, vertical = 10.dp)) {
            summary.kills.forEach { row ->
                SummaryRow(
                    label = stringResource(TanksStrings.enemyName(row.type)),
                    value = "${row.count} x = ${row.points}",
                    labelColor = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.width(300.dp).padding(horizontal = 14.dp)) {
            SummaryRow(stringResource(TanksStrings.summaryStagePoints), summary.stageScore.toString())
            SummaryRow(stringResource(TanksStrings.summaryTotalPoints), summary.campaignScore.toString())
            SummaryRow(stringResource(TanksStrings.summaryLivesLeft), summary.livesLeft.toString())
        }

        Spacer(modifier = Modifier.height(18.dp))
        PixelButton(
            text = stringResource(TanksStrings.nextStage),
            onClick = onNextStage,
            modifier = Modifier.width(280.dp),
            focusRequester = next
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, labelColor: Color = MutedText) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = labelColor)
        Text(text = value, color = Color.White, style = LocalPixelType.current.body.shadowed())
    }
}

@Composable
private fun TanksGameOverOverlay(
    campaignScore: Int,
    endlessWave: Int?,
    canResurrect: Boolean,
    isCoop: Boolean,
    adAvailability: RewardedAdAvailability,
    adCooldownSeconds: Int,
    resurrectionInProgress: Boolean,
    resurrectionResult: RewardedAdResult?,
    onResurrect: () -> Unit,
    onRetry: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val retry = rememberInitialFocus()
    FullScreenOverlay {
        PixelTitle(
            text = stringResource(
                if (endlessWave != null) TanksStrings.endlessOverTitle else TanksStrings.gameOver
            ),
            color = Color.White,
            style = LocalPixelType.current.title
        )
        if (endlessWave != null) {
            Spacer(modifier = Modifier.height(6.dp))
            PixelTitle(stringResource(TanksStrings.endlessOverWave, endlessWave))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(TanksStrings.score, campaignScore),
            color = MutedText
        )
        Spacer(modifier = Modifier.height(18.dp))
        if (canResurrect) {
            Text(
                stringResource(if (isCoop) TanksStrings.resurrectionOfferCoop else TanksStrings.resurrectionOffer),
                color = MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            )
            Spacer(Modifier.height(10.dp))
            PixelButton(
                text = stringResource(if (resurrectionInProgress) TanksStrings.resurrectionWatching else TanksStrings.resurrectionWatch),
                onClick = onResurrect,
                enabled = adAvailability == RewardedAdAvailability.Ready && !resurrectionInProgress,
                modifier = Modifier.width(280.dp),
                material = PixelMaterial.Gold
            )
            val message = when {
                resurrectionInProgress -> null
                adAvailability == RewardedAdAvailability.CoolingDown -> TanksStrings.resurrectionCooldown
                resurrectionResult == RewardedAdResult.NotEarned -> TanksStrings.resurrectionNotEarned
                resurrectionResult == RewardedAdResult.Failed -> TanksStrings.resurrectionFailed
                adAvailability == RewardedAdAvailability.Loading -> TanksStrings.resurrectionLoading
                adAvailability == RewardedAdAvailability.Unavailable -> TanksStrings.resurrectionUnavailable
                else -> null
            }
            if (message != null) {
                val text = if (message == TanksStrings.resurrectionCooldown) stringResource(message, adCooldownSeconds) else stringResource(message)
                Spacer(Modifier.height(6.dp))
                Text(text, color = MutedText, style = LocalPixelType.current.caption, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }
        PixelButtonColumn(Modifier.width(280.dp)) {
            PixelButton(
                text = stringResource(TanksStrings.playAgain),
                onClick = onRetry,
                enabled = !resurrectionInProgress,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = retry
            )
            PixelButton(
                text = stringResource(TanksStrings.commonBack),
                onClick = onExitToMenu,
                enabled = !resurrectionInProgress,
                modifier = Modifier.fillMaxWidth(),
                material = PixelMaterial.Steel
            )
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
            PixelLoading()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(TanksStrings.loading), color = Color.White)
        }
    }
}

@Composable
private fun TanksMessagePanel(title: String, actionText: String, onAction: () -> Unit) {
    val action = rememberInitialFocus()
    Box(
        modifier = Modifier.fillMaxSize().background(ScreenBackground).padding(24.dp).pixelMenuKeys(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            PixelButton(actionText, onAction, focusRequester = action)
        }
    }
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
    // The list opens on the stage being played, so Enter on it is a restart and the arrows start
    // from where the player already is.
    val current = rememberInitialFocus()
    PixelDialog(
        title = stringResource(TanksStrings.selectStageTitle),
        onDismiss = onDismiss,
        buttons = {
            PixelButton(stringResource(TanksStrings.close), onDismiss, material = PixelMaterial.Steel)
        }
    ) {
        Text(
            text = if (allStagesUnlocked) {
                stringResource(TanksStrings.allStagesAvailable)
            } else {
                stringResource(TanksStrings.highestCompletedStage, highestCompletedStage)
            },
            color = MutedText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..BattleCityMaxStage).chunked(4).forEach { rowStages ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowStages.forEach { stage ->
                        StageTile(
                            modifier = Modifier.weight(1f),
                            stage = stage,
                            info = stageInfos[stage],
                            isSelected = stage == selectedStage,
                            isAvailable = isUnlocked(stage),
                            focusRequester = current.takeIf { stage == selectedStage },
                            onClick = { onStageSelected(stage) }
                        )
                    }
                    repeat(4 - rowStages.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        if (!allStagesUnlocked && unlockedStage < BattleCityMaxStage) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(TanksStrings.lockedStagesHint),
                color = MutedText,
                style = LocalPixelType.current.caption,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StageTile(
    modifier: Modifier,
    stage: Int,
    info: BattleCityStageInfo?,
    isSelected: Boolean,
    isAvailable: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    PixelBlockButton(
        onClick = onClick,
        modifier = modifier,
        material = if (isSelected) PixelMaterial.Brick else PixelMaterial.Steel,
        enabled = isAvailable,
        selected = isSelected,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        minHeight = 0.dp
    ) { color ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StageMiniMap(
                grid = info?.grid.orEmpty(),
                dimmed = !isAvailable,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isAvailable) {
                    PixelIconImage(PixelIcons.Lock, color, cell = 1.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(text = stage.toString(), color = color, style = LocalPixelType.current.label.shadowed())
            }
            DifficultyStars(info?.difficulty ?: 1, lit = if (isAvailable) GoldLight else DisabledText)
        }
    }
}

/** The stage's difficulty as a row of stars, the unearned ones left dark. */
@Composable
private fun DifficultyStars(difficulty: Int, lit: Color) {
    val filled = difficulty.coerceIn(1, BattleCityMaxDifficulty)
    // Shoulder to shoulder: each star already carries a one-cell shadow column, and five of them
    // have to fit a phone's stage tile.
    Row {
        repeat(BattleCityMaxDifficulty) { index ->
            PixelIconImage(PixelIcons.Star, if (index < filled) lit else SteelDark, cell = 1.dp)
        }
    }
}

/** The thumbnail is drawn from the level data itself, so it can never fall out of sync. */
@Composable
private fun StageMiniMap(grid: List<String>, dimmed: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0C0C0C))) {
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
