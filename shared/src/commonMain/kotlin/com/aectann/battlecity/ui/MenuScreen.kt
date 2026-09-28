package com.aectann.battlecity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aectann.battlecity.TanksStrings
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The app's front door. Deliberately small: the ways into the game plus settings and the
 * legal note. Stage picking lives in the pause menu, where the player already is.
 *
 * The seat count is a toggle above the entries rather than a pair of duplicated buttons,
 * because it applies to both the campaign and endless — spelling out all four combinations
 * would double the menu to say very little.
 */
@Composable
fun MenuScreen(
    highestCompletedStage: Int,
    titleSprite: ImageBitmap?,
    dailyClaimable: Boolean,
    collectionUnlocked: Int,
    collectionTotal: Int,
    bestEndlessWave: Int,
    playerCount: Int,
    /** False on a device with no keys to drive the second seat with. */
    coopAvailable: Boolean,
    onPlayerCountChange: (Int) -> Unit,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onEndless: () -> Unit,
    onDaily: () -> Unit,
    onCollection: () -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    // Enter on a fresh menu starts a new run.
    val newGame = rememberInitialFocus()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .safeContentPadding()
            .pixelMenuKeys()
    ) {
        // Three columns only on a screen that is wide and lies on its side. A tall 720 × 1080
        // window clears the width too, but squeezed its title column until the name broke in two.
        if (maxWidth >= 700.dp && maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuBrand(
                    modifier = Modifier.weight(0.78f),
                    titleSprite = titleSprite,
                    highestCompletedStage = highestCompletedStage,
                    spriteSize = 88
                )
                PrimaryMenuActions(
                    modifier = Modifier.weight(1.15f),
                    highestCompletedStage = highestCompletedStage,
                    dailyClaimable = dailyClaimable,
                    bestEndlessWave = bestEndlessWave,
                    playerCount = playerCount,
                    coopAvailable = coopAvailable,
                    onPlayerCountChange = onPlayerCountChange,
                    onNewGame = onNewGame,
                    onContinue = onContinue,
                    onEndless = onEndless,
                    onDaily = onDaily,
                    newGameFocus = newGame
                )
                SecondaryMenuActions(
                    modifier = Modifier.weight(1f),
                    collectionUnlocked = collectionUnlocked,
                    collectionTotal = collectionTotal,
                    onCollection = onCollection,
                    onLeaderboard = onLeaderboard,
                    onSettings = onSettings,
                    onAbout = onAbout
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MenuBrand(
                    titleSprite = titleSprite,
                    highestCompletedStage = highestCompletedStage,
                    spriteSize = 96
                )
                Spacer(modifier = Modifier.height(26.dp))
                PrimaryMenuActions(
                    highestCompletedStage = highestCompletedStage,
                    dailyClaimable = dailyClaimable,
                    bestEndlessWave = bestEndlessWave,
                    playerCount = playerCount,
                    coopAvailable = coopAvailable,
                    onPlayerCountChange = onPlayerCountChange,
                    onNewGame = onNewGame,
                    onContinue = onContinue,
                    onEndless = onEndless,
                    onDaily = onDaily,
                    newGameFocus = newGame
                )
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryMenuActions(
                    collectionUnlocked = collectionUnlocked,
                    collectionTotal = collectionTotal,
                    onCollection = onCollection,
                    onLeaderboard = onLeaderboard,
                    onSettings = onSettings,
                    onAbout = onAbout
                )
            }
        }
    }
}

