package com.aectann.classicgames.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aectann.classicgames.BuildConfig
import com.aectann.classicgames.R
import com.aectann.classicgames.battlecity.BattleCityAssets
import com.aectann.classicgames.battlecity.BattleCityDirection
import com.aectann.classicgames.battlecity.BattleCityEffectKind
import com.aectann.classicgames.battlecity.BattleCityInput
import com.aectann.classicgames.battlecity.BattleCityMaxDifficulty
import com.aectann.classicgames.battlecity.BattleCityMaxStage
import com.aectann.classicgames.battlecity.BattleCityRenderState
import com.aectann.classicgames.battlecity.BattleCityRepository
import com.aectann.classicgames.battlecity.BattleCitySoundEvent
import com.aectann.classicgames.battlecity.BattleCitySoundPlayer
import com.aectann.classicgames.battlecity.BattleCityStageInfo
import com.aectann.classicgames.battlecity.BattleCityTankRenderState
import com.aectann.classicgames.battlecity.BattleCityTileSnapshot
import com.aectann.classicgames.battlecity.NoopTanksAnalytics
import com.aectann.classicgames.battlecity.TanksAnalytics
import com.aectann.classicgames.battlecity.TanksAttemptTokenCost
import com.aectann.classicgames.battlecity.TanksMessage
import com.aectann.classicgames.battlecity.TanksPhase
import com.aectann.classicgames.battlecity.TanksProgressStore
import com.aectann.classicgames.battlecity.TanksStageSummary
import com.aectann.classicgames.battlecity.TanksViewModel
import com.aectann.classicgames.battlecity.TanksWallet
import com.aectann.classicgames.data.GameStatisticsManager
import com.aectann.classicgames.data.PreferencesManager
import com.aectann.classicgames.viewmodel.GameViewModel
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private val BoardBackground = Color(0xFF111111)
private val ScreenBackground = Color(0xFF202020)

