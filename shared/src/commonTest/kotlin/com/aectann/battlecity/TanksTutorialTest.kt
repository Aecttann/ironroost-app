package com.aectann.battlecity

import com.aectann.battlecity.engine.BattleCityDirection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The keys drawn next to a new player's tank: when they show, and that each goes once used. */
class TanksTutorialTest {

    @Test
    fun `a new player starts in the game and loses each hint as they use it`() {
        val store = InMemoryKeyValueStore()
        val tutorial = TanksTutorial(store)
        assertTrue(tutorial.isFirstVisit(highestCompletedStage = 0))

        tutorial.beginRun(coop = false)
        assertTrue(tutorial.showMove(0))
        assertTrue(tutorial.showFire(0))

        tutorial.onInput(0, BattleCityDirection.Up, fire = false)
        assertFalse(tutorial.showMove(0), "moving should put the move keys away")
        assertTrue(tutorial.showFire(0))
        assertFalse(tutorial.done, "not done until the player has also fired")

        tutorial.onInput(0, null, fire = true)
        assertFalse(tutorial.showing)
        assertTrue(tutorial.done)
        assertTrue(store.getBoolean(TanksTutorial.KeyDone, false), "the lesson should be saved")
    }

    @Test
    fun `a returning player is not taught again, nor sent past the menu`() {
        val store = InMemoryKeyValueStore().apply { putBoolean(TanksTutorial.KeyDone, true) }
        val tutorial = TanksTutorial(store)
        assertFalse(tutorial.isFirstVisit(highestCompletedStage = 0))

        tutorial.beginRun(coop = false)
        assertFalse(tutorial.showMove(0))
        assertFalse(tutorial.showFire(0))

        // Nor is someone with progress from before the lesson existed.
        assertFalse(TanksTutorial(InMemoryKeyValueStore()).isFirstVisit(highestCompletedStage = 3))
    }

    @Test
    fun `a lesson left halfway comes back on the next stage`() {
        val tutorial = TanksTutorial(InMemoryKeyValueStore())
        tutorial.beginRun(coop = false)
        tutorial.onInput(0, BattleCityDirection.Left, fire = false)

        tutorial.beginRun(coop = false)
        assertTrue(tutorial.showMove(0))
        assertTrue(tutorial.showFire(0))
    }

    @Test
    fun `co-op shows both seats their keys once a visit and saves nothing`() {
        val store = InMemoryKeyValueStore().apply { putBoolean(TanksTutorial.KeyDone, true) }
        val tutorial = TanksTutorial(store)

        tutorial.beginRun(coop = true)
        assertTrue(tutorial.showMove(0))
        assertTrue(tutorial.showMove(1))

        tutorial.onInput(0, BattleCityDirection.Up, fire = true)
        assertFalse(tutorial.showMove(0))
        assertTrue(tutorial.showMove(1), "the second seat has not moved yet")
        assertTrue(tutorial.showing)

        tutorial.onInput(1, BattleCityDirection.Down, fire = true)
        assertFalse(tutorial.showing)

        tutorial.beginRun(coop = true)
        assertFalse(tutorial.showing, "the pair has already been shown their keys this visit")
    }

    @Test
    fun `co-op does not count as the solo lesson`() {
        val store = InMemoryKeyValueStore()
        val tutorial = TanksTutorial(store)
        tutorial.beginRun(coop = true)
        tutorial.onInput(0, BattleCityDirection.Up, fire = true)
        tutorial.onInput(1, BattleCityDirection.Up, fire = true)

        assertFalse(tutorial.done)
        tutorial.beginRun(coop = false)
        assertTrue(tutorial.showing)
    }
}
