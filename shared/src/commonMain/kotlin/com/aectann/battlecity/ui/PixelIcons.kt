package com.aectann.battlecity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import kotlin.math.floor

/**
 * A one-colour pixel glyph, written out as rows of text: `#` is a lit pixel, anything else is
 * empty. Drawn on the UI's pixel grid, so it is as sharp as the frames around it at any density,
 * and costs the web build no files.
 */
@Immutable
internal class PixelIcon(vararg rows: String) {
    val rows: List<String> = rows.toList()
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size

    init {
        require(rows.isNotEmpty()) { "an icon needs at least one row" }
    }
}

internal object PixelIcons {
    val Settings = PixelIcon(
        "...###...",
        ".#.###.#.",
        "..#####..",
        "####.####",
        "###...###",
        "####.####",
        "..#####..",
        ".#.###.#.",
        "...###..."
    )

    val Records = PixelIcon(
        "#########",
        "#.#####.#",
        "#.#####.#",
        ".#.###.#.",
        "..#####..",
        "...###...",
        "....#....",
        "..#####..",
        "..#####.."
    )

    val About = PixelIcon(
        "..#####..",
        ".#######.",
        "####.####",
        "#########",
        "###..####",
        "####.####",
        "####.####",
        ".##...##.",
        "..#####.."
    )

    val Collection = PixelIcon(
        "..######.",
        "..#....#.",
        "#####..#.",
        "#...#..#.",
        "#.#.#..#.",
        "#...####.",
        "#.#.#....",
        "#...#....",
        "#####...."
    )

    val Daily = PixelIcon(
        "..#...#..",
        "...#.#...",
        "#########",
        "#...#...#",
        "#########",
        ".#..#..#.",
        ".#..#..#.",
        ".#..#..#.",
        ".#######."
    )

    val Back = PixelIcon(
        ".........",
        "...#.....",
        "..##.....",
        ".########",
        "#########",
        ".########",
        "..##.....",
        "...#.....",
        "........."
    )

    val Play = PixelIcon(
        ".#.....",
        ".##....",
        ".###...",
        ".####..",
        ".#####.",
        ".####..",
        ".###...",
        ".##....",
        ".#....."
    )

    /** The steering pad's arms. */
    val ArrowUp = PixelIcon(
        "...#...",
        "..###..",
        ".#####.",
        "#######"
    )
    val ArrowDown = PixelIcon(
        "#######",
        ".#####.",
        "..###..",
        "...#..."
    )
    val ArrowLeft = PixelIcon(
        "...#",
        "..##",
        ".###",
        "####",
        ".###",
        "..##",
        "...#"
    )
    val ArrowRight = PixelIcon(
        "#...",
        "##..",
        "###.",
        "####",
        "###.",
        "##..",
        "#..."
    )

    val Pause = PixelIcon(
        ".##...##.",
        ".##...##.",
        ".##...##.",
        ".##...##.",
        ".##...##.",
        ".##...##.",
        ".##...##."
    )

    val SoundOn = PixelIcon(
        "...#.....",
        "..##..#..",
        "####...#.",
        "####.#.#.",
        "####.#.#.",
        "####...#.",
        "..##..#..",
        "...#....."
    )

    val SoundOff = PixelIcon(
        "...#.....",
        "..##.....",
        "####.#..#",
        "####..##.",
        "####..##.",
        "####.#..#",
        "..##.....",
        "...#....."
    )

    val Lock = PixelIcon(
        "..###..",
        ".#...#.",
        ".#...#.",
        "#######",
        "###.###",
        "###.###",
        "#######"
    )

    val Star = PixelIcon(
        "...#...",
        "...#...",
        "#######",
        ".#####.",
        "..###..",
        ".##.##.",
        ".#...#."
    )

    /** The mark on a button's "something is waiting" tag. */
    val Alert = PixelIcon(
        "##",
        "##",
        "##",
        "##",
        "..",
        "##"
    )

    /** The menu cursor: points at whatever the keys have selected. */
    val Cursor = PixelIcon(
        "#...",
        "##..",
        "###.",
        "####",
        "###.",
        "##..",
        "#..."
    )
}

/**
 * Draws [icon] with its top-left corner at [topLeft], one icon pixel per [cell] device pixels,
 * over a hard shadow one cell down and right when [shadow] is given.
 */
internal fun DrawScope.drawPixelIcon(
    icon: PixelIcon,
    topLeft: Offset,
    cell: Float,
    color: Color,
    shadow: Color? = Ink
) {
    fun pass(tint: Color, shift: Float) {
        icon.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, pixel ->
                if (pixel == '#') {
                    drawRect(tint, Offset(topLeft.x + x * cell + shift, topLeft.y + y * cell + shift), Size(cell, cell))
                }
            }
        }
    }
    if (shadow != null) pass(shadow, cell)
    pass(color, 0f)
}

/**
 * An icon laid out as a composable, one icon pixel per [cell] — the UI's pixel unit unless a
 * smaller one is asked for, as the stage list's difficulty stars do.
 */
@Composable
internal fun PixelIconImage(
    icon: PixelIcon,
    color: Color,
    modifier: Modifier = Modifier,
    shadow: Color? = Ink,
    cell: Dp = PixelUnitDp
) {
    // One extra cell each way leaves room for the shadow.
    Canvas(modifier.size(cell * (icon.width + 1), cell * (icon.height + 1))) {
        drawPixelIcon(icon, Offset.Zero, floor(cell.toPx()).coerceAtLeast(1f), color, shadow)
    }
}