@Composable
fun TanksGameScreen(
    onBack: () -> Unit,
    gameViewModel: GameViewModel,
    onFullscreenChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val preferencesManager = remember { PreferencesManager() }
    val tokens by gameViewModel.tokens.collectAsState()

    val viewModel: TanksViewModel = viewModel(
        factory = remember(appContext, gameViewModel) {
            viewModelFactory {
                initializer {
                    TanksViewModel(
                        repository = BattleCityRepository(appContext),
                        wallet = WalletAdapter(gameViewModel),
                        progress = ProgressAdapter(
                            statistics = gameViewModel.statisticsManager,
                            preferences = preferencesManager,
                            context = appContext
                        ),
                        analytics = createAnalytics(appContext, preferencesManager),
                        allStagesUnlocked = BuildConfig.DEBUG
                    )
                }
            }
        }
    )

    val session by viewModel.session.collectAsState()
    val renderState by viewModel.render.collectAsState()

    var assets by remember { mutableStateOf<BattleCityAssets?>(null) }
    var assetError by remember { mutableStateOf<String?>(null) }
    var pressedDirection by remember { mutableStateOf<BattleCityDirection?>(null) }
    var isFirePressed by remember { mutableStateOf(false) }
    var animationFrame by remember { mutableIntStateOf(0) }
    var showStageSelector by rememberSaveable { mutableStateOf(false) }
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    var showFullscreenPrompt by rememberSaveable { mutableStateOf(false) }

    val soundPlayer = remember(appContext) {
        BattleCitySoundPlayer(appContext, preferencesManager.isSoundEnabled(appContext))
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { BattleCityAssets.load(appContext) } }
            .onSuccess { assets = it }
            .onFailure { assetError = it.message ?: it::class.java.simpleName }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                TanksMessage.NotEnoughTokens -> Toast.makeText(
                    context,
                    context.getString(R.string.tokens_not_enough),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(session.phase, session.stage) {
        if (session.phase != TanksPhase.Playing) {
            pressedDirection = null
            isFirePressed = false
            soundPlayer.setEngineRunning(false)
        }
        if (session.phase == TanksPhase.Ready) {
            soundPlayer.play(BattleCitySoundEvent.StageStart)
            if (preferencesManager.shouldShowTanksFullscreenPrompt(context)) {
                showFullscreenPrompt = true
            }
        }
    }

    // The run must stop when the app leaves the foreground, or the player comes back dead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.pause()
                soundPlayer.setEngineRunning(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            soundPlayer.release()
            exitTanksFullscreen(context)
            onFullscreenChanged(false)
        }
    }

    LaunchedEffect(viewModel, session.phase) {
        if (session.phase != TanksPhase.Playing) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val delta = ((frame - previousFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f)
            previousFrame = frame
            val direction = pressedDirection
            val events = viewModel.advance(
                deltaSeconds = delta,
                input = BattleCityInput(direction = direction, firePressed = isFirePressed)
            )
            events.forEach(soundPlayer::play)
            soundPlayer.setEngineRunning(direction != null)
            animationFrame++
        }
    }

    val runInProgress = session.charged &&
        session.phase in setOf(TanksPhase.Playing, TanksPhase.Paused, TanksPhase.Ready)

    fun leaveScreen() {
        viewModel.pause()
        exitTanksFullscreen(context)
        onFullscreenChanged(false)
        onBack()
    }

    fun requestExit() {
        soundPlayer.playMenuSelect()
        if (runInProgress) showExitConfirm = true else leaveScreen()
    }

    BackHandler { requestExit() }

    val loadedAssets = assets
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { requestExit() }) { Text(stringResource(R.string.back)) }
            Button(
                enabled = !runInProgress,
                onClick = {
                    soundPlayer.playMenuSelect()
                    viewModel.pause()
                    showStageSelector = true
                }
            ) { Text(stringResource(R.string.tanks_select_stage)) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TanksHudBar(
            state = renderState,
            stage = session.stage,
            campaignScore = session.campaignScore,
            tokens = tokens,
            assets = loadedAssets
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            assetError != null -> TanksMessagePanel(
                title = stringResource(R.string.tanks_error_message, assetError.orEmpty()),
                actionText = stringResource(R.string.back),
                onAction = { leaveScreen() }
            )

            session.phase == TanksPhase.Error -> TanksMessagePanel(
                title = stringResource(R.string.tanks_error_message, session.errorMessage.orEmpty()),
                actionText = stringResource(R.string.tanks_retry),
                onAction = { viewModel.retryLoad() }
            )

            loadedAssets == null || renderState == null -> TanksLoadingPanel()

            else -> TanksPlayArea(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = renderState!!,
                session = session,
                assets = loadedAssets,
                animationFrame = animationFrame,
                activeDirection = pressedDirection,
                isFirePressed = isFirePressed,
                onDirectionChanged = { pressedDirection = it },
                onFirePressedChanged = { isFirePressed = it },
                onStartPause = {
                    soundPlayer.playMenuSelect()
                    viewModel.togglePause()
                },
                onNextStage = {
                    soundPlayer.playMenuSelect()
                    viewModel.advanceToNextStage()
                },
                onRetry = {
                    soundPlayer.playMenuSelect()
                    viewModel.retryAfterLoss()
                }
            )
        }
    }

    if (showStageSelector) {
        TanksStageSelectorDialog(
            selectedStage = session.stage,
            stageInfos = session.stageInfos,
            highestCompletedStage = session.highestCompletedStage,
            unlockedStage = viewModel.unlockedStage(),
            isDebugBuild = BuildConfig.DEBUG,
            isUnlocked = viewModel::isStageUnlocked,
            onStageSelected = { stage ->
                soundPlayer.playMenuSelect()
                viewModel.selectStage(stage)
                showStageSelector = false
            },
            onDismiss = { showStageSelector = false }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.tanks_exit_title)) },
            text = { Text(stringResource(R.string.tanks_exit_message, TanksAttemptTokenCost)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    viewModel.abandonRun()
                    leaveScreen()
                }) { Text(stringResource(R.string.tanks_exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.tanks_exit_cancel))
                }
            }
        )
    }

    if (showFullscreenPrompt) {
        TanksFullscreenPromptDialog(
            onConfirmFullscreen = { doNotShowAgain ->
                if (doNotShowAgain) preferencesManager.disableTanksFullscreenPrompt(context)
                enterTanksFullscreen(context)
                onFullscreenChanged(true)
                showFullscreenPrompt = false
            },
            onDismiss = { doNotShowAgain ->
                if (doNotShowAgain) preferencesManager.disableTanksFullscreenPrompt(context)
                showFullscreenPrompt = false
            }
        )
    }
}

// ---------------------------------------------------------------------------- HUD

