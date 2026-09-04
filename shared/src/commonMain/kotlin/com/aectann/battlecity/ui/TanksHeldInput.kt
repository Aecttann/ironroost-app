package com.aectann.battlecity.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aectann.battlecity.engine.BattleCityDirection

/**
 * The keys and pointers held right now.
 *
 * The simulation reads [direction] and [firePressed] from a `withFrameNanos` callback while the
 * key handler writes to them from the browser's own event dispatch. Snapshot state does not carry
 * a value across that boundary on Kotlin/Wasm — the frame callback keeps reading the value the
 * frame started with — so what the simulation reads are plain fields. [visibleDirection] and
 * [visibleFirePressed] are snapshot mirrors, refreshed by every mutator, so the on-screen pad and
 * fire button still recompose when they light up.
 */
internal class TanksHeldInput {
    private var pointer: BattleCityDirection? = null
    private var pointerFire: Boolean = false
    private var keyboardFire: Boolean = false

    // Last key wins, so holding Left and tapping Up steers up and falls back to left on release.
    private val keys = mutableListOf<BattleCityDirection>()

    val direction: BattleCityDirection? get() = pointer ?: keys.lastOrNull()
    val firePressed: Boolean get() = pointerFire || keyboardFire

    var visibleDirection by mutableStateOf<BattleCityDirection?>(null)
        private set
    var visibleFirePressed by mutableStateOf(false)
        private set

    fun setPointerDirection(value: BattleCityDirection?) {
        pointer = value
        publish()
    }

    fun setPointerFire(pressed: Boolean) {
        pointerFire = pressed
        publish()
    }

    fun pressKey(value: BattleCityDirection) {
        keys.remove(value)
        keys.add(value)
        publish()
    }

    fun releaseKey(value: BattleCityDirection) {
        keys.remove(value)
        publish()
    }

    fun setKeyboardFire(pressed: Boolean) {
        keyboardFire = pressed
        publish()
    }

    /** Focus left the board: the browser will not send the matching key-up. */
    fun releaseKeyboard() {
        keys.clear()
        keyboardFire = false
        publish()
    }

    fun releaseAll() {
        pointer = null
        pointerFire = false
        releaseKeyboard()
    }

    private fun publish() {
        visibleDirection = direction
        visibleFirePressed = firePressed
    }
}
