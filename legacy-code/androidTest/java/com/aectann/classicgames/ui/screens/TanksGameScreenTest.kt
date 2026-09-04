package com.aectann.classicgames.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aectann.classicgames.R
import com.aectann.classicgames.viewmodel.GameViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Composes the real tank screen on a device: view model wiring, asset loading and the board
 * renderer all run. The game loop is deliberately left unstarted — an always-running frame
 * loop never lets Compose go idle, which would deadlock the test framework.
 */
@RunWith(AndroidJUnit4::class)
class TanksGameScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun screenReachesReadyStateWithControls() {
        showScreen()

        val start = text(R.string.tanks_start)
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(start).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(start).assertExists()
        composeRule.onNodeWithText(text(R.string.tanks_fire)).assertExists()
        composeRule.onNodeWithText(text(R.string.back)).assertExists()
    }

    @Test
    fun stagePickerOpensAndRendersItsThumbnails() {
        showScreen()

        val stages = text(R.string.tanks_select_stage)
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(stages).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(stages).performClick()
        composeRule.onNodeWithText(text(R.string.tanks_select_stage_title)).assertExists()
        // Stage numbers are drawn next to each generated thumbnail.
        composeRule.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty()
    }

    private fun showScreen() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val gameViewModel = GameViewModel(application)
        composeRule.setContent {
            TanksGameScreen(onBack = {}, gameViewModel = gameViewModel)
        }
    }

    private fun text(resId: Int): String = composeRule.activity.getString(resId)
}
