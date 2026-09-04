package com.aectann.battlecity.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TanksDailyRewardsTest {

    private val day = 20_000

    @Test
    fun aFreshSaveCanClaimStraightAway() {
        val status = TanksDailyRewards.status(TanksDailyState(), day)
        assertEquals(TanksDailyAvailability.Claimable, status.availability)
        assertEquals(1, status.cycleDay)
        assertFalse(status.streakWillReset)
    }

    @Test
    fun claimingTwiceOnTheSameDayChangesNothing() {
        val first = TanksDailyRewards.claim(TanksDailyState(), day)
        val second = TanksDailyRewards.claim(first, day)
        assertEquals(first.claimedTotal, second.claimedTotal)
        assertEquals(first.streak, second.streak)
        assertEquals(first.pendingBonusLives, second.pendingBonusLives)
        assertEquals(TanksDailyAvailability.Claimed, TanksDailyRewards.status(second, day).availability)
    }

    @Test
    fun consecutiveDaysGrowTheStreak() {
        var state = TanksDailyState()
        repeat(7) { offset -> state = TanksDailyRewards.claim(state, day + offset) }
        assertEquals(7, state.streak)
        assertEquals(7, state.bestStreak)
        assertEquals(7, state.claimedTotal)
    }

    @Test
    fun aMissedDayResetsTheStreakButKeepsTheBest() {
        var state = TanksDailyState()
        repeat(4) { offset -> state = TanksDailyRewards.claim(state, day + offset) }
        assertEquals(4, state.streak)

        // Skip a day, then claim again.
        state = TanksDailyRewards.claim(state, day + 6)
        assertEquals(1, state.streak)
        assertEquals(4, state.bestStreak)
    }

    @Test
    fun aWarningIsGivenBeforeTheStreakResets() {
        var state = TanksDailyState()
        repeat(3) { offset -> state = TanksDailyRewards.claim(state, day + offset) }
        val status = TanksDailyRewards.status(state, day + 5)
        assertTrue(status.streakWillReset, "the player should be told the streak is gone")
        assertEquals(1, status.cycleDay)
    }

    @Test
    fun theSeventhDayOfAStreakPaysTheCardAndTheMostLives() {
        val reward = TanksDailyRewards.rewardFor(7)
        assertEquals(3, reward.bonusLives)
        assertEquals(TanksCollection.CardWeeklyStreak, reward.cardId)
        assertNull(TanksDailyRewards.rewardFor(6).cardId)
    }

    @Test
    fun theCycleRepeatsAfterSevenDays() {
        var state = TanksDailyState()
        repeat(7) { offset -> state = TanksDailyRewards.claim(state, day + offset) }
        assertEquals(7, state.streak)

        // The eighth consecutive day starts the week over while the streak keeps counting up.
        assertEquals(1, TanksDailyRewards.status(state, day + 7).cycleDay)
        state = TanksDailyRewards.claim(state, day + 7)
        assertEquals(8, state.streak)
        assertEquals(2, TanksDailyRewards.status(state, day + 8).cycleDay)
    }

    @Test
    fun windingTheClockBackDoesNotReopenAClaim() {
        val claimed = TanksDailyRewards.claim(TanksDailyState(), day)
        val status = TanksDailyRewards.status(claimed, day - 3)
        assertEquals(TanksDailyAvailability.ClockBehind, status.availability)

        val attempted = TanksDailyRewards.claim(claimed, day - 3)
        assertEquals(claimed.claimedTotal, attempted.claimedTotal)
        assertEquals(day, attempted.lastSeenDay, "the furthest day seen must not move back")
    }

    @Test
    fun windingTheClockForwardBreaksTheStreakInsteadOfAdvancingIt() {
        var state = TanksDailyState()
        repeat(5) { offset -> state = TanksDailyRewards.claim(state, day + offset) }
        assertEquals(5, state.streak)

        state = TanksDailyRewards.claim(state, day + 60)
        assertEquals(1, state.streak, "a jumped clock must not be worth more than playing daily")
    }

    @Test
    fun bankedLivesTopUpTheNextRunAndAreCapped() {
        var state = TanksDailyState()
        repeat(7) { offset -> state = TanksDailyRewards.claim(state, day + offset) }
        assertTrue(state.pendingBonusLives >= 7)

        assertEquals(
            TanksDailyRewards.MaxStartingLives,
            TanksDailyRewards.startingLives(3, state),
            "banked lives must not stack without limit"
        )
        assertEquals(0, TanksDailyRewards.spendBonusLives(state).pendingBonusLives)
    }

    @Test
    fun touchRecordsProgressOfTimeWithoutClaiming() {
        val touched = TanksDailyRewards.touch(TanksDailyState(), day)
        assertEquals(day, touched.lastSeenDay)
        assertEquals(-1, touched.lastClaimDay)
        assertEquals(day, TanksDailyRewards.touch(touched, day - 5).lastSeenDay)
    }
}

