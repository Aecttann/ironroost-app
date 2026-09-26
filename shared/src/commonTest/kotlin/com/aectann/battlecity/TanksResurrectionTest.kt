@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.aectann.battlecity

import androidx.lifecycle.ViewModelStore
import com.aectann.battlecity.engine.BattleCityEnemyGroup
import com.aectann.battlecity.engine.BattleCityGridSize
import com.aectann.battlecity.engine.BattleCityInput
import com.aectann.battlecity.engine.BattleCityInputs
import com.aectann.battlecity.engine.BattleCityLevelData
import com.aectann.battlecity.engine.BattleCitySpawnPoints
import com.aectann.battlecity.engine.FreePlayWallet
import com.aectann.battlecity.engine.InMemoryProgressStore
import com.aectann.battlecity.engine.NoopTanksAnalytics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TanksResurrectionTest {
    private val level = BattleCityLevelData(
        id = "resurrection-test", note = null, gridSize = BattleCityGridSize(7, 7),
        spawnPoints = BattleCitySpawnPoints(
            enemy = listOf(listOf(3, 0)), player1 = listOf(3, 4),
            player2 = listOf(5, 4), base = listOf(3, 6)
        ),
        tilesLegend = null,
        grid = listOf(".......", ".......", ".......", ".......", ".......", "SSSSSSS", "SSSHSSS"),
        enemyGroups = listOf(BattleCityEnemyGroup("basic", 20)), difficulty = 1
    )

    private fun withGame(enabled: Boolean = true, block: TestScope.(TanksViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val game = TanksViewModel(
                wallet = FreePlayWallet, progress = InMemoryProgressStore(), analytics = NoopTanksAnalytics,
                metaRepository = TanksMetaRepository(InMemoryKeyValueStore()) { 1_800_000_000_000L },
                seedProvider = { 42L }, resurrectionEnabled = enabled,
                levelLoader = { level }, stageInfosLoader = { emptyMap() }
            )
            store.put("game", game)
            runCurrent()
            game.startOrResume()
            block(game)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private fun lose(game: TanksViewModel, earnScore: Boolean = true) {
        repeat(18_000) {
            if (game.session.value.phase == TanksPhase.Playing) {
                val fire = earnScore && game.render.value!!.stageScore == 0
                game.advance(1f / 60f, BattleCityInputs(BattleCityInput(null, fire)))
            }
        }
        assertEquals(TanksPhase.GameOver, game.session.value.phase, "state: ${game.render.value}")
        assertFalse(game.render.value!!.baseDestroyed)
        if (earnScore) assertTrue(game.render.value!!.stageScore > 0)
    }

    @Test
    fun earnedRewardRestoresTheSameRunAndItsLossIsRecordedOnlyOnce() = withGame { game ->
        lose(game)
        val score = game.render.value!!.stageScore
        assertTrue(game.meta.value.leaderboard.entries.isEmpty())
        val ads = DeferredAds()
        game.resurrectWithAd(ads)
        runCurrent()
        ads.result.complete(RewardedAdResult.Earned)
        runCurrent()
        assertEquals(TanksPhase.Paused, game.session.value.phase)
        assertEquals(1, game.render.value!!.lives)
        assertEquals(score, game.render.value!!.stageScore)
        assertTrue(game.meta.value.leaderboard.entries.isEmpty())
        game.startOrResume()
        lose(game, earnScore = false)
        game.finishLostRun()
        game.finishLostRun()
        assertEquals(1, game.meta.value.leaderboard.entries.size)
        assertEquals(game.render.value!!.stageScore, game.meta.value.leaderboard.entries.single().score)
        assertEquals(game.render.value!!.killsByType, game.meta.value.stats.enemyKills)
    }

    @Test
    fun duplicateTapsCannotShowOrGrantTwoRewards() = withGame { game ->
        lose(game)
        val ads = DeferredAds()
        game.resurrectWithAd(ads)
        game.resurrectWithAd(ads)
        runCurrent()
        assertEquals(1, ads.showCount)
        ads.result.complete(RewardedAdResult.Earned)
        runCurrent()
        game.resurrectWithAd(ads)
        runCurrent()
        assertEquals(1, ads.showCount)
        assertEquals(1, game.render.value!!.lives)
    }

    @Test
    fun closingWithoutRewardOrFailingNeverResurrects() {
        listOf(RewardedAdResult.NotEarned, RewardedAdResult.Unavailable, RewardedAdResult.Failed).forEach { result ->
            withGame { game ->
                lose(game)
                val ads = DeferredAds()
                ads.result.complete(result)
                game.resurrectWithAd(ads)
                runCurrent()
                assertEquals(TanksPhase.GameOver, game.session.value.phase)
                assertEquals(0, game.render.value!!.lives)
                assertEquals(result, game.session.value.resurrectionResult)
                assertFalse(game.session.value.resurrectionInProgress)
                game.retryAfterLoss()
                runCurrent()
                assertEquals(1, game.meta.value.leaderboard.entries.size)
                assertEquals(3, game.render.value!!.lives)
            }
        }
    }

    @Test
    fun anOldRewardCannotReviveANewRun() = withGame { game ->
        lose(game)
        val ads = DeferredAds()
        game.resurrectWithAd(ads)
        runCurrent()
        game.selectStage(1)
        runCurrent()
        ads.result.complete(RewardedAdResult.Earned)
        runCurrent()
        assertEquals(TanksPhase.Ready, game.session.value.phase)
        assertEquals(3, game.render.value!!.lives)
        assertEquals(0, game.render.value!!.stageScore)
        assertEquals(1, game.meta.value.leaderboard.entries.size)
    }

    @Test
    fun hostsWithoutAdsKeepImmediateLossRecording() = withGame(enabled = false) { game ->
        lose(game)
        assertFalse(game.session.value.canResurrect)
        assertEquals(1, game.meta.value.leaderboard.entries.size)
    }

    @Test
    fun processRestartCommitsAPendingLossOnceWithItsOriginalDate() {
        val storage = InMemoryKeyValueStore()
        val before = TanksMetaRepository(storage) { 1_800_000_000_000L }
        before.prepareRunLoss(mapOf("basic" to 2), emptyMap(), 2, 200, 4, endless = false)
        val day = before.today()
        assertTrue(before.save().leaderboard.entries.isEmpty())
        val recovered = TanksMetaRepository(storage) { 1_900_000_000_000L }
        assertEquals(mapOf("basic" to 2), recovered.stats().enemyKills)
        assertEquals(day, recovered.leaderboard().entries.single().day)
        assertEquals(200, recovered.leaderboard().entries.single().score)
        val reopened = TanksMetaRepository(storage) { 1_900_000_000_000L }
        assertEquals(1, reopened.leaderboard().entries.size)
        assertEquals(mapOf("basic" to 2), reopened.stats().enemyKills)
    }

    @Test
    fun earnedResurrectionDiscardsPersistedLossAndEndlessLossKeepsItsOwnBoard() {
        val storage = InMemoryKeyValueStore()
        val repository = TanksMetaRepository(storage) { 1_800_000_000_000L }
        repository.prepareRunLoss(mapOf("basic" to 2), emptyMap(), 2, 200, 4, endless = true)
        repository.discardPendingLoss()
        val reopened = TanksMetaRepository(storage) { 1_800_000_000_000L }
        assertTrue(reopened.endlessLeaderboard().entries.isEmpty())
        assertTrue(reopened.stats().enemyKills.isEmpty())
        reopened.prepareRunLoss(mapOf("basic" to 3), emptyMap(), 2, 300, 5, endless = true)
        reopened.finishPendingLoss()
        reopened.finishPendingLoss()
        assertTrue(reopened.leaderboard().entries.isEmpty())
        assertEquals(300, reopened.endlessLeaderboard().entries.single().score)
        assertEquals(5, reopened.stats().bestEndlessWave)
        assertEquals(mapOf("basic" to 3), reopened.stats().enemyKills)
    }

    private class DeferredAds : TanksAds {
        override val supportsResurrection = true
        override val state = MutableStateFlow(TanksAdsState(resurrection = RewardedAdAvailability.Ready))
        val result = CompletableDeferred<RewardedAdResult>()
        var showCount = 0
        override suspend fun showResurrection(): RewardedAdResult {
            showCount++
            return result.await()
        }
        override fun showPrivacyOptions() = Unit
    }

}
