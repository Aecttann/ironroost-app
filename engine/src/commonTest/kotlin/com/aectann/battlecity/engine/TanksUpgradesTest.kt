package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TanksUpgradeDraftTest {

    @Test
    fun anOfferIsStableForTheSameRunAndWave() {
        val first = TanksUpgradeDraft.offer(TanksUpgradeLoadout(), wave = 6, seed = 9182L)
        val second = TanksUpgradeDraft.offer(TanksUpgradeLoadout(), wave = 6, seed = 9182L)

        assertEquals(first, second)
        assertEquals(TanksUpgradeDraft.OfferSize, first.size)
        assertEquals(first.size, first.toSet().size)
    }

    @Test
    fun aDifferentWaveRerollsTheDraftWithoutChangingItsRules() {
        val first = TanksUpgradeDraft.offer(TanksUpgradeLoadout(), wave = 2, seed = 41L)
        val second = TanksUpgradeDraft.offer(TanksUpgradeLoadout(), wave = 9, seed = 41L)

        assertNotEquals(first, second)
        assertEquals(TanksUpgradeDraft.OfferSize, second.size)
    }

    @Test
    fun maxedUpgradesAreNeverOffered() {
        var loadout = TanksUpgradeLoadout()
        repeat(TanksUpgrade.RapidFire.maxLevel) {
            loadout = loadout.plus(TanksUpgrade.RapidFire)
        }

        val offer = TanksUpgradeDraft.offer(loadout, wave = 4, seed = 7L)
        assertTrue(TanksUpgrade.RapidFire !in offer)
    }
}

class TanksEndlessWavesTest {

    @Test
    fun pressureAndPopulationGrowButActiveEnemiesStayCapped() {
        assertTrue(TanksEndlessWaves.enemyCount(10) > TanksEndlessWaves.enemyCount(1))
        assertTrue(TanksEndlessWaves.pressure(10) > TanksEndlessWaves.pressure(1))
        assertEquals(28, TanksEndlessWaves.enemyCount(100))
        assertEquals(8, TanksEndlessWaves.maxActiveEnemies(100))
    }

    @Test
    fun everyWaveCompositionAddsUpToItsDeclaredEnemyCount() {
        (1..40).forEach { wave ->
            assertEquals(
                TanksEndlessWaves.enemyCount(wave),
                TanksEndlessWaves.groups(wave).sumOf { it.count },
                "wave $wave"
            )
        }
    }
}
