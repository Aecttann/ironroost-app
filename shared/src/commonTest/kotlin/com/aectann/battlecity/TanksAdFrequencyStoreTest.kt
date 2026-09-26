package com.aectann.battlecity

import kotlin.test.Test
import kotlin.test.assertEquals

class TanksAdFrequencyStoreTest {
    @Test
    fun placementCapsAreIndependentAndExpireAtTheExactBoundary() {
        var now = 1_800_000_000_000L
        val store = InMemoryKeyValueStore()
        val frequency = TanksAdFrequencyStore(store) { now }
        frequency.recordShown(TanksRewardedPlacement.Resurrection)
        assertEquals(300_000L, frequency.remainingMillis(TanksRewardedPlacement.Resurrection))
        assertEquals(0L, frequency.remainingMillis(TanksRewardedPlacement.StreakFreeze))
        frequency.recordShown(TanksRewardedPlacement.StreakFreeze)
        now += 299_999
        assertEquals(1L, frequency.remainingMillis(TanksRewardedPlacement.Resurrection))
        now++
        assertEquals(0L, frequency.remainingMillis(TanksRewardedPlacement.Resurrection))
        assertEquals(172_500_000L, frequency.remainingMillis(TanksRewardedPlacement.StreakFreeze))
        now += 172_500_000
        assertEquals(0L, frequency.remainingMillis(TanksRewardedPlacement.StreakFreeze))
    }

    @Test
    fun restartingTheOwnerDoesNotResetTheCapAndRollbackDoesNotShortenIt() {
        var now = 1_800_000_000_000L
        val storage = InMemoryKeyValueStore()
        TanksAdFrequencyStore(storage) { now }.recordShown(TanksRewardedPlacement.StreakFreeze)
        now += 86_400_000
        val restarted = TanksAdFrequencyStore(storage) { now }
        assertEquals(86_400_000L, restarted.remainingMillis(TanksRewardedPlacement.StreakFreeze))
        now -= 2 * 86_400_000
        assertEquals(172_800_000L, restarted.remainingMillis(TanksRewardedPlacement.StreakFreeze))
    }
}
