package com.aectann.battlecity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aectann.battlecity.TanksAssets
import com.aectann.battlecity.TanksResources
import com.aectann.battlecity.TanksStrings
import com.aectann.battlecity.engine.BattleCityEngine
import com.aectann.battlecity.engine.BattleCityLevelData
import com.aectann.battlecity.engine.BattleCityStatus
import com.aectann.battlecity.engine.TanksAttractPilot
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/**
 * The front door, built like an arcade cabinet's attract screen: a battle already going on behind
 * the glass, the logo over it, one big way in, and everything else a row of icons.
 *
 * PLAY is the whole decision for a first visit — a new game. Once there is progress it carries on
 * from the next stage instead, and a quieter New game sits below Endless for starting over. Stage
 * picking lives in the pause menu, where the player already is.
 *
 * The seat choice appears only where there is a keyboard to seat a second player at; the second
 * seat is keyboard-only (see App.kt's coop polling, which can bring it in later).
 */
@Composable
fun MenuScreen(
    highestCompletedStage: Int,
    assets: TanksAssets?,
    dailyClaimable: Boolean,
    bestEndlessWave: Int,
    playerCount: Int,
    /** False on a device with no keys to drive the second seat with. */
    coopAvailable: Boolean,
    onPlayerCountChange: (Int) -> Unit,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onEndless: () -> Unit,
    onDaily: () -> Unit,
    onCollection: () -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    // Enter on a fresh menu plays.
    val play = rememberInitialFocus()
    val type = LocalPixelType.current
    val continuing = highestCompletedStage > 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .pixelMenuKeys()
    ) {
        MenuBackdrop(assets = assets, modifier = Modifier.fillMaxSize())
        val logoMaxWidth = maxWidth * 0.8f
        val logoMaxHeight = maxHeight * 0.18f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            MenuLogo(logo = assets?.logo(), maxWidth = logoMaxWidth, maxHeight = logoMaxHeight)
            Spacer(Modifier.height(6.dp))

            val column = Modifier.widthIn(max = 320.dp).fillMaxWidth()
            if (coopAvailable) {
                PixelSegmented(
                    options = listOf(1, 2),
                    selected = if (playerCount > 1) 2 else 1,
                    label = { seats ->
                        stringResource(if (seats == 1) TanksStrings.menuPlayersOne else TanksStrings.menuPlayersTwo)
                    },
                    onSelect = onPlayerCountChange,
                    modifier = column
                )
            }

            MenuEntry(
                title = stringResource(TanksStrings.menuPlay),
                subtitle = if (continuing) stringResource(TanksStrings.stage, highestCompletedStage + 1) else null,
                onClick = if (continuing) onContinue else onNewGame,
                modifier = column,
                titleStyle = type.heading,
                minHeight = 64.dp,
                focusRequester = play
            )
            MenuEntry(
                title = stringResource(TanksStrings.menuEndless),
                subtitle = if (bestEndlessWave > 0) stringResource(TanksStrings.menuEndlessBest, bestEndlessWave) else null,
                onClick = onEndless,
                modifier = column
            )
            if (continuing) {
                PixelButton(
                    text = stringResource(TanksStrings.menuNewGame),
                    onClick = onNewGame,
                    modifier = column,
                    material = PixelMaterial.Steel
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MenuIcon(PixelIcons.Daily, stringResource(TanksStrings.menuDaily), onDaily, PixelMaterial.Gold, badge = dailyClaimable)
                MenuIcon(PixelIcons.Collection, stringResource(TanksStrings.menuCollection), onCollection)
                MenuIcon(PixelIcons.Records, stringResource(TanksStrings.menuLeaderboard), onLeaderboard)
                MenuIcon(PixelIcons.Settings, stringResource(TanksStrings.menuSettings), onSettings)
                MenuIcon(PixelIcons.About, stringResource(TanksStrings.menuAbout), onAbout)
            }
        }
    }
}

/** A main entry: a label, and under it a quieter line saying where it leads. */
@Composable
private fun MenuEntry(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    titleStyle: TextStyle = LocalPixelType.current.label,
    minHeight: Dp = 48.dp,
    focusRequester: FocusRequester? = null
) {
    PixelBlockButton(
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        minHeight = minHeight
    ) { color ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = color, style = titleStyle.shadowed())
            if (subtitle != null) {
                Text(text = subtitle, color = color.copy(alpha = 0.85f), style = LocalPixelType.current.caption.shadowed())
            }
        }
    }
}

/**
 * One of the secondary destinations: an icon button with its name under it, in a fixed-width
 * cell so five of them share a phone's width.
 */
