package com.aectann.battlecity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aectann.battlecity.TanksAssets
import com.aectann.battlecity.TanksMetaUi
import com.aectann.battlecity.TanksStrings
import com.aectann.battlecity.TanksAdsState
import com.aectann.battlecity.RewardedAdAvailability
import com.aectann.battlecity.RewardedAdResult
import com.aectann.battlecity.engine.TanksCard
import com.aectann.battlecity.engine.TanksCardGroup
import com.aectann.battlecity.engine.TanksCollection
import com.aectann.battlecity.engine.TanksDailyAvailability
import com.aectann.battlecity.engine.TanksDailyRewards
import com.aectann.battlecity.engine.TanksNickname
import com.aectann.battlecity.engine.TanksScoreEntry
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// -------------------------------------------------------------------- daily

@Composable
fun DailyRewardScreen(
    meta: TanksMetaUi,
    onClaim: () -> Unit,
    onBack: () -> Unit,
    supportsStreakFreeze: Boolean = false,
    adState: TanksAdsState = TanksAdsState(),
    freezeInProgress: Boolean = false,
    freezeResult: RewardedAdResult? = null,
    onFreeze: () -> Unit = {}
) {
    SubScreenScaffold(title = stringResource(TanksStrings.dailyTitle), onBack = onBack, backEnabled = !freezeInProgress) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val daily = meta.daily

            Text(
                text = stringResource(TanksStrings.dailyStreak, daily.streak),
                color = AccentGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(TanksStrings.dailyBestStreak, daily.bestStreak),
                color = MutedText,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
            WeekStrip(currentCycleDay = daily.cycleDay, claimable = daily.availability)
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(TanksStrings.dailyRewardLives, daily.reward.bonusLives),
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(TanksStrings.dailyRewardCard),
                color = MutedText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            if (daily.streakWillReset) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(TanksStrings.dailyStreakWarning),
                    color = BrickRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            when (daily.availability) {
                TanksDailyAvailability.Claimable -> Button(
                    onClick = onClaim,
                    enabled = !freezeInProgress,
                    modifier = Modifier.widthIn(min = 220.dp)
                ) { Text(stringResource(TanksStrings.dailyClaim)) }

                TanksDailyAvailability.Claimed -> Text(
                    text = stringResource(TanksStrings.dailyClaimed),
                    color = PlayerGreen,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                TanksDailyAvailability.ClockBehind -> Text(
                    text = stringResource(TanksStrings.dailyClockBehind),
                    color = BrickRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (meta.pendingBonusLives > 0) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(TanksStrings.dailyBanked, meta.pendingBonusLives),
                    color = PlayerGreen,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (supportsStreakFreeze) {
                Spacer(Modifier.height(24.dp))
                if (daily.streakFreezeStored) {
                    Text(stringResource(if (daily.streakFreezeWillBeUsed) TanksStrings.dailyFreezeProtectClaim
                        else TanksStrings.dailyFreezeStored), color = PlayerGreen, fontSize = 14.sp,
                        textAlign = TextAlign.Center)
                } else {
                    Text(stringResource(TanksStrings.dailyFreezeOffer), color = MutedText, fontSize = 13.sp,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onFreeze, enabled = meta.canEarnStreakFreeze &&
                        adState.streakFreeze == RewardedAdAvailability.Ready && !freezeInProgress) {
                        Text(stringResource(if (freezeInProgress) TanksStrings.resurrectionWatching else TanksStrings.dailyFreezeWatch))
                    }
                    val cooldownHours = maxOf(meta.streakFreezeCooldownHours, adState.streakFreezeCooldownHours)
                    when {
                        freezeInProgress -> Unit
                        cooldownHours > 0 -> Text(stringResource(TanksStrings.dailyFreezeCooldown, cooldownHours),
                            color = MutedText, fontSize = 12.sp, textAlign = TextAlign.Center)
                        adState.streakFreeze == RewardedAdAvailability.Loading -> Text(stringResource(TanksStrings.resurrectionLoading), color = MutedText)
                        adState.streakFreeze == RewardedAdAvailability.Unavailable -> Text(stringResource(TanksStrings.dailyFreezeUnavailable), color = MutedText,
                            fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    if (!freezeInProgress && (freezeResult == RewardedAdResult.NotEarned || freezeResult == RewardedAdResult.Failed)) {
                        Text(stringResource(if (freezeResult == RewardedAdResult.NotEarned) TanksStrings.dailyFreezeNotEarned
                            else TanksStrings.resurrectionFailed), color = MutedText, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** The seven cells of the weekly cycle, with the day a claim would land on picked out. */
@Composable
private fun WeekStrip(currentCycleDay: Int, claimable: TanksDailyAvailability) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..TanksDailyRewards.CycleLength).forEach { day ->
            val isToday = day == currentCycleDay
            val isPast = day < currentCycleDay
            val border = when {
                isToday && claimable == TanksDailyAvailability.Claimable -> AccentGold
                isToday -> PlayerGreen
                else -> Color.White.copy(alpha = 0.18f)
            }
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .border(if (isToday) 2.dp else 1.dp, border, RoundedCornerShape(6.dp))
                    .background(
                        if (isPast) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.toString(),
                    color = if (isToday) Color.White else MutedText,
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "+${TanksDailyRewards.rewardFor(day).bonusLives}",
                    color = if (isToday) AccentGold else MutedText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// --------------------------------------------------------------- collection

@Composable
fun CollectionScreen(
    meta: TanksMetaUi,
    assets: TanksAssets?,
    onBack: () -> Unit
) {
    SubScreenScaffold(title = stringResource(TanksStrings.collectionTitle), onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    TanksStrings.collectionProgress,
                    meta.collectionUnlocked,
                    meta.collectionTotal
                ),
                color = AccentGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(18.dp))

            TanksCardGroup.entries.forEach { group ->
                val cards = TanksCollection.cards.filter { it.group == group }
                if (cards.isEmpty()) return@forEach

                Text(
                    text = stringResource(groupLabel(group)),
                    color = MutedText,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                cards.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { card ->
                            CollectionCardTile(
                                modifier = Modifier.weight(1f),
                                card = card,
                                meta = meta,
                                assets = assets
                            )
                        }
                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

private fun groupLabel(group: TanksCardGroup) = when (group) {
    TanksCardGroup.Enemies -> TanksStrings.collectionGroupEnemies
    TanksCardGroup.PowerUps -> TanksStrings.collectionGroupPowerups
    TanksCardGroup.Milestones -> TanksStrings.collectionGroupMilestones
    TanksCardGroup.Feats -> TanksStrings.collectionGroupFeats
}

@Composable
private fun CollectionCardTile(
    modifier: Modifier,
    card: TanksCard,
    meta: TanksMetaUi,
    assets: TanksAssets?
) {
    val unlocked = TanksCollection.isUnlocked(card, meta.stats, meta.awardedCards)
    val progress = TanksCollection.progress(card, meta.stats, meta.awardedCards)
    val sprite = card.spriteKey?.let { assets?.sprite(it) }

    Column(
        modifier = modifier
            .border(
                1.dp,
                if (unlocked) AccentGold else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(6.dp)
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A fixed art box, not a proportional one: letting the sprite follow the tile width
        // made the canvas wider than the card on a wide window, and the art spilled over the
        // neighbouring cards.
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (sprite != null) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    drawImage(
                        image = sprite,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.None,
                        alpha = if (unlocked) 1f else 0.18f
                    )
                }
            } else {
                Text(
                    text = "D7",
                    color = if (unlocked) AccentGold else Color.White.copy(alpha = 0.18f),
                    fontSize = 26.sp
                )
            }
        }
        Text(
            text = "$progress / ${card.requirement}",
            color = if (unlocked) PlayerGreen else MutedText,
            fontSize = 10.sp
        )
    }
}

// -------------------------------------------------------------- leaderboard

private enum class LeaderboardTab {
    Campaign,
    Endless
}

@Composable
fun LeaderboardScreen(
    meta: TanksMetaUi,
    onNicknameChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(LeaderboardTab.Endless) }

    SubScreenScaffold(title = stringResource(TanksStrings.leaderboardTitle), onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(TanksStrings.nicknameTitle),
                        color = MutedText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = meta.nickname,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(onClick = { editing = true }) {
                    Text(stringResource(TanksStrings.nicknameChange))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeaderboardTabButton(
                    modifier = Modifier.weight(1f),
                    selected = selectedTab == LeaderboardTab.Campaign,
                    label = stringResource(TanksStrings.leaderboardTabCampaign),
                    onClick = { selectedTab = LeaderboardTab.Campaign }
                )
                LeaderboardTabButton(
                    modifier = Modifier.weight(1f),
                    selected = selectedTab == LeaderboardTab.Endless,
                    label = stringResource(TanksStrings.leaderboardTabEndless),
                    onClick = { selectedTab = LeaderboardTab.Endless }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val board = if (selectedTab == LeaderboardTab.Endless) {
                meta.endlessLeaderboard
            } else {
                meta.leaderboard
            }
            if (board.entries.isEmpty()) {
                Text(
                    text = stringResource(TanksStrings.leaderboardEmpty),
                    color = MutedText,
                    fontSize = 14.sp
                )
            } else {
                board.entries.forEachIndexed { index, entry ->
                    LeaderboardRow(
                        place = index + 1,
                        entry = entry,
                        isEndless = selectedTab == LeaderboardTab.Endless
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(TanksStrings.leaderboardLocalNote),
                color = MutedText,
                fontSize = 11.sp
            )
            if (selectedTab == LeaderboardTab.Endless && meta.hasPortalLeaderboard) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(TanksStrings.leaderboardPortalNote),
                    color = PlayerGreen,
                    fontSize = 11.sp
                )
            }
        }
    }

    if (editing) {
        NicknameDialog(
            initial = meta.nickname,
            onSave = {
                onNicknameChange(it)
                editing = false
            },
            onDismiss = { editing = false }
        )
    }
}

@Composable
private fun LeaderboardTabButton(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun LeaderboardRow(place: Int, entry: TanksScoreEntry, isEndless: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$place.",
            color = if (place == 1) AccentGold else MutedText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.name, color = Color.White, fontSize = 14.sp)
            Text(
                text = stringResource(
                    if (isEndless) TanksStrings.leaderboardWaveRow else TanksStrings.leaderboardRow,
                    entry.stage
                ),
                color = MutedText,
                fontSize = 11.sp
            )
        }
        Text(
            text = entry.score.toString(),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NicknameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(TanksStrings.nicknameTitle)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(TanksNickname.MaxLength) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(TanksStrings.nicknameHint, TanksNickname.MaxLength),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(TanksStrings.nicknameSave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(TanksStrings.commonCancel)) }
        }
    )
}