@Composable
private fun TanksHudBar(
    state: BattleCityRenderState?,
    stage: Int,
    campaignScore: Int,
    tokens: Int,
    assets: BattleCityAssets?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconStrip(
                    image = assets?.lifeIcon(),
                    count = state?.lives ?: 0,
                    tint = Color(0xFF9BD16B)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "x${state?.lives ?: 0}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteIcon(assets?.flagIcon(), size = 18.dp, tint = Color(0xFFE0A03C))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tanks_stage, stage),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.score, campaignScore + (state?.stageScore ?: 0)),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.tokens_count, tokens),
                color = Color.White,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconStrip(
                image = assets?.enemyQueueIcon(),
                count = state?.enemiesPending ?: 0,
                tint = Color(0xFFBFC5CC),
                maxIcons = 10
            )
            Text(
                text = stringResource(
                    R.string.tanks_enemies,
                    state?.destroyedEnemies ?: 0,
                    state?.totalEnemies ?: 0
                ),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = stringResource(R.string.tanks_tank_level, state?.playerLevel ?: 1),
                color = Color(0xFF9BD16B),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun IconStrip(
    image: ImageBitmap?,
    count: Int,
    tint: Color,
    maxIcons: Int = 5
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shown = count.coerceAtMost(maxIcons)
        repeat(shown) {
            SpriteIcon(image, size = 14.dp, tint = tint)
            Spacer(modifier = Modifier.width(2.dp))
        }
        if (count > maxIcons) {
            Text(text = "+${count - maxIcons}", color = tint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpriteIcon(image: ImageBitmap?, size: androidx.compose.ui.unit.Dp, tint: Color) {
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

@Composable
private fun TanksPlayArea(
    modifier: Modifier,
    state: BattleCityRenderState,
    session: com.aectann.classicgames.battlecity.TanksSession,
    assets: BattleCityAssets,
    animationFrame: Int,
    activeDirection: BattleCityDirection?,
    isFirePressed: Boolean,
    onDirectionChanged: (BattleCityDirection?) -> Unit,
    onFirePressedChanged: (Boolean) -> Unit,
    onStartPause: () -> Unit,
    onNextStage: () -> Unit,
    onRetry: () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val isLandscape = maxWidth > maxHeight
        val spacing = 12.dp

        if (isLandscape) {
            val controlsWidth = if (maxWidth < 660.dp) 304.dp else 332.dp
            val boardSize = minOf(maxHeight, (maxWidth - controlsWidth - spacing).coerceAtLeast(180.dp))
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TanksBoardPanel(
                    modifier = Modifier.size(boardSize),
                    state = state,
                    session = session,
                    assets = assets,
                    animationFrame = animationFrame,
                    onNextStage = onNextStage,
                    onRetry = onRetry
                )
                TanksHudControls(
                    modifier = Modifier.width(controlsWidth).height(boardSize),
                    activeDirection = activeDirection,
                    isRunning = session.phase == TanksPhase.Playing,
                    isFirePressed = isFirePressed,
                    onDirectionChanged = onDirectionChanged,
                    onFirePressedChanged = onFirePressedChanged,
                    onStartPause = onStartPause
                )
            }
        } else {
            val controlsHeight = if (maxHeight < 580.dp) 170.dp else 192.dp
            val boardSize = minOf(maxWidth, (maxHeight - controlsHeight - spacing).coerceAtLeast(180.dp))
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically)
            ) {
                TanksBoardPanel(
                    modifier = Modifier.size(boardSize),
                    state = state,
                    session = session,
                    assets = assets,
                    animationFrame = animationFrame,
                    onNextStage = onNextStage,
                    onRetry = onRetry
                )
                TanksHudControls(
                    modifier = Modifier.fillMaxWidth().height(controlsHeight),
                    activeDirection = activeDirection,
                    isRunning = session.phase == TanksPhase.Playing,
                    isFirePressed = isFirePressed,
                    onDirectionChanged = onDirectionChanged,
                    onFirePressedChanged = onFirePressedChanged,
                    onStartPause = onStartPause
                )
            }
        }
    }
}

@Composable
private fun TanksBoardPanel(
    modifier: Modifier,
    state: BattleCityRenderState,
    session: com.aectann.classicgames.battlecity.TanksSession,
    assets: BattleCityAssets,
    animationFrame: Int,
    onNextStage: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier.aspectRatio(1f).background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        TanksBoard(state = state, assets = assets, animationFrame = animationFrame)

        when (session.phase) {
            TanksPhase.StageCleared -> session.summary?.let { summary ->
                TanksSummaryOverlay(summary = summary, onNextStage = onNextStage)
            }

            TanksPhase.GameOver -> TanksGameOverOverlay(
                campaignScore = session.campaignScore + state.stageScore,
                onRetry = onRetry
            )

            TanksPhase.Paused -> TanksDimOverlay(stringResource(R.string.tanks_pause))
            else -> Unit
        }
    }
}

@Composable
private fun TanksBoard(
    state: BattleCityRenderState,
    assets: BattleCityAssets,
    animationFrame: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
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

        state.player?.let { drawTank(it, assets, tileSize, offset) }
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
    assets: BattleCityAssets,
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
    assets: BattleCityAssets,
    tileSize: Float,
    offset: Offset
) {
    assets.tank(tank)?.let { image -> drawSprite(image, tank.x, tank.y, 1f, tileSize, offset) }
    if (tank.hasShield) {
        assets.shield(tank.shieldFrame)?.let { image ->
            drawSprite(image, tank.x, tank.y, 1f, tileSize, offset)
        }
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

@Composable
private fun TanksDimOverlay(title: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xB3000000)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TanksSummaryOverlay(summary: TanksStageSummary, onNextStage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.tanks_summary_title, summary.stage),
            color = Color(0xFFE0A03C),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))

        summary.kills.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(0.86f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = enemyTypeName(row.type), color = Color.White, fontSize = 13.sp)
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
        SummaryRow(stringResource(R.string.tanks_summary_stage_points), summary.stageScore)
        SummaryRow(stringResource(R.string.tanks_summary_total_points), summary.campaignScore)
        SummaryRow(stringResource(R.string.tanks_summary_lives_left), summary.livesLeft)

        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onNextStage) { Text(stringResource(R.string.tanks_next_stage)) }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(0.86f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFBFC5CC), fontSize = 13.sp)
        Text(text = value.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(3.dp))
}

