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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .safeContentPadding()
    ) {
        if (maxWidth >= 700.dp) {
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
                    onDaily = onDaily
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
                    onDaily = onDaily
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
        Text(
            text = stringResource(TanksStrings.appName),
            color = Color.White,
            fontSize = 34.sp,
            textAlign = TextAlign.Center
        )
        if (highestCompletedStage > 0) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(TanksStrings.highestCompletedStage, highestCompletedStage),
                color = MutedText,
                fontSize = 13.sp,
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
    modifier: Modifier = Modifier
) {
    val buttonWidth = Modifier.fillMaxWidth().widthIn(min = 220.dp, max = 360.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SeatSelector(buttonWidth, playerCount, coopAvailable, onPlayerCountChange)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onNewGame, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.menuNewGame))
        }
        if (highestCompletedStage > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onContinue, modifier = buttonWidth) {
                Text(stringResource(TanksStrings.menuContinue))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onEndless, modifier = buttonWidth) {
            Text(
                if (bestEndlessWave > 0) {
                    stringResource(TanksStrings.menuEndless) + "  ·  " +
                        stringResource(TanksStrings.menuEndlessBest, bestEndlessWave)
                } else {
                    stringResource(TanksStrings.menuEndless)
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDaily, modifier = buttonWidth) {
            Text(
                if (dailyClaimable) "! " + stringResource(TanksStrings.menuDaily)
                else stringResource(TanksStrings.menuDaily)
            )
        }
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
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedButton(onClick = onCollection, modifier = buttonWidth) {
            Text(
                stringResource(TanksStrings.menuCollection) + "  " +
                    stringResource(TanksStrings.collectionProgress, collectionUnlocked, collectionTotal)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onLeaderboard, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.menuLeaderboard))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onSettings, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.menuSettings))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onAbout, modifier = buttonWidth) {
            Text(stringResource(TanksStrings.menuAbout))
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeatOption(
                modifier = Modifier.weight(1f),
                label = stringResource(TanksStrings.menuPlayersOne),
                selected = playerCount <= 1,
                enabled = true,
                onClick = { onPlayerCountChange(1) }
            )
            SeatOption(
                modifier = Modifier.weight(1f),
                label = stringResource(TanksStrings.menuPlayersTwo),
                selected = coopAvailable && playerCount > 1,
                enabled = coopAvailable,
                onClick = { onPlayerCountChange(2) }
            )
        }
        if (!coopAvailable) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(TanksStrings.menuCoopNeedsKeyboard),
                color = MutedText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SeatOption(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(label, fontSize = 13.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(
                text = label,
                color = if (enabled) MutedText else MutedText.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }
    }
}

/** Shared chrome for the settings and about pages. */
@Composable
internal fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .safeContentPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = Color.White, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.widthIn(min = 200.dp)) {
            Text(stringResource(TanksStrings.commonBack))
        }
    }
}
