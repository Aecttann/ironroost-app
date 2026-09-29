package com.aectann.battlecity.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.aectann.battlecity.TanksTutorial
import com.aectann.battlecity.engine.BattleCityDirection
import com.aectann.battlecity.engine.BattleCityRenderState
import kotlin.math.floor

/**
 * What the board is teaching this frame, for [TanksPlayfield]: the keys drawn beside each tank
 * that has not used them yet ([keys], on a device that has some), and the stick and trigger made
 * to blink ([touch], on a touch screen). Everything is read from [tutorial] while drawing, so each
 * group goes the frame it is used.
 */
internal class BoardLesson(
    val tutorial: TanksTutorial,
    val coop: Boolean,
    val keys: Boolean,
    val touch: Boolean
) {
    val beckonPad: Boolean get() = touch && tutorial.showMove(0)
    val beckonFire: Boolean get() = touch && tutorial.showFire(0)
}

/** One seat's keys: the steering cross or crosses, and the trigger. */
private class SeatKeys(val crosses: List<KeyCross>, val fire: String)

/** A steering cross, lettered or with arrows. */
private enum class KeyCross(val up: String?, val left: String?, val down: String?, val right: String?) {
    Wasd("W", "A", "S", "D"),
    Arrows(null, null, null, null)
}

/** The same split the key handler makes (directionForKey, fireSeatForKey). */
private fun seatKeys(seat: Int, coop: Boolean): SeatKeys = when {
    !coop -> SeatKeys(listOf(KeyCross.Wasd, KeyCross.Arrows), "SPACE")
    seat == 0 -> SeatKeys(listOf(KeyCross.Wasd), "SPACE")
    else -> SeatKeys(listOf(KeyCross.Arrows), "ENTER")
}

/**
 * The key caps over the board, a little plate of them just above each tank still being taught,
 * with a notch pointing down at it. Drawn over the sprites and under the stage curtain, so they
 * are uncovered as the shutters open. The caps press themselves in turn — the cross going round,
 * the trigger tapping — which is what says "these are for pressing" without a word of text.
 */
@Composable
internal fun LessonKeys(lesson: BoardLesson, state: BattleCityRenderState, modifier: Modifier) {
    if (!lesson.keys || !lesson.tutorial.showing) return
    val textMeasurer = rememberTextMeasurer()
    val type = LocalPixelType.current
    val letterSizes = listOf(type.heading, type.label, type.caption).map { it.copy(color = Color.White).shadowed() }
    val beat by rememberInfiniteTransition(label = "lesson").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
        label = "beat"
    )
    Canvas(modifier) {
        val tiles = state.tiles
        val tile = size.minDimension / maxOf(tiles.cols, tiles.rows)
        val board = Size(tile * tiles.cols, tile * tiles.rows)
        val origin = Offset((size.width - board.width) / 2f, (size.height - board.height) / 2f)
        state.players.forEachIndexed { seat, player ->
            val tank = player.tank ?: return@forEachIndexed
            val move = lesson.tutorial.showMove(seat)
            val fire = lesson.tutorial.showFire(seat)
            if (!move && !fire) return@forEachIndexed
            drawKeyPlate(
                keys = seatKeys(seat, lesson.coop),
                move = move,
                fire = fire,
                tankTopLeft = Offset(origin.x + tank.x * tile, origin.y + tank.y * tile),
                tile = tile,
                origin = origin,
                board = board,
                beat = beat,
                textMeasurer = textMeasurer,
                letterSizes = letterSizes
            )
        }
    }
}