@Composable
private fun TanksGameOverOverlay(campaignScore: Int, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xE6000000)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.tanks_game_over),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.score, campaignScore),
            color = Color(0xFFBFC5CC),
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.tanks_play_again_cost, TanksAttemptTokenCost))
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
            Text(stringResource(R.string.tanks_loading), color = Color.White)
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

@Composable
private fun enemyTypeName(type: String): String = when (type) {
    "fast" -> stringResource(R.string.tanks_enemy_fast)
    "power" -> stringResource(R.string.tanks_enemy_power)
    "armor" -> stringResource(R.string.tanks_enemy_armor)
    else -> stringResource(R.string.tanks_enemy_basic)
}

// ----------------------------------------------------------------------- controls

@Composable
private fun TanksHudControls(
    modifier: Modifier,
    activeDirection: BattleCityDirection?,
    isRunning: Boolean,
    isFirePressed: Boolean,
    onDirectionChanged: (BattleCityDirection?) -> Unit,
    onFirePressedChanged: (Boolean) -> Unit,
    onStartPause: () -> Unit
) {
    BoxWithConstraints(modifier = modifier.padding(8.dp)) {
        val joystickSize = if (maxWidth < 340.dp || maxHeight < 176.dp) 140.dp else 152.dp
        val actionWidth = if (maxWidth < 340.dp) 140.dp else 150.dp
        val actionHeight = if (maxHeight < 176.dp) 160.dp else 176.dp

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectionJoystick(
                activeDirection = activeDirection,
                modifier = Modifier.size(joystickSize),
                onDirectionChanged = onDirectionChanged
            )
            TanksActionPanel(
                modifier = Modifier.width(actionWidth).height(actionHeight),
                isRunning = isRunning,
                isFirePressed = isFirePressed,
                onFirePressedChanged = onFirePressedChanged,
                onStartPause = onStartPause
            )
        }
    }
}

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
                    .padding(14.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(joystickKnobAlignment(activeDirection))
                    .padding(28.dp)
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.36f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.50f), CircleShape)
            )
        }
    }
}

