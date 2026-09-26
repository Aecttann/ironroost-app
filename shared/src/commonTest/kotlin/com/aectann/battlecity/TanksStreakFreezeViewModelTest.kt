@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.aectann.battlecity

import androidx.lifecycle.ViewModelStore
import com.aectann.battlecity.engine.BattleCityGridSize
import com.aectann.battlecity.engine.BattleCityLevelData
import com.aectann.battlecity.engine.BattleCitySpawnPoints
import com.aectann.battlecity.engine.FreePlayWallet
import com.aectann.battlecity.engine.InMemoryProgressStore
import com.aectann.battlecity.engine.NoopTanksAnalytics
import com.aectann.battlecity.engine.TanksCollection
import com.aectann.battlecity.engine.TanksDailyRewards
import com.aectann.battlecity.engine.TanksDailyState
import com.aectann.battlecity.engine.TanksMetaCodec
import com.aectann.battlecity.engine.TanksMetaSave
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TanksStreakFreezeViewModelTest {
    private val day = 20_000
    private val now = day * 86_400_000L + 12 * 60 * 60 * 1000L
    private val missed = TanksDailyState(lastClaimDay = day - 2, lastSeenDay = day, streak = 6, claimedTotal = 6)
    private val level = BattleCityLevelData(
        id = "freeze-test", note = null, gridSize = BattleCityGridSize(3, 3),
        spawnPoints = BattleCitySpawnPoints(enemy = listOf(listOf(0, 0)), player1 = listOf(0, 1),
            player2 = listOf(2, 1), base = listOf(1, 2)), tilesLegend = null,
        grid = listOf("...", "...", ".H."), enemyGroups = emptyList(), difficulty = 1
    )

    private fun withDaily(block: TestScope.(TanksViewModel, TanksMetaRepository, InMemoryKeyValueStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModels = ViewModelStore()
        try {
            val storage = InMemoryKeyValueStore()
            storage.putString("tanks_meta_save_v1", TanksMetaCodec.encode(TanksMetaSave(daily = missed)))
            val repository = TanksMetaRepository(storage) { now }
            val game = TanksViewModel(FreePlayWallet, InMemoryProgressStore(), NoopTanksAnalytics, repository,
                levelLoader = { level }, stageInfosLoader = { emptyMap() })
            viewModels.put("game", game)
            runCurrent()
            block(game, repository, storage)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rewardProtectsAMissedDayAndTheWeeklyRewardIsGrantedExactlyOnceOnClaim() = withDaily { game, repository, _ ->
        val ads = DeferredFreezeAds()
        game.earnStreakFreezeWithAd(ads)
        runCurrent()
        assertTrue(game.session.value.streakFreezeInProgress)
        game.claimDaily()
        assertEquals(6, repository.save().daily.claimedTotal)
        ads.result.complete(RewardedAdResult.Earned)
        runCurrent()
        assertFalse(game.session.value.streakFreezeInProgress)
        assertTrue(game.meta.value.daily.streakFreezeStored)
        assertTrue(game.meta.value.daily.streakFreezeWillBeUsed)
        assertTrue(game.meta.value.awardedCards.isEmpty())
        game.claimDaily()
        game.claimDaily()
        assertEquals(7, game.meta.value.daily.streak)
        assertEquals(7, repository.save().daily.claimedTotal)
        assertEquals(3, game.meta.value.pendingBonusLives)
        assertEquals(setOf(TanksCollection.CardWeeklyStreak), game.meta.value.awardedCards)
        assertFalse(game.meta.value.daily.streakFreezeStored)
        assertFalse(game.meta.value.canEarnStreakFreeze)
    }

    @Test
    fun duplicateTapsOrAnExistingFreezeCannotEarnTwoRewards() = withDaily { game, _, _ ->
        val ads = DeferredFreezeAds()
        game.earnStreakFreezeWithAd(ads)
        game.earnStreakFreezeWithAd(ads)
        runCurrent()
        assertEquals(1, ads.shows)
        ads.result.complete(RewardedAdResult.Earned)
        runCurrent()
        game.earnStreakFreezeWithAd(ads)
        runCurrent()
        assertEquals(1, ads.shows)
        assertTrue(game.meta.value.daily.streakFreezeStored)
    }

    @Test
    fun closingOrFailingAnAdNeverGrantsAFreezeOrPreventsClaiming() {
        listOf(RewardedAdResult.NotEarned, RewardedAdResult.Failed, RewardedAdResult.Unavailable).forEach { result ->
            withDaily { game, _, _ ->
                val ads = DeferredFreezeAds()
                ads.result.complete(result)
                game.earnStreakFreezeWithAd(ads)
                runCurrent()
                assertFalse(game.meta.value.daily.streakFreezeStored)
                assertFalse(game.session.value.streakFreezeInProgress)
                assertEquals(result, game.session.value.streakFreezeResult)
                game.claimDaily()
                assertEquals(1, game.meta.value.daily.streak)
            }
        }
    }

    @Test
    fun earnedFreezeAndItsCooldownPersistAcrossRepositoryRestart() = withDaily { game, _, storage ->
        val ads = DeferredFreezeAds()
        ads.result.complete(RewardedAdResult.Earned)
        game.earnStreakFreezeWithAd(ads)
        runCurrent()
        val restarted = TanksMetaRepository(storage) { now }
        assertTrue(restarted.dailyStatus().streakFreezeStored)
        restarted.claimDaily()
        val reopened = TanksMetaRepository(storage) { now }
        assertFalse(reopened.dailyStatus().streakFreezeStored)
        assertEquals(7, reopened.dailyStatus().streak)
        assertEquals(TanksDailyRewards.StreakFreezeCooldownMillis, reopened.streakFreezeCooldownRemaining())
        assertFalse(reopened.canEarnStreakFreeze())
    }

    @Test
    fun hostsWithoutThePlacementDoNotShowOrGrantAFreeze() = withDaily { game, _, _ ->
        val ads = DeferredFreezeAds(supported = false)
        game.earnStreakFreezeWithAd(ads)
        runCurrent()
        assertEquals(0, ads.shows)
        assertFalse(game.meta.value.daily.streakFreezeStored)
    }

    private class DeferredFreezeAds(private val supported: Boolean = true) : TanksAds {
        override val supportsResurrection = false
        override val supportsStreakFreeze get() = supported
        override val state = MutableStateFlow(TanksAdsState(streakFreeze = RewardedAdAvailability.Ready))
        val result = CompletableDeferred<RewardedAdResult>()
        var shows = 0
        override suspend fun showResurrection() = RewardedAdResult.Unavailable
        override suspend fun showStreakFreeze(): RewardedAdResult {
            shows++
            return result.await()
        }
        override fun showPrivacyOptions() = Unit
    }
}
