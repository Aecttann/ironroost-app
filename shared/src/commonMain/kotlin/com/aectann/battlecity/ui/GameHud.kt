package com.aectann.battlecity.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aectann.battlecity.TanksAssets
import com.aectann.battlecity.TanksStrings
import com.aectann.battlecity.engine.BattleCityDirection
import com.aectann.battlecity.engine.BattleCityPlayerRenderState
import com.aectann.battlecity.engine.BattleCityRenderState
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// ------------------------------------------------------------------- backdrop

/**
 * A wall of the game's own bricks, laid on the grid of a [fieldSide]-wide square field of
 * [cells] cells centred in it, and dimmed well back: the fortress the field is set into. Behind
 * the menu's demo battle and behind the game itself, so the two read as one place.
 */
@Composable
internal fun BrickWall(assets: TanksAssets?, fieldSide: Dp, modifier: Modifier = Modifier, cells: Int = 13, dim: Float = 0.8f) {
    val brick = assets?.brick()
    if (brick == null) {
        Box(modifier.background(MenuBackground))
        return
    }
    // A layer of its own: the wall never changes during a run, and without one it was redrawn,
    // four hundred bricks at a time, every frame the board beside it moved.
    Canvas(modifier.graphicsLayer()) {
        val sidePx = fieldSide.toPx()
        val tile = (sidePx / cells).coerceAtLeast(4f)
        val tileSize = IntSize(ceil(tile).toInt(), ceil(tile).toInt())
        // Start one tile before the edge on the field's own grid, so the wall's joints line up
        // with the field's cells wherever the two meet.
        val originX = (size.width - sidePx) / 2f
        val originY = (size.height - sidePx) / 2f
        val firstX = originX - ceil(originX / tile) * tile
        val firstY = originY - ceil(originY / tile) * tile
        var y = firstY
        while (y < size.height) {
            var x = firstX
            while (x < size.width) {
                drawImage(
                    image = brick,
                    dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                    dstSize = tileSize,
                    filterQuality = FilterQuality.None
                )
                x += tile
            }
            y += tile
        }
        drawRect(Color.Black.copy(alpha = dim))
    }
}

// ---------------------------------------------------------------------- board

/** The sprites are 16 pixels a cell. */
private const val SpritePixels = 16

/**
 * The side to draw a board of [cells] cells at, given [room] to fit in.
 *
 * Sprites are pixel art drawn with no smoothing, so at a fractional scale some of their pixels
 * come out a screen pixel wider than others, and a moving tank shimmers. A whole-number scale
 * avoids that, and is worth giving up to a fifth of the room for; below 2× — a phone held
 * upright — the board takes all the room it has instead.
 */
internal fun Density.snapBoardSide(room: Dp, cells: Int): Dp {
    val roomPx = room.toPx()
    val nativePx = (cells * SpritePixels).toFloat()
    val scale = roomPx / nativePx
    val whole = floor(scale)
    val sidePx = if (whole >= 2f && whole / scale >= 0.8f) whole * nativePx else floor(roomPx)
    return sidePx.toDp()
}

/** How thick the steel ring round the board is. */
private val BoardFrame = PixelUnitDp * 4

/**
 * What covers the board between stages: [closed] while a stage is being set up, [title] lettered
 * on it, [onTap] to open it early. The controls are not written here: they are shown on the field,
 * beside the tank (BoardLesson).
 */
internal class StageCurtain(val closed: Boolean, val title: String, val onTap: () -> Unit)

/**
 * The board in a riveted steel ring: the arena as a machined thing, not a hole in the page. The
 * whole assembly shakes with [fx]; the curtain closes over the board, not the ring, and over the
 * [lesson]'s keys, which it uncovers as it opens.
 */