@Composable
private fun TanksActionPanel(
    modifier: Modifier,
    isRunning: Boolean,
    isFirePressed: Boolean,
    onFirePressedChanged: (Boolean) -> Unit,
    onStartPause: () -> Unit
) {
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
                ControlTapButton(
                    text = if (isRunning) {
                        stringResource(R.string.tanks_pause)
                    } else {
                        stringResource(R.string.tanks_start)
                    },
                    modifier = Modifier.size(94.dp, startButtonHeight),
                    onClick = onStartPause
                )
                ControlHoldButton(
                    text = stringResource(R.string.tanks_fire),
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
    isDebugBuild: Boolean,
    isUnlocked: (Int) -> Boolean,
    onStageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tanks_select_stage_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isDebugBuild) {
                        stringResource(R.string.tanks_debug_all_stages_available)
                    } else {
                        stringResource(R.string.tanks_highest_completed_stage, highestCompletedStage)
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
                        repeat(4 - rowStages.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (!isDebugBuild && unlockedStage < BattleCityMaxStage) {
                    Text(
                        text = stringResource(R.string.tanks_locked_stages_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
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
        isSelected -> Color(0xFFE0A03C)
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
            color = if (isAvailable) Color(0xFFE0A03C) else Color.Gray
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
                    'B' -> Color(0xFFB4522A)
                    'S' -> Color(0xFFC9C9C9)
                    'W' -> Color(0xFF2A4FB4)
                    'F' -> Color(0xFF2E8B34)
                    'I' -> Color(0xFFBFE3EC)
                    'H' -> Color(0xFFE0A03C)
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
    return "★".repeat(filled) + "☆".repeat(BattleCityMaxDifficulty - filled)
}

@Composable
private fun TanksFullscreenPromptDialog(
    onConfirmFullscreen: (Boolean) -> Unit,
    onDismiss: (Boolean) -> Unit
) {
    var doNotShowAgain by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(doNotShowAgain) },
        title = { Text(stringResource(R.string.tanks_fullscreen_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tanks_fullscreen_message))
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = doNotShowAgain, onCheckedChange = { doNotShowAgain = it })
                    Text(stringResource(R.string.tanks_fullscreen_do_not_show))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmFullscreen(doNotShowAgain) }) {
                Text(stringResource(R.string.tanks_fullscreen_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(doNotShowAgain) }) {
                Text(stringResource(R.string.tanks_fullscreen_skip))
            }
        }
    )
}

// ------------------------------------------------------------------------ adapters

private class WalletAdapter(private val gameViewModel: GameViewModel) : TanksWallet {
    override fun tokens(): Int = gameViewModel.tokens.value

    override fun spend(amount: Int): Boolean {
        if (gameViewModel.tokens.value < amount) return false
        gameViewModel.delTokenFromPlayer(amount)
        return true
    }
}

private class ProgressAdapter(
    private val statistics: GameStatisticsManager,
    private val preferences: PreferencesManager,
    private val context: Context
) : TanksProgressStore {
    override fun highestCompletedStage(): Int = statistics.getTanksHighestCompletedStage()

    override fun saveHighestCompletedStage(stage: Int) =
        statistics.updateTanksHighestCompletedStage(stage)

    override fun recordAttempt(): Int {
        statistics.putTanksPlayedCount()
        return statistics.getTanksPlayedCount()
    }

    override fun recordStageCleared(): Int {
        statistics.putTanksWonCount()
        return statistics.getTanksWonCount()
    }

    override fun isSoundEnabled(): Boolean = preferences.isSoundEnabled(context)
}

private class FirebaseTanksAnalytics(private val userRef: DatabaseReference) : TanksAnalytics {
    override fun onAttemptStarted(totalAttempts: Int) {
        runCatching { userRef.child("tanks_games").setValue(totalAttempts) }
    }

    override fun onStageCleared(stage: Int, totalWins: Int, highestCompletedStage: Int) {
        runCatching {
            userRef.child("tanks_wins").setValue(totalWins)
            userRef.child("tanks_stage_reached").setValue(highestCompletedStage)
        }
    }
}

private fun createAnalytics(context: Context, preferences: PreferencesManager): TanksAnalytics =
    runCatching {
        val userId = preferences.getUserId(context)
        FirebaseTanksAnalytics(
            FirebaseDatabase.getInstance().getReference("users/$userId/data/user_stats")
        )
    }.getOrDefault(NoopTanksAnalytics)

// --------------------------------------------------------------------- fullscreen

private fun enterTanksFullscreen(context: Context) {
    val activity = context.findActivity() ?: return
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private fun exitTanksFullscreen(context: Context) {
    val activity = context.findActivity() ?: return
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
