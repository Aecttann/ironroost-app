package com.aectann.battlecity.ui

import com.aectann.battlecity.engine.BattleCityFxEvent
import com.aectann.battlecity.engine.BattleCityFxKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The board's effects: what a kill leaves behind, and that all of it goes away by itself. */
class TanksFxTest {

    private val kill = BattleCityFxEvent(BattleCityFxKind.EnemyDestroyed, x = 4.5f, y = 2.5f, points = 100)

    @Test
    fun `a kill leaves wreckage and floats its points up from where it happened`() {
        val fx = TanksFx()
        fx.onEvents(listOf(kill))

        assertTrue(fx.particles.isNotEmpty(), "no wreckage")
        val popup = fx.popupList.single()
        assertEquals("+100", popup.text)

        val startY = popup.y
        fx.update(0.3f)
        assertTrue(popup.y < startY, "the points should rise")
    }

    @Test
    fun `everything settles and the board stops shaking`() {
        val fx = TanksFx()
        fx.onEvents(listOf(kill, BattleCityFxEvent(BattleCityFxKind.BaseDestroyed, 6.5f, 12.5f)))
        fx.update(0.02f)
        assertTrue(fx.shakeX != 0f || fx.shakeY != 0f, "a lost base should shake the board")

        repeat(300) { fx.update(1f / 60f) }
        assertFalse(fx.active, "effects still running after five seconds")
        assertEquals(0f, fx.shakeX)
        assertEquals(0f, fx.shakeY)
    }

    @Test
    fun `the menu's battle gets no points and no shake`() {
        val fx = TanksFx(popups = false, shakes = false)
        fx.onEvents(listOf(kill))
        fx.update(0.02f)

        assertTrue(fx.popupList.isEmpty())
        assertEquals(0f, fx.shakeX)
        assertTrue(fx.particles.isNotEmpty(), "the wreckage should still fly")
    }
}
