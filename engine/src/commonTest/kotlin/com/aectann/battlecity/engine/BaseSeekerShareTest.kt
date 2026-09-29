package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How much of a wave goes for the base: gentle where a new player starts, full further on. */
class BaseSeekerShareTest {

    @Test
    fun `the opening stages send fewer tanks for the base than the late ones`() {
        val shares = (1..BattleCityMaxDifficulty).map { baseSeekerShare(it, endless = false) }
        assertEquals(shares.sorted(), shares, "the share should never fall as stages get harder")
        assertTrue(shares.first() < 0.15f, "stage one still sends ${shares.first()} of its wave for the base")
        assertEquals(0.3f, shares.last(), 0.0001f)
    }

    @Test
    fun `endless runs at the full share from its first wave`() {
        (1..BattleCityMaxDifficulty).forEach { difficulty ->
            assertEquals(0.3f, baseSeekerShare(difficulty, endless = true), 0.0001f)
        }
    }
}
