package com.aectann.battlecity.ui

import com.aectann.battlecity.engine.BattleCityDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The simulation steers off [TanksHeldInput], so the rules a player feels — last key wins, a
 * finger beats the keyboard, letting go of one key falls back to the other — are pinned here.
 */
class TanksHeldInputTest {

    @Test
    fun `starts with nothing held`() {
        val input = TanksHeldInput()

        assertNull(input.direction)
        assertFalse(input.firePressed)
        assertNull(input.visibleDirection)
        assertFalse(input.visibleFirePressed)
    }

    @Test
    fun `last key pressed wins`() {
        val input = TanksHeldInput()

        input.pressKey(BattleCityDirection.Left)
        input.pressKey(BattleCityDirection.Up)

        assertEquals(BattleCityDirection.Up, input.direction)
    }

    @Test
    fun `releasing the newest key falls back to the one still held`() {
        val input = TanksHeldInput()

        input.pressKey(BattleCityDirection.Left)
        input.pressKey(BattleCityDirection.Up)
        input.releaseKey(BattleCityDirection.Up)

        assertEquals(BattleCityDirection.Left, input.direction)
    }

    @Test
    fun `repeating a held key does not stack it`() {
        val input = TanksHeldInput()

        input.pressKey(BattleCityDirection.Left)
        input.pressKey(BattleCityDirection.Left)
        input.releaseKey(BattleCityDirection.Left)

        assertNull(input.direction)
    }

    @Test
    fun `a finger on the pad overrides the keyboard and hands it back on release`() {
        val input = TanksHeldInput()

        input.pressKey(BattleCityDirection.Up)
        input.setPointerDirection(BattleCityDirection.Down)
        assertEquals(BattleCityDirection.Down, input.direction)

        input.setPointerDirection(null)
        assertEquals(BattleCityDirection.Up, input.direction)
    }

    @Test
    fun `fire is held while either source holds it`() {
        val input = TanksHeldInput()

        input.setKeyboardFire(true)
        assertTrue(input.firePressed)

        input.setPointerFire(true)
        input.setKeyboardFire(false)
        assertTrue(input.firePressed)

        input.setPointerFire(false)
        assertFalse(input.firePressed)
    }

    @Test
    fun `losing focus drops the keys the browser will never release`() {
        val input = TanksHeldInput()

        input.pressKey(BattleCityDirection.Up)
        input.setKeyboardFire(true)
        input.setPointerDirection(BattleCityDirection.Left)
        input.setPointerFire(true)

        input.releaseKeyboard()

        // A finger stays on the screen when the window loses focus, so only the keys let go.
        assertEquals(BattleCityDirection.Left, input.direction)
        assertTrue(input.firePressed)
    }

    @Test
    fun `leaving play releases everything`() {
        val input = TanksHeldInput()

        input.pressKey(BattleCityDirection.Up)
        input.setKeyboardFire(true)
        input.setPointerDirection(BattleCityDirection.Left)
        input.setPointerFire(true)

        input.releaseAll()

        assertNull(input.direction)
        assertFalse(input.firePressed)
    }

    @Test
    fun `the on-screen controls follow what the simulation reads`() {
        val input = TanksHeldInput()

        input.pressKey(BattleCityDirection.Right)
        input.setKeyboardFire(true)
        assertEquals(input.direction, input.visibleDirection)
        assertEquals(input.firePressed, input.visibleFirePressed)

        input.releaseAll()
        assertEquals(input.direction, input.visibleDirection)
        assertEquals(input.firePressed, input.visibleFirePressed)
    }
}
