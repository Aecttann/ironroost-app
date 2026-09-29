package com.aectann.battlecity.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The board is pixel art drawn without smoothing, so its size decides whether sprites stay crisp.
 * These pin the rule that picks it: a whole multiple of the 16-pixel cells where one is close
 * enough, all the room there is where none is.
 */
class BoardSizeTest {

    private val oneToOne = Density(1f)
    private val cells = 13  // 208 px at 1x

    private fun side(room: Float, density: Density = oneToOne) =
        with(density) { snapBoardSide(room.dp, cells).toPx() }

    @Test
    fun `a 720p window gets exactly three times the sprites`() {
        assertEquals(624f, side(700f))
    }

    @Test
    fun `a 1080p window gets five times`() {
        assertEquals(1040f, side(1060f))
    }

    @Test
    fun `when the whole multiple would waste over a fifth of the room it takes the room instead`() {
        // 400 / 208 = 1.92: below 2x there is no whole multiple worth having.
        assertEquals(400f, side(400f))
        // 620 / 208 = 2.98: 2x would give up a third of the room.
        assertEquals(620f, side(620f))
    }

    @Test
    fun `density is counted in device pixels, not dp`() {
        // 350 dp at 2x is 700 px: three whole multiples, 624 px, which is 312 dp.
        assertEquals(624f, side(350f, Density(2f)))
    }
}
