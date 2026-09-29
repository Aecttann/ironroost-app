package com.aectann.battlecity

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aectann.battlecity.engine.BattleCityDirection

/**
 * The lesson a new player gets instead of a page of text: the keys drawn on the board next to
 * their tank, each group gone the moment it has been used. There are only two things to find —
 * moving and firing — and once a solo player has done both, the lesson is over for good.
 *
 * Co-op is taught on its own terms. Two people on one keyboard each need to see which half is
 * theirs, so every seat is shown its keys on the first co-op run of a visit, whether or not the
 * solo lesson was ever taken; that part is not saved, because the pair might be different next
 * time.
 *
 * Snapshot state throughout, written only on the few frames something changes, so the board's
 * hints appear and disappear as a player acts.
 */
@Stable
class TanksTutorial(private val store: TanksKeyValueStore) {

    /** Saved: this player has moved and fired, and will not be shown the solo lesson again. */
    var done by mutableStateOf(store.getBoolean(KeyDone, false))
        private set

    /** Whether this run is showing any keys at all. */
    var showing by mutableStateOf(false)
        private set

    private var coopRun = false
    private var coopTaughtThisVisit = false
    private val moved = List(2) { mutableStateOf(false) }
    private val fired = List(2) { mutableStateOf(false) }

    /** A first visit starts straight in stage one, lesson on, rather than at a menu. */
    fun isFirstVisit(highestCompletedStage: Int): Boolean = !done && highestCompletedStage == 0

    /** Called as each stage opens. */
    fun beginRun(coop: Boolean) {
        coopRun = coop
        moved.forEach { it.value = false }
        fired.forEach { it.value = false }
        showing = if (coop) {
            !coopTaughtThisVisit.also { coopTaughtThisVisit = true }
        } else {
            !done
        }
    }

    fun showMove(seat: Int): Boolean = showing && !moved[seat.coerceIn(0, 1)].value
    fun showFire(seat: Int): Boolean = showing && !fired[seat.coerceIn(0, 1)].value

    /** A seat's input this frame. The first move and the first shot each put their keys away. */
    fun onInput(seat: Int, direction: BattleCityDirection?, fire: Boolean) {
        if (!showing) return
        val index = seat.coerceIn(0, 1)
        if (direction != null && !moved[index].value) moved[index].value = true
        if (fire && !fired[index].value) fired[index].value = true

        val seats = if (coopRun) 0..1 else 0..0
        if (seats.all { moved[it].value && fired[it].value }) {
            showing = false
            if (!coopRun) markDone()
        }
    }

    /** Ends the solo lesson for good, for a player who has shown they know the keys. */
    fun markDone() {
        if (done) return
        done = true
        store.putBoolean(KeyDone, true)
    }

    companion object {
        /** Also written by the screenshot tool, to open on the menu as a returning player. */
        const val KeyDone = "tanks_tutorial_done"
    }
}
