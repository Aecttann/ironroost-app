package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TanksStreakFreezeTest {
    private val day = 20_000
    private val now = day * 86_400_000L + 12 * 60 * 60 * 1000L

    @Test
    fun storedFreezeProtectsOneFutureMissWithoutGrantingAMissedReward() {
        val first = TanksDailyRewards.claim(TanksDailyState(), day)
        val protected = TanksDailyRewards.earnStreakFreeze(first, now)
        val consecutive = TanksDailyRewards.claim(protected, day + 1)
        assertTrue(consecutive.streakFreezeStored)
        val before = TanksDailyRewards.status(consecutive, day + 3)
        assertTrue(before.streakFreezeWillBeUsed)
        assertFalse(before.streakWillReset)
        val claimed = TanksDailyRewards.claim(consecutive, day + 3)
        assertEquals(3, claimed.streak)
        assertEquals(3, claimed.claimedTotal)
        assertEquals(consecutive.pendingBonusLives + before.reward.bonusLives, claimed.pendingBonusLives)
        assertFalse(claimed.streakFreezeStored)
        assertEquals(claimed, TanksDailyRewards.claim(claimed, day + 3))
    }

    @Test
    fun aFreezeEarnedAfterAMissRestoresTheNextClaimWithoutAdvancingRewards() {
        val missed = TanksDailyState(lastClaimDay = day - 2, lastSeenDay = day, streak = 6, claimedTotal = 6)
        assertTrue(TanksDailyRewards.status(missed, day).streakWillReset)
        val protected = TanksDailyRewards.earnStreakFreeze(missed, now)
        assertEquals(missed.lastClaimDay, protected.lastClaimDay)
        assertEquals(6, protected.streak)
        assertEquals(6, protected.claimedTotal)
        val status = TanksDailyRewards.status(protected, day)
        assertEquals(7, status.cycleDay)
        assertEquals(TanksCollection.CardWeeklyStreak, status.reward.cardId)
        val claimed = TanksDailyRewards.claim(protected, day)
        assertEquals(7, claimed.streak)
        assertEquals(7, claimed.bestStreak)
        assertEquals(7, claimed.claimedTotal)
        assertFalse(claimed.streakFreezeStored)
    }

    @Test
    fun multipleMissedDaysResetTheStreakAndKeepTheFreezeForAFutureStreak() {
        val missed = TanksDailyState(lastClaimDay = day - 3, lastSeenDay = day, streak = 4, streakFreezeStored = true)
        val status = TanksDailyRewards.status(missed, day)
        assertTrue(status.streakWillReset)
        assertFalse(status.streakFreezeWillBeUsed)
        val claimed = TanksDailyRewards.claim(missed, day)
        assertEquals(1, claimed.streak)
        assertTrue(claimed.streakFreezeStored)
    }

    @Test
    fun onlyOneFreezeCanBeStoredAndAnotherRequires48HoursAfterEarning() {
        val missed = TanksDailyState(lastClaimDay = day - 2, lastSeenDay = day, streak = 4)
        val protected = TanksDailyRewards.earnStreakFreeze(missed, now)
        assertEquals(protected, TanksDailyRewards.earnStreakFreeze(protected, now + TanksDailyRewards.StreakFreezeCooldownMillis))
        val consumed = TanksDailyRewards.claim(protected, day)
        assertFalse(TanksDailyRewards.canEarnStreakFreeze(consumed, now + TanksDailyRewards.StreakFreezeCooldownMillis - 1))
        assertTrue(TanksDailyRewards.canEarnStreakFreeze(consumed, now + TanksDailyRewards.StreakFreezeCooldownMillis))
    }

    @Test
    fun aClockRollbackCannotEarnOrConsumeAFreeze() {
        val state = TanksDailyState(lastClaimDay = day - 2, lastSeenDay = day + 1, streak = 4, streakFreezeStored = true)
        assertFalse(TanksDailyRewards.status(state, day).streakFreezeWillBeUsed)
        assertEquals(state, TanksDailyRewards.claim(state, day))
        assertFalse(TanksDailyRewards.canEarnStreakFreeze(state.copy(streakFreezeStored = false), now))
        val earned = state.copy(lastSeenDay = day, streakFreezeStored = false, lastStreakFreezeEarnedAtMillis = now)
        assertEquals(TanksDailyRewards.StreakFreezeCooldownMillis, TanksDailyRewards.streakFreezeCooldownRemaining(earned, now - 1))
    }

    @Test
    fun aFreezeBeforeTheFirstClaimCannotCreateAStreakOrAnExtraReward() {
        val stored = TanksDailyRewards.earnStreakFreeze(TanksDailyState(), now)
        val claimed = TanksDailyRewards.claim(stored, day)
        assertEquals(1, claimed.streak)
        assertEquals(1, claimed.claimedTotal)
        assertTrue(claimed.streakFreezeStored)
    }

    @Test
    fun freezeAndCooldownSurviveSaveEncodingAndOlderSavesHaveNoFreeze() {
        val state = TanksDailyRewards.earnStreakFreeze(TanksDailyState(), now)
        val save = TanksMetaSave(daily = state)
        assertEquals(save, TanksMetaCodec.decode(TanksMetaCodec.encode(save)))
        val old = TanksMetaCodec.decode("""{"daily":{"lastClaimDay":7,"streak":3}}""")
        assertFalse(old.daily.streakFreezeStored)
        assertEquals(null, old.daily.lastStreakFreezeEarnedAtMillis)
        assertEquals(3, old.daily.streak)
    }
}