class TanksCollectionTest {

    @Test
    fun everyCardIdIsUnique() {
        val ids = TanksCollection.cards.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate card ids: $ids")
    }

    @Test
    fun everyGroupHasCards() {
        TanksCardGroup.entries.forEach { group ->
            assertTrue(
                TanksCollection.cards.any { it.group == group },
                "no cards in group $group"
            )
        }
    }

    @Test
    fun nothingIsUnlockedOnAFreshSave() {
        assertEquals(0, TanksCollection.unlockedCount(TanksMetaStats(), emptySet()))
    }

    @Test
    fun killsUnlockTheMatchingEnemyCard() {
        val card = TanksCollection.cards.first { it.id == "enemy_basic" }
        val short = TanksMetaStats(enemyKills = mapOf("basic" to card.requirement - 1))
        assertFalse(TanksCollection.isUnlocked(card, short, emptySet()))
        assertEquals(card.requirement - 1, TanksCollection.progress(card, short, emptySet()))

        val enough = TanksMetaStats(enemyKills = mapOf("basic" to card.requirement + 5))
        assertTrue(TanksCollection.isUnlocked(card, enough, emptySet()))
        assertEquals(card.requirement, TanksCollection.progress(card, enough, emptySet()))
    }

    @Test
    fun oneOfEachPowerUpIsEnough() {
        val card = TanksCollection.cards.first { it.id == "powerup_star" }
        assertTrue(TanksCollection.isUnlocked(card, TanksMetaStats(powerUps = mapOf("star" to 1)), emptySet()))
    }

    @Test
    fun milestoneCardsFollowTheFurthestStageCleared() {
        val stats = TanksMetaStats(highestStageCleared = 10)
        val reached = TanksCollection.cards.filter { it.id == "stage_1" || it.id == "stage_5" || it.id == "stage_10" }
        reached.forEach { assertTrue(TanksCollection.isUnlocked(it, stats, emptySet()), "${it.id} should be unlocked") }

        val ahead = TanksCollection.cards.first { it.id == "stage_20" }
        assertFalse(TanksCollection.isUnlocked(ahead, stats, emptySet()))
    }

    @Test
    fun anAwardedCardCountsEvenWithoutTheStats() {
        val card = TanksCollection.cards.first { it.id == TanksCollection.CardWeeklyStreak }
        assertFalse(TanksCollection.isUnlocked(card, TanksMetaStats(), emptySet()))
        assertTrue(TanksCollection.isUnlocked(card, TanksMetaStats(), setOf(card.id)))
    }

    @Test
    fun statsAccumulateAcrossRuns() {
        val stats = TanksMetaStats()
            .plusKills(mapOf("basic" to 3, "fast" to 1))
            .plusKills(mapOf("basic" to 2))
            .plusPowerUps(mapOf("star" to 1))
            .plusPowerUps(mapOf("star" to 1, "helmet" to 1))
        assertEquals(5, stats.enemyKills["basic"])
        assertEquals(1, stats.enemyKills["fast"])
        assertEquals(2, stats.powerUps["star"])
        assertEquals(1, stats.powerUps["helmet"])
    }
}

class TanksLeaderboardTest {

    private fun entry(score: Int, stage: Int = 1) =
        TanksScoreEntry(name = "P$score", score = score, stage = stage, day = 1)

    @Test
    fun scoresAreKeptInDescendingOrder() {
        var board = TanksLeaderboard()
        listOf(300, 100, 500, 200).forEach { board = TanksLeaderboards.insert(board, entry(it)) }
        assertEquals(listOf(500, 300, 200, 100), board.entries.map { it.score })
    }

    @Test
    fun theTableIsCappedAndKeepsTheBest() {
        var board = TanksLeaderboard()
        (1..20).forEach { board = TanksLeaderboards.insert(board, entry(it * 100)) }
        assertEquals(TanksLeaderboards.Capacity, board.entries.size)
        assertEquals(2000, board.entries.first().score)
        assertEquals(1100, board.entries.last().score)
    }

    @Test
    fun tiesAreBrokenByTheFurtherStage() {
        var board = TanksLeaderboard()
        board = TanksLeaderboards.insert(board, entry(500, stage = 2))
        board = TanksLeaderboards.insert(board, entry(500, stage = 9))
        assertEquals(listOf(9, 2), board.entries.map { it.stage })
    }