@Composable
private fun MenuBrand(
    titleSprite: ImageBitmap?,
    highestCompletedStage: Int,
    spriteSize: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (titleSprite != null) {
            Canvas(modifier = Modifier.size(spriteSize.dp)) {
                val side = size.minDimension.roundToInt()
                drawImage(
                    image = titleSprite,
                    dstOffset = IntOffset(
                        ((size.width - side) / 2f).roundToInt(),
                        ((size.height - side) / 2f).roundToInt()
                    ),
                    dstSize = IntSize(side, side),
                    filterQuality = FilterQuality.None
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        PixelTitle(
            text = stringResource(TanksStrings.appName),
            color = Color.White,
            style = LocalPixelType.current.display
        )
        if (highestCompletedStage > 0) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(TanksStrings.highestCompletedStage, highestCompletedStage),
                color = MutedText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PrimaryMenuActions(
    highestCompletedStage: Int,
    dailyClaimable: Boolean,
    bestEndlessWave: Int,
    playerCount: Int,
    /** False on a device with no keys to drive the second seat with. */
    coopAvailable: Boolean,
    onPlayerCountChange: (Int) -> Unit,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onEndless: () -> Unit,
    onDaily: () -> Unit,
    newGameFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val buttonWidth = Modifier.fillMaxWidth().widthIn(min = 220.dp, max = 360.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        SeatSelector(buttonWidth, playerCount, coopAvailable, onPlayerCountChange)
        Spacer(modifier = Modifier.height(2.dp))
        PixelButton(
            text = stringResource(TanksStrings.menuNewGame),
            onClick = onNewGame,
            modifier = buttonWidth,
            focusRequester = newGameFocus
        )
        if (highestCompletedStage > 0) {
            PixelButton(stringResource(TanksStrings.menuContinue), onContinue, buttonWidth)
        }
        PixelButton(
            text = if (bestEndlessWave > 0) {
                stringResource(TanksStrings.menuEndless) + "  ·  " +
                    stringResource(TanksStrings.menuEndlessBest, bestEndlessWave)
            } else {
                stringResource(TanksStrings.menuEndless)
            },
            onClick = onEndless,
            modifier = buttonWidth
        )
        // A reward waiting is a badge on the button, not a character in its label.
        PixelButton(
            text = stringResource(TanksStrings.menuDaily),
            onClick = onDaily,
            modifier = buttonWidth,
            material = PixelMaterial.Gold,
            badge = dailyClaimable
        )
    }
}

@Composable
private fun SecondaryMenuActions(
    collectionUnlocked: Int,
    collectionTotal: Int,
    onCollection: () -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonWidth = Modifier.fillMaxWidth().widthIn(min = 220.dp, max = 360.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        val steel = PixelMaterial.Steel
        PixelButton(
            text = stringResource(TanksStrings.menuCollection) + "  " +
                stringResource(TanksStrings.collectionProgress, collectionUnlocked, collectionTotal),
            onClick = onCollection,
            modifier = buttonWidth,
            material = steel
        )
        PixelButton(stringResource(TanksStrings.menuLeaderboard), onLeaderboard, buttonWidth, material = steel)
        PixelButton(stringResource(TanksStrings.menuSettings), onSettings, buttonWidth, material = steel)
        PixelButton(stringResource(TanksStrings.menuAbout), onAbout, buttonWidth, material = steel)
    }
}

/**
 * One-or-two seats, as a pair of segments rather than a switch: the labels have to name what
 * each option is, and a bare toggle beside the word "players" reads as an on/off.
 *
 * Without a keyboard the second segment is shown disabled with the reason underneath rather than
 * hidden. Hiding it would read as the mode not existing, and on a phone that is the wrong thing
 * to learn — the same player on a desktop has it. Disabled-with-a-reason also fails safely if
 * the keyboard probe is wrong: the player can see what they are missing and why.
 */
@Composable
private fun SeatSelector(
    modifier: Modifier,
    playerCount: Int,
    coopAvailable: Boolean,
    onPlayerCountChange: (Int) -> Unit
) {
    Column(modifier = modifier) {
        PixelSegmented(
            options = listOf(1, 2),
            selected = if (coopAvailable && playerCount > 1) 2 else 1,
            label = { seats ->
                stringResource(if (seats == 1) TanksStrings.menuPlayersOne else TanksStrings.menuPlayersTwo)
            },
            onSelect = onPlayerCountChange,
            isEnabled = { seats -> seats == 1 || coopAvailable },
            modifier = Modifier.fillMaxWidth()
        )
        if (!coopAvailable) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(TanksStrings.menuCoopNeedsKeyboard),
                color = MutedText,
                style = LocalPixelType.current.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Shared chrome for the pages off the menu: a lettered title, the page, and Back. Back takes
 * focus as the page opens unless [backTakesFocus] is off because the page has a better
 * first choice of its own (the daily claim, say).
 */
@Composable
internal fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    backTakesFocus: Boolean = true,
    content: @Composable () -> Unit
) {
    val back = rememberInitialFocus(enabled = backTakesFocus)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .safeContentPadding()
            .padding(24.dp)
            .pixelMenuKeys(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PixelTitle(text = title, style = LocalPixelType.current.title)
        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).weight(1f)) { content() }
        Spacer(modifier = Modifier.height(16.dp))
        PixelButton(
            text = stringResource(TanksStrings.commonBack),
            onClick = onBack,
            enabled = backEnabled,
            modifier = Modifier.widthIn(min = 220.dp),
            material = PixelMaterial.Steel,
            focusRequester = back
        )
    }
}