private fun DrawScope.drawKeyPlate(
    keys: SeatKeys,
    move: Boolean,
    fire: Boolean,
    tankTopLeft: Offset,
    tile: Float,
    origin: Offset,
    board: Size,
    beat: Float,
    textMeasurer: TextMeasurer,
    /** Large, medium and small, for the cap sizes a board can end up with. */
    letterSizes: List<TextStyle>
) {
    val u = pixelUnit()
    fun snap(value: Float) = floor(value / u) * u

    // Caps about three quarters of a cell, on the UI's pixel grid, readable on a phone and not
    // swamping a full-HD board.
    val cap = snap(tile * 0.72f).coerceIn(11 * u, 20 * u)
    val gap = u
    val crossGap = snap(cap / 2f)
    val crossWidth = 3 * cap + 2 * gap
    val crossHeight = 2 * cap + gap
    val moveWidth = keys.crosses.size * crossWidth + (keys.crosses.size - 1) * crossGap
    val fireWidth = crossWidth
    val contentWidth = maxOf(if (move) moveWidth else 0f, if (fire) fireWidth else 0f)
    val contentHeight = (if (move) crossHeight else 0f) + (if (fire) cap else 0f) + (if (move && fire) 3 * gap else 0f)
    val pad = 3 * u
    val plate = Size(contentWidth + 2 * pad, contentHeight + 2 * pad)
    val notch = 3 * u

    // Above the tank, centred on it and kept on the board; below it, for a tank at the top.
    val tankCenterX = tankTopLeft.x + tile / 2f
    val left = snap((tankCenterX - plate.width / 2f).coerceIn(origin.x + u, origin.x + board.width - plate.width - u))
    var top = snap(tankTopLeft.y - notch - u - plate.height)
    val above = top >= origin.y + u
    if (!above) top = snap(tankTopLeft.y + tile + notch + u)

    // The plate: dark glass with its corners cut, like every panel, and the notch at the tank.
    val glass = Ink.copy(alpha = 0.8f)
    drawRect(glass, Offset(left + u, top), Size(plate.width - 2 * u, plate.height))
    drawRect(glass, Offset(left, top + u), Size(plate.width, plate.height - 2 * u))
    val notchX = snap(tankCenterX.coerceIn(left + 4 * u, left + plate.width - 4 * u))
    for (step in 0 until 3) {
        val width = (5 - 2 * step) * u
        val y = if (above) top + plate.height + step * u else top - (step + 1) * u
        drawRect(glass, Offset(notchX - width / 2f, y), Size(width, u))
    }

    val letters = letterSizes[
        when {
            cap >= 16 * u -> 0
            cap >= 13 * u -> 1
            else -> 2
        }
    ]
    var y = top + pad
    if (move) {
        // Round the cross, one arm a beat: up, left, down, right.
        val held = listOf(BattleCityDirection.Up, BattleCityDirection.Left, BattleCityDirection.Down, BattleCityDirection.Right)[(beat * 4).toInt().coerceIn(0, 3)]
        var x = left + pad + snap((contentWidth - moveWidth) / 2f)
        keys.crosses.forEach { cross ->
            val arms = listOf(
                Triple(BattleCityDirection.Up, Offset(x + cap + gap, y), cross.up),
                Triple(BattleCityDirection.Left, Offset(x, y + cap + gap), cross.left),
                Triple(BattleCityDirection.Down, Offset(x + cap + gap, y + cap + gap), cross.down),
                Triple(BattleCityDirection.Right, Offset(x + 2 * (cap + gap), y + cap + gap), cross.right)
            )
            arms.forEach { (direction, at, letter) ->
                val pressed = direction == held
                drawKeyCap(at, Size(cap, cap), PixelMaterial.Steel, pressed) { face ->
                    if (letter != null) {
                        drawCentredText(letter, face, textMeasurer, letters)
                    } else {
                        val icon = when (direction) {
                            BattleCityDirection.Up -> PixelIcons.ArrowUp
                            BattleCityDirection.Down -> PixelIcons.ArrowDown
                            BattleCityDirection.Left -> PixelIcons.ArrowLeft
                            BattleCityDirection.Right -> PixelIcons.ArrowRight
                        }
                        val iconAt = Offset(
                            face.left + snap((face.width - icon.width * u) / 2f),
                            face.top + snap((face.height - icon.height * u) / 2f)
                        )
                        drawPixelIcon(icon, iconAt, u, Color.White)
                    }
                }
            }
            x += crossWidth + crossGap
        }
        y += crossHeight + 3 * gap
    }
    if (fire) {
        // The trigger taps twice a round, in the red of the on-screen FIRE button.
        val pressed = (beat * 2f) % 1f > 0.6f
        val at = Offset(left + pad + snap((contentWidth - fireWidth) / 2f), y)
        drawKeyCap(at, Size(fireWidth, cap), PixelMaterial.Alarm, pressed) { face ->
            drawCentredText(keys.fire, face, textMeasurer, letters)
        }
    }
}

/** Where a cap's label goes: its face, which moves down as the cap is pressed. */
private class CapFace(val left: Float, val top: Float, val width: Float, val height: Float)

private fun DrawScope.drawKeyCap(
    topLeft: Offset,
    capSize: Size,
    material: PixelMaterial,
    pressed: Boolean,
    label: DrawScope.(CapFace) -> Unit
) {
    val u = pixelUnit()
    val lift = if (pressed) 0 else 2
    drawPixelBlock(material, lift = lift, faceTint = if (pressed) 0.35f else 0f, texture = false, topLeft = topLeft, blockSize = capSize)
    // The face as drawPixelBlock lays it out: inside the outline, above the base that shows.
    val faceTop = topLeft.y + u + (2 - lift) * u
    label(CapFace(topLeft.x + u, faceTop, capSize.width - 2 * u, capSize.height - 4 * u))
}

private fun DrawScope.drawCentredText(text: String, face: CapFace, textMeasurer: TextMeasurer, style: TextStyle) {
    val layout = textMeasurer.measure(text, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            floor(face.left + (face.width - layout.size.width) / 2f),
            floor(face.top + (face.height - layout.size.height) / 2f)
        )
    )
}