@Composable
private fun FramedBoard(
    side: Dp,
    state: BattleCityRenderState,
    assets: TanksAssets,
    animationFrame: Int,
    fx: TanksFx?,
    curtain: StageCurtain?,
    lesson: BoardLesson?
) {
    val cells = maxOf(state.tiles.cols, state.tiles.rows)
    Box(
        modifier = Modifier
            .size(side + BoardFrame * 2)
            .graphicsLayer {
                if (fx != null) {
                    fx.tick.intValue
                    val tile = side.toPx() / cells
                    translationX = floor(fx.shakeX * tile)
                    translationY = floor(fx.shakeY * tile)
                }
            }
            .drawBehind { drawPixelPanel(framed = true) },
        contentAlignment = Alignment.Center
    ) {
        TanksBoard(modifier = Modifier.size(side), state = state, assets = assets, animationFrame = animationFrame, fx = fx)
        if (lesson != null) LessonKeys(lesson, state, Modifier.size(side))
        if (curtain != null) StageCurtainView(curtain, Modifier.size(side))
    }
}

/**
 * Two steel shutters over the board with the stage's name on a plate between them. They slide
 * apart as the run starts, which is the stage's start, marked, rather than a grey card cut away.
 */
@Composable
private fun StageCurtainView(curtain: StageCurtain, modifier: Modifier) {
    val open by animateFloatAsState(
        targetValue = if (curtain.closed) 0f else 1f,
        animationSpec = tween(durationMillis = if (curtain.closed) 0 else 520, easing = FastOutSlowInEasing),
        label = "curtain"
    )
    if (open >= 1f) return
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .pointerInput(curtain.onTap) { detectTapGestures { curtain.onTap() } }
    ) {
        val half = maxWidth / 2
        listOf(-1f, 1f).forEach { sideSign ->
            Box(
                modifier = Modifier
                    .width(half)
                    .fillMaxHeight()
                    .align(if (sideSign < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .graphicsLayer { translationX = sideSign * half.toPx() * open }
                    .drawBehind {
                        drawPixelBlock(PixelMaterial.Steel, lift = 2)
                        // Horizontal seams, so the shutters read as plates rather than as a fill.
                        val u = pixelUnit()
                        var y = size.height / 6f
                        while (y < size.height - u) {
                            drawRect(SteelDark, Offset(u, floor(y)), Size(size.width - 2 * u, u))
                            y += size.height / 6f
                        }
                    }
            )
        }
        Box(
            modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = (1f - open * 2.5f).coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center
        ) {
            PixelPanel(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) {
                PixelTitle(curtain.title, style = LocalPixelType.current.title)
            }
        }
    }
}

// ------------------------------------------------------------------- playfield

/**
 * Everything on the game screen under the overlays: the board as large as it can be drawn
 * sharply, and around it what a player glances at — in landscape, a panel either side (who is
 * alive and how strong, on the left; what is still coming and where, on the right); upright, one
 * strip across the top. Numbers are in the pixel font; nearly everything else is an icon.
 *
 * The on-screen stick and trigger live in the same panels when [touchControls] is on: steering
 * under the left thumb, the trigger under the right. Pause then goes up, out of the trigger's way
 * — beside the stage in landscape, into the top strip upright: just above the trigger, a hurried
 * shot that landed a little high paused the game.
 */
@Composable
internal fun TanksPlayfield(
    modifier: Modifier,
    state: BattleCityRenderState,
    assets: TanksAssets,
    animationFrame: Int,
    stage: Int,
    campaignScore: Int,
    isCoop: Boolean,
    isEndless: Boolean,
    activeDirection: BattleCityDirection?,
    isRunning: Boolean,
    showStartButton: Boolean,
    touchControls: Boolean,
    /** Shows the key beside pause, on a device that has keys. */
    keyboardHints: Boolean,
    isFirePressed: Boolean,
    onDirectionChanged: (BattleCityDirection?) -> Unit,
    onFirePressedChanged: (Boolean) -> Unit,
    onStartPause: () -> Unit,
    fx: TanksFx? = null,
    curtain: StageCurtain? = null,
    /** The first-run keys over the board and the blinking stick and trigger; see BoardLesson. */
    lesson: BoardLesson? = null
) {
    val cells = maxOf(state.tiles.cols, state.tiles.rows)
    val score = campaignScore + state.stageScore
    val stageLabel = if (isEndless) stringResource(TanksStrings.wave, state.wave) else stringResource(TanksStrings.stage, stage)
    val density = LocalDensity.current

    // The board is redrawn every frame; everything else on this screen changes a few times a run.
    // Each block round it has its own layer (graphicsLayer) so that a frame repaints the board
    // alone rather than the whole screen: on a low-end phone that was the difference between
    // forty-odd frames a second and twenty.
    BoxWithConstraints(modifier) {
        val gap = 10.dp
        if (maxWidth > maxHeight) {
            val minSide = if (touchControls) 160.dp else 132.dp
            val room = minOf(maxHeight, maxWidth - minSide * 2 - gap * 2) - BoardFrame * 2
            val side = with(density) { snapBoardSide(room.coerceAtLeast(120.dp), cells) }
            val framed = side + BoardFrame * 2
            val panelWidth = ((maxWidth - framed - gap * 2) / 2).coerceAtMost(340.dp)
            // A full-HD window leaves wide flanks; the HUD grows with them rather than sitting
            // small in a corner of a mostly empty plate.
            val big = panelWidth >= 300.dp
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The flanks are exactly as tall as the framed board: the three read as one
                // cabinet front, not as cards floating in the corners.
                PixelPanel(
                    modifier = Modifier.width(panelWidth).height(framed).graphicsLayer(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    ScoreLine(score, big)
                    state.players.forEach { player ->
                        Spacer(Modifier.height(if (big) 20.dp else 14.dp))
                        PlayerCard(player, assets, isCoop, big)
                    }
                    Spacer(Modifier.weight(1f))
                    if (touchControls) {
                        DirectionPad(
                            activeDirection = activeDirection,
                            onDirectionChanged = onDirectionChanged,
                            beckon = lesson?.beckonPad == true,
                            modifier = Modifier.size(minOf(panelWidth - 32.dp, 168.dp)).align(Alignment.CenterHorizontally)
                        )
                    }
                }
                FramedBoard(side, state, assets, animationFrame, fx, curtain, lesson)
                PixelPanel(
                    modifier = Modifier.width(panelWidth).height(framed).graphicsLayer(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StageLine(assets.flagIcon(), stageLabel, if (isEndless) state.wave else stage, big, Modifier.weight(1f))
                        if (showStartButton && touchControls) {
                            Spacer(Modifier.width(8.dp))
                            PauseControl(isRunning, keyboardHints, onStartPause)
                        }
                    }
                    Spacer(Modifier.height(if (big) 20.dp else 14.dp))
                    EnemyReserve(state.enemiesPending, assets.enemyQueueIcon(), big)
                    LoadoutLines(state)
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (showStartButton && !touchControls) PauseControl(isRunning, keyboardHints, onStartPause)
                        if (touchControls) {
                            FireButton(isFirePressed, onFirePressedChanged, lesson?.beckonFire == true, Modifier.size(minOf(panelWidth - 32.dp, 132.dp)))
                        }
                    }
                }
            }
        } else {
            val stripHeight = 64.dp
            val controlsHeight = if (touchControls) 164.dp else 0.dp
            val room = minOf(maxWidth, maxHeight - stripHeight - controlsHeight - gap * 2) - BoardFrame * 2
            val side = with(density) { snapBoardSide(room.coerceAtLeast(120.dp), cells) }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                PortraitStrip(
                    state = state,
                    assets = assets,
                    score = score,
                    stageLabel = stageLabel,
                    isCoop = isCoop,
                    showStartButton = showStartButton,
                    isRunning = isRunning,
                    keyboardHints = keyboardHints,
                    onStartPause = onStartPause,
                    modifier = Modifier.fillMaxWidth().height(stripHeight).graphicsLayer()
                )
                // The board sits in the middle of whatever height is left, not up under the strip.
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    FramedBoard(side, state, assets, animationFrame, fx, curtain, lesson)
                }
                if (touchControls) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(controlsHeight).graphicsLayer(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DirectionPad(activeDirection, onDirectionChanged, lesson?.beckonPad == true, Modifier.size(controlsHeight - 8.dp))
                        // The trigger has the height to itself now that pause is up in the strip.
                        FireButton(isFirePressed, onFirePressedChanged, lesson?.beckonFire == true, Modifier.size(120.dp))
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------- pieces

/** Icon cell size for the HUD's pixel icons: the UI unit, or half as big again on [big] flanks. */
private fun hudCell(big: Boolean): Dp = if (big) PixelUnitDp * 1.5f else PixelUnitDp

@Composable
private fun ScoreLine(score: Int, big: Boolean) {
    val type = LocalPixelType.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelIconImage(PixelIcons.Records, GoldLight, cell = hudCell(big))
        Spacer(Modifier.width(8.dp))
        Text(score.toString(), color = Color.White, style = (if (big) type.title else type.heading).shadowed())
    }
}

/**
 * One seat: its tank as it looks right now (colour and upgrade level both), the lives left,
 * and the level as stars. In co-op the seat's number is on it too, in the tank's colour.
 */
@Composable
private fun PlayerCard(player: BattleCityPlayerRenderState, assets: TanksAssets, isCoop: Boolean, big: Boolean) {
    val colour = if (player.index == 0) "green" else "yellow"
    val tint = if (player.index == 0) PlayerGreen else PlayerYellow
    val level = player.level.coerceIn(1, 4)
    val type = LocalPixelType.current
    Column {
        if (isCoop) {
            Text(stringResource(TanksStrings.playerSeat, player.index + 1), color = tint, style = (if (big) type.label else type.caption).shadowed())
            Spacer(Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpriteImage(assets.sprite("player_${colour}_level${level}_up"), if (big) 42.dp else 28.dp)
            Spacer(Modifier.width(8.dp))
            Text("×${player.lives}", color = Color.White, style = (if (big) type.title else type.heading).shadowed())
        }
        Spacer(Modifier.height(4.dp))
        Row {
            repeat(4) { index ->
                PixelIconImage(PixelIcons.Star, if (index < level) GoldLight else SteelFace, cell = hudCell(big))
            }
        }
    }
}

/**
 * The flag with the stage, or the wave in an endless run. Where the whole [label] does not fit on
 * one line — a phone's narrow flank, with the pause button beside it — the flag keeps just the
 * [number], as on the original's own side panel, rather than breaking "Stage" in two.
 */
@Composable
private fun StageLine(flag: ImageBitmap?, label: String, number: Int, big: Boolean, modifier: Modifier = Modifier) {
    val type = LocalPixelType.current
    val style = (if (big) type.heading else type.label).shadowed()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SpriteImage(flag, if (big) 30.dp else 20.dp)
        Spacer(Modifier.width(8.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val measurer = rememberTextMeasurer()
            val roomPx = with(LocalDensity.current) { maxWidth.toPx() }
            val fits = remember(label, style, roomPx) {
                measurer.measure(label, style, softWrap = false, maxLines = 1).size.width <= roomPx
            }
            Text(if (fits) label else number.toString(), color = Color.White, style = style, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The enemies still to come, one icon each, in as many columns as the panel fits: the classic
 * reserve column, and the one number a player tracks without reading it.
 */
@Composable
private fun EnemyReserve(pending: Int, icon: ImageBitmap?, big: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val iconSize = if (big) 24.dp else 16.dp
        val spacing = 4.dp
        val columns = ((maxWidth + spacing) / (iconSize + spacing)).toInt().coerceIn(2, 10)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            (0 until pending).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { SpriteImage(icon, iconSize) }
                }
            }
        }
    }
}

/** The endless build so far, one line an upgrade. Nothing at all in a campaign. */
@Composable
private fun ColumnScope.LoadoutLines(state: BattleCityRenderState) {
    val taken = state.loadout.taken()
    if (taken.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    taken.forEach { (upgrade, level) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            PixelIconImage(icon = PixelIcons.upgrade(upgrade), color = GoldLight)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(TanksStrings.upgradeName(upgrade)) + " " + level,
                color = GoldLight,
                style = LocalPixelType.current.caption.shadowed()
            )
        }
    }
}

/** Pause, or start while the run is held; with its key under it where there are keys. */
@Composable
private fun PauseControl(isRunning: Boolean, keyboardHints: Boolean, onStartPause: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PixelIconButton(
            icon = if (isRunning) PixelIcons.Pause else PixelIcons.Play,
            label = stringResource(if (isRunning) TanksStrings.pause else TanksStrings.start),
            onClick = onStartPause,
            size = 52.dp
        )
        if (keyboardHints) {
            Spacer(Modifier.height(4.dp))
            Text("P", color = MutedText, style = LocalPixelType.current.caption.shadowed())
        }
    }
}

/** The portrait HUD: lives and level, stage, enemies left, score, pause — one row. */
@Composable
private fun PortraitStrip(
    state: BattleCityRenderState,
    assets: TanksAssets,
    score: Int,
    stageLabel: String,
    isCoop: Boolean,
    showStartButton: Boolean,
    isRunning: Boolean,
    keyboardHints: Boolean,
    onStartPause: () -> Unit,
    modifier: Modifier
) {
    val type = LocalPixelType.current
    PixelPanel(modifier = modifier, framed = false, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.players.forEach { player ->
                val colour = if (player.index == 0) "green" else "yellow"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteImage(assets.sprite("player_${colour}_level${player.level.coerceIn(1, 4)}_up"), 22.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("×${player.lives}", color = if (isCoop && player.index == 1) PlayerYellow else Color.White, style = type.label.shadowed())
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteImage(assets.flagIcon(), 16.dp)
                Spacer(Modifier.width(4.dp))
                Text(stageLabel, color = Color.White, style = type.caption.shadowed())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteImage(assets.enemyQueueIcon(), 16.dp)
                Spacer(Modifier.width(4.dp))
                Text(state.enemiesPending.toString(), color = Color.White, style = type.label.shadowed())
            }
            Text(score.toString(), color = GoldLight, style = type.label.shadowed())
            if (showStartButton) {
                PixelIconButton(
                    icon = if (isRunning) PixelIcons.Pause else PixelIcons.Play,
                    label = stringResource(if (isRunning) TanksStrings.pause else TanksStrings.start),
                    onClick = onStartPause,
                    size = 44.dp
                )
            }
        }
    }
}

/** A sprite at [size], unsmoothed; a plain block of panel colour until the sprite is in. */
@Composable
internal fun SpriteImage(image: ImageBitmap?, size: Dp) {
    if (image == null) {
        Box(Modifier.size(size).background(PanelLight))
        return
    }
    Canvas(Modifier.size(size)) {
        drawImage(
            image = image,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            filterQuality = FilterQuality.None
        )
    }
}

// -------------------------------------------------------------------- touch

/**
 * The on-screen stick, drawn as a four-armed steel pad: the arm being held sinks and lights.
 * It reacts on touch down, not after a drag, so a tap steers too; sliding across it changes
 * direction without lifting the finger. While [beckon] is on — a new player who has not steered
 * yet — its arms are rimmed in blinking gold.
 */
@Composable
private fun DirectionPad(
    activeDirection: BattleCityDirection?,
    onDirectionChanged: (BattleCityDirection?) -> Unit,
    beckon: Boolean,
    modifier: Modifier
) {
    val lit = beckonBlink(beckon)
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onDirectionChanged(directionFromPadPosition(down.position, size))
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    onDirectionChanged(directionFromPadPosition(change.position, size))
                    change.consume()
                }
                onDirectionChanged(null)
            }
        }
    ) {
        val u = pixelUnit()
        // Arms a third of the pad wide, snapped to the pixel grid so the bevels stay even.
        val third = floor(size.minDimension / 3f / u) * u
        val offsetX = (size.width - third * 3) / 2f
        val offsetY = (size.height - third * 3) / 2f
        fun cell(column: Int, row: Int) = Offset(offsetX + column * third, offsetY + row * third)
        val armSize = Size(third, third)
        val arms = listOf(
            BattleCityDirection.Up to cell(1, 0),
            BattleCityDirection.Left to cell(0, 1),
            BattleCityDirection.Right to cell(2, 1),
            BattleCityDirection.Down to cell(1, 2)
        )
        // Lit, the pad gets a gold rim round its whole cross: a backing one unit larger than
        // each arm, which the centre and the arms then cover all but the edge of.
        if (lit) arms.forEach { (_, at) -> drawRect(GoldLight, at - Offset(u, u), Size(third + 2 * u, third + 2 * u)) }
        drawPixelBlock(PixelMaterial.Steel, lift = 1, texture = false, topLeft = cell(1, 1), blockSize = armSize)
        arms.forEach { (direction, at) ->
            val held = direction == activeDirection
            drawPixelBlock(
                material = PixelMaterial.Steel,
                lift = if (held) 0 else 2,
                outline = if (lit) GoldLight else Ink,
                faceTint = if (held) 0.3f else if (lit) 0.2f else 0f,
                texture = false,
                topLeft = at,
                blockSize = armSize
            )
            val icon = when (direction) {
                BattleCityDirection.Up -> PixelIcons.ArrowUp
                BattleCityDirection.Down -> PixelIcons.ArrowDown
                BattleCityDirection.Left -> PixelIcons.ArrowLeft
                BattleCityDirection.Right -> PixelIcons.ArrowRight
            }
            val sink = if (held) 2 * u else 0f
            val iconX = at.x + floor((third - icon.width * u) / 2f / u) * u
            val iconY = at.y + floor((third - 2 * u - icon.height * u) / 2f / u) * u + sink
            drawPixelIcon(icon, Offset(iconX, iconY), u, if (held) Color.White else if (lit) GoldLight else SteelLight)
        }
    }
}