    @Test
    fun aScorelessRunIsNotRecorded() {
        val board = TanksLeaderboards.insert(TanksLeaderboard(), entry(0))
        assertTrue(board.entries.isEmpty())
    }

    @Test
    fun placementIsReportedOnlyForScoresThatWouldMakeTheTable() {
        var board = TanksLeaderboard()
        (1..TanksLeaderboards.Capacity).forEach { board = TanksLeaderboards.insert(board, entry(it * 100)) }
        assertEquals(1, TanksLeaderboards.placementOf(board, 5000))
        assertNull(TanksLeaderboards.placementOf(board, 50))
        assertNull(TanksLeaderboards.placementOf(board, 0))
    }
}

class TanksNicknameTest {

    @Test
    fun surroundingSpaceIsRemoved() {
        assertEquals("Sasha", TanksNickname.sanitize("  Sasha  "))
    }

    @Test
    fun controlCharactersAreStripped() {
        assertEquals("AB", TanksNickname.sanitize("A\nB\t"))
    }

    @Test
    fun anEmptyNameFallsBackRatherThanBeingStored() {
        assertEquals(TanksNickname.Fallback, TanksNickname.sanitize("   "))
        assertEquals(TanksNickname.Fallback, TanksNickname.sanitize(""))
    }

    @Test
    fun longNamesAreCut() {
        val long = "A".repeat(TanksNickname.MaxLength + 10)
        assertEquals(TanksNickname.MaxLength, TanksNickname.sanitize(long).length)
    }

    @Test
    fun nonLatinNamesSurvive() {
        assertEquals("Олександр", TanksNickname.sanitize("Олександр"))
        assertEquals("坦克", TanksNickname.sanitize("坦克"))
    }
}

class TanksMetaCodecTest {

    @Test
    fun aSaveSurvivesARoundTrip() {
        val save = TanksMetaSave(
            daily = TanksDailyState(lastClaimDay = 5, lastSeenDay = 5, streak = 2, bestStreak = 4),
            stats = TanksMetaStats(enemyKills = mapOf("basic" to 7), highestStageCleared = 3),
            awardedCards = setOf(TanksCollection.CardWeeklyStreak),
            leaderboard = TanksLeaderboard(listOf(TanksScoreEntry("Me", 900, 3, 5))),
            endlessLeaderboard = TanksLeaderboard(listOf(TanksScoreEntry("Me", 1_400, 8, 5))),
            nickname = "Me"
        )
        assertEquals(save, TanksMetaCodec.decode(TanksMetaCodec.encode(save)))
    }

    @Test
    fun nonsenseDecodesToAFreshSave() {
        assertEquals(TanksMetaSave(), TanksMetaCodec.decode("not json at all"))
        assertEquals(TanksMetaSave(), TanksMetaCodec.decode(null))
        assertEquals(TanksMetaSave(), TanksMetaCodec.decode(""))
    }

    @Test
    fun anOlderBlobWithMissingFieldsStillLoads() {
        val decoded = TanksMetaCodec.decode("""{"nickname":"Old"}""")
        assertEquals("Old", decoded.nickname)
        assertEquals(TanksDailyState(), decoded.daily)
    }

    @Test
    fun epochDayCountsWholeUtcDays() {
        assertEquals(0, epochDay(0))
        assertEquals(0, epochDay(86_399_999))
        assertEquals(1, epochDay(86_400_000))
    }
}

class TanksCardSpriteTest {

    /** Card art is addressed by the renderer's own sprite keys; a typo shows as a blank tile. */
    @Test
    fun everyCardSpriteKeyLooksLikeARealSpriteName() {
        TanksCollection.cards.forEach { card ->
            val key = card.spriteKey ?: return@forEach
            assertTrue(key.isNotBlank(), "${card.id} has a blank sprite key")
            assertFalse(key.contains("__"), "${card.id} sprite key looks unfinished: $key")
            assertFalse(key.contains("$"), "${card.id} sprite key kept a template: $key")
        }
    }

    @Test
    fun theArmourCardPointsAtArtThatExists() {
        val card = TanksCollection.cards.first { it.id == "enemy_armor" }
        assertEquals("enemy_armor_green_up", card.spriteKey)
    }

    @Test
    fun enemyCardsUseTheUpFacingSprite() {
        listOf("basic", "fast", "power").forEach { type ->
            val card = TanksCollection.cards.first { it.id == "enemy_$type" }
            assertEquals("enemy_${type}_up", card.spriteKey)
        }
    }
}
