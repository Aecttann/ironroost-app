package com.aectann.battlecity

import com.aectann.battlecity.engine.BattleCityMaxStage
import kotlin.test.Test
import kotlin.test.assertEquals

class TanksProgressRulesTest {

    @Test
    fun firstStageIsUnlockedForNewPlayer() {
        assertEquals(1, highestUnlockedStage(highestCompletedStage = 0, allStagesUnlocked = false))
    }

    @Test
    fun completingStageUnlocksFollowingStage() {
        assertEquals(2, highestUnlockedStage(highestCompletedStage = 1, allStagesUnlocked = false))
        assertEquals(18, highestUnlockedStage(highestCompletedStage = 17, allStagesUnlocked = false))
    }

    @Test
    fun unlockedStageIsCappedAtCampaignEnd() {
        assertEquals(
            BattleCityMaxStage,
            highestUnlockedStage(BattleCityMaxStage, allStagesUnlocked = false)
        )
    }

    @Test
    fun developmentModeUnlocksEntireCampaign() {
        assertEquals(BattleCityMaxStage, highestUnlockedStage(0, allStagesUnlocked = true))
    }
}