/**
 * The trigger: held, not tapped — the tank fires as long as it is down, like the key. Rimmed in
 * blinking gold while [beckon] is on, until a new player has fired once.
 */
@Composable
private fun FireButton(pressed: Boolean, onPressedChanged: (Boolean) -> Unit, beckon: Boolean, modifier: Modifier) {
    val unit = with(LocalDensity.current) { pixelUnit().toDp() }
    val lit = beckonBlink(beckon)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
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
            }
            .drawBehind {
                if (lit) drawBeckonRing()
                drawPixelBlock(PixelMaterial.Alarm, lift = if (pressed) 0 else 2, outline = if (lit) GoldLight else Ink, faceTint = if (lit) 0.25f else 0f, texture = false)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(TanksStrings.fire),
            color = Color.White,
            style = LocalPixelType.current.heading.shadowed(),
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = if (pressed) unit else -unit)
        )
    }
}

/**
 * On and off twice a second while [beckon] holds, for a control a new player has not found yet;
 * always off otherwise, with no animation running.
 */
@Composable
private fun beckonBlink(beckon: Boolean): Boolean {
    if (!beckon) return false
    val phase by rememberInfiniteTransition(label = "beckon").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "blink"
    )
    return (phase * 2f) % 1f < 0.55f
}

/** A gold ring just outside a control, over the panel it sits on: "this one". */
private fun DrawScope.drawBeckonRing() {
    val u = pixelUnit()
    val w = size.width
    val h = size.height
    drawRect(GoldLight, Offset(-u, -2 * u), Size(w + 2 * u, u))
    drawRect(GoldLight, Offset(-u, h + u), Size(w + 2 * u, u))
    drawRect(GoldLight, Offset(-2 * u, -u), Size(u, h + 2 * u))
    drawRect(GoldLight, Offset(w + u, -u), Size(u, h + 2 * u))
}

/** Which arm of the pad a touch at [position] is on; the middle is a dead zone. */
private fun directionFromPadPosition(position: Offset, size: IntSize): BattleCityDirection? {
    val dx = position.x - size.width / 2f
    val dy = position.y - size.height / 2f
    val threshold = minOf(size.width, size.height) * 0.12f
    if (abs(dx) < threshold && abs(dy) < threshold) return null
    return if (abs(dx) > abs(dy)) {
        if (dx > 0f) BattleCityDirection.Right else BattleCityDirection.Left
    } else {
        if (dy > 0f) BattleCityDirection.Down else BattleCityDirection.Up
    }
}