@Composable
private fun MenuIcon(
    icon: PixelIcon,
    label: String,
    onClick: () -> Unit,
    material: PixelMaterial = PixelMaterial.Steel,
    badge: Boolean = false
) {
    Column(modifier = Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PixelIconButton(icon = icon, label = label, onClick = onClick, material = material, size = 52.dp, badge = badge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            style = LocalPixelType.current.caption.shadowed(),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * The logotype at a whole multiple of its own pixels — as large as fits the given box, never
 * smaller than 1× — so its blocks stay square. Before the image has loaded, the name in type.
 */
@Composable
private fun MenuLogo(logo: ImageBitmap?, maxWidth: Dp, maxHeight: Dp) {
    if (logo == null) {
        PixelTitle(
            text = stringResource(TanksStrings.appName),
            color = Color.White,
            style = LocalPixelType.current.display
        )
        return
    }
    val density = LocalDensity.current
    val scale = with(density) {
        floor(minOf(maxWidth.toPx() / logo.width, maxHeight.toPx() / logo.height)).toInt().coerceIn(1, 4)
    }
    val width = with(density) { (logo.width * scale).toDp() }
    val height = with(density) { (logo.height * scale).toDp() }
    Canvas(Modifier.size(width, height)) {
        drawImage(
            image = logo,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(logo.width * scale, logo.height * scale),
            filterQuality = FilterQuality.None
        )
    }
}

/**
 * Behind the menu: the live battlefield in the middle, as large as the screen's short side, and
 * around it a wall of the game's own bricks laid on the same grid, so the field reads as set into
 * the fortress rather than floating on a flat fill. Both are dimmed well back: this is scenery.
 */
@Composable
private fun MenuBackdrop(assets: TanksAssets?, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val side = minOf(maxWidth, maxHeight)
        BrickWall(assets = assets, fieldSide = side, modifier = Modifier.fillMaxSize())
        AttractBattle(assets = assets, modifier = Modifier.align(Alignment.Center).size(side))
        // Dark enough that the buttons are plainly the foreground, light enough that the tanks
        // still read as moving at a glance.
        Box(Modifier.align(Alignment.Center).size(side).background(Color.Black.copy(alpha = 0.62f)))
    }
}


/**
 * A battle nobody is playing: the menu's own map, both seats flown by [TanksAttractPilot], run
 * on the real engine at the display's frame rate. When a round ends either way — the wave beaten
 * or the base lost — it holds the final frame for a moment and starts over.
 */
@Composable
private fun AttractBattle(assets: TanksAssets?, modifier: Modifier) {
    var level by remember { mutableStateOf<BattleCityLevelData?>(null) }
    LaunchedEffect(Unit) {
        level = runCatching { TanksResources.loadMenuLevel() }.getOrNull()
    }
    val loadedLevel = level
    if (loadedLevel == null || assets == null) {
        Box(modifier.background(BoardBackground))
        return
    }

    val engine = remember(loadedLevel) {
        BattleCityEngine(stageNumber = 0, level = loadedLevel, seed = AttractSeed, initialLives = listOf(9, 9))
    }
    val pilot = remember(loadedLevel) { TanksAttractPilot(seed = AttractSeed) }
    // The chips and flashes, but not the shake or the points: this is scenery behind buttons.
    val fx = remember(loadedLevel) { TanksFx(seed = AttractSeed.toInt(), popups = false, shakes = false) }
    var state by remember(engine) { mutableStateOf(engine.currentState()) }
    var animationFrame by remember(engine) { mutableIntStateOf(0) }

    LaunchedEffect(engine) {
        var previous = withFrameNanos { it }
        var frames = 0
        var overFor = 0f
        while (true) {
            val now = withFrameNanos { it }
            val delta = ((now - previous) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.25f)
            previous = now
            fx.update(delta)
            state = if (state.status == BattleCityStatus.Running) {
                engine.step(delta, pilot.inputs(state, delta)).also { fx.onEvents(it.fx) }.state
            } else {
                overFor += delta
                if (overFor < RoundOverHoldSeconds) state else engine.reset().also { overFor = 0f }
            }
            animationFrame = ++frames
        }
    }
    TanksBoard(modifier = modifier, state = state, assets = assets, animationFrame = animationFrame, fx = fx)
}

/** Fixed, so the menu opens on the same battle every time — and every screenshot agrees. */
private const val AttractSeed = 2026_0928L
private const val RoundOverHoldSeconds = 1.5f

/**
 * Shared chrome for the pages off the menu: a lettered title, the page, and Back. Back takes
 * focus as the page opens unless [backTakesFocus] is off because the page has a better
 * first choice of its own (the daily claim, say).
 */
@Composable
internal fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    backTakesFocus: Boolean = true,
    content: @Composable () -> Unit
) {
    val back = rememberInitialFocus(enabled = backTakesFocus)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .safeContentPadding()
            .padding(24.dp)
            .pixelMenuKeys(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PixelTitle(text = title, style = LocalPixelType.current.title)
        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).weight(1f)) { content() }
        Spacer(modifier = Modifier.height(16.dp))
        PixelButton(
            text = stringResource(TanksStrings.commonBack),
            onClick = onBack,
            enabled = backEnabled,
            modifier = Modifier.widthIn(min = 220.dp),
            material = PixelMaterial.Steel,
            focusRequester = back
        )
    }
}
