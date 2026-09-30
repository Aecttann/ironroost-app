package com.aectann.battlecity.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    val daily = meta.daily
    val claimable = daily.availability == TanksDailyAvailability.Claimable
    // With a reward waiting, Enter claims it; otherwise it goes back.
    val claim = rememberInitialFocus(enabled = claimable)
    val type = LocalPixelType.current

    SubScreenScaffold(
        title = stringResource(TanksStrings.dailyTitle),
        onBack = onBack,
        backEnabled = !freezeInProgress,
        backTakesFocus = !claimable
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PixelTitle(stringResource(TanksStrings.dailyStreak, daily.streak))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(TanksStrings.dailyBestStreak, daily.bestStreak),
                color = MutedText,
                style = type.caption
            )

            Spacer(modifier = Modifier.height(20.dp))
            WeekStrip(currentCycleDay = daily.cycleDay, claimable = daily.availability)
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(TanksStrings.dailyRewardLives, daily.reward.bonusLives),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(TanksStrings.dailyRewardCard),
                color = MutedText,
                textAlign = TextAlign.Center
            )

            if (daily.streakWillReset) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(TanksStrings.dailyStreakWarning),
                    color = BrickLight,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            when (daily.availability) {
                TanksDailyAvailability.Claimable -> PixelButton(
                    text = stringResource(TanksStrings.dailyClaim),
                    onClick = onClaim,
                    enabled = !freezeInProgress,
                    modifier = Modifier.widthIn(min = 240.dp),
                    material = PixelMaterial.Gold,
                    focusRequester = claim
                )

                TanksDailyAvailability.Claimed -> Text(
                    text = stringResource(TanksStrings.dailyClaimed),
                    color = PlayerGreen,
                    textAlign = TextAlign.Center
                )

                TanksDailyAvailability.ClockBehind -> Text(
                    text = stringResource(TanksStrings.dailyClockBehind),
                    color = BrickLight,
                    textAlign = TextAlign.Center
                )
            }

            if (meta.pendingBonusLives > 0) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(TanksStrings.dailyBanked, meta.pendingBonusLives),
                    color = PlayerGreen,
                    textAlign = TextAlign.Center
                )
            }
            if (supportsStreakFreeze) {
                Spacer(Modifier.height(24.dp))
                if (daily.streakFreezeStored) {
                    Text(stringResource(if (daily.streakFreezeWillBeUsed) TanksStrings.dailyFreezeProtectClaim
                        else TanksStrings.dailyFreezeStored), color = PlayerGreen,
                        textAlign = TextAlign.Center)
                } else {
                    Text(stringResource(TanksStrings.dailyFreezeOffer), color = MutedText,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    PixelButton(
                        text = stringResource(if (freezeInProgress) TanksStrings.resurrectionWatching else TanksStrings.dailyFreezeWatch),
                        onClick = onFreeze,
                        enabled = meta.canEarnStreakFreeze &&
                            adState.streakFreeze == RewardedAdAvailability.Ready && !freezeInProgress,
                        material = PixelMaterial.Steel
                    )
                    val cooldownHours = maxOf(meta.streakFreezeCooldownHours, adState.streakFreezeCooldownHours)
                    val note = when {
                        freezeInProgress -> null
                        cooldownHours > 0 -> stringResource(TanksStrings.dailyFreezeCooldown, cooldownHours)
                        adState.streakFreeze == RewardedAdAvailability.Loading -> stringResource(TanksStrings.resurrectionLoading)
                        adState.streakFreeze == RewardedAdAvailability.Unavailable -> stringResource(TanksStrings.dailyFreezeUnavailable)
                        else -> null
                    }
                    if (note != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(note, color = MutedText, style = type.caption, textAlign = TextAlign.Center)
                    }
                    if (!freezeInProgress && (freezeResult == RewardedAdResult.NotEarned || freezeResult == RewardedAdResult.Failed)) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(if (freezeResult == RewardedAdResult.NotEarned) TanksStrings.dailyFreezeNotEarned
                            else TanksStrings.resurrectionFailed), color = MutedText, style = type.caption, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** The seven cells of the weekly cycle, with the day a claim would land on picked out. */
@Composable
private fun WeekStrip(currentCycleDay: Int, claimable: TanksDailyAvailability) {
    val type = LocalPixelType.current
    // Seven cells share whatever width there is, up to 60 dp each: at 360 dp the fixed cells ran
    // off the screen and cut the seventh day in half, and on the framed page of a wide window the
    // week should still read as the main thing on it.
    Row(
        modifier = Modifier.widthIn(max = 60.dp * TanksDailyRewards.CycleLength + 6.dp * (TanksDailyRewards.CycleLength - 1)).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..TanksDailyRewards.CycleLength).forEach { day ->
            val isToday = day == currentCycleDay
            val isPast = day < currentCycleDay
            val accent = when {
                isToday && claimable == TanksDailyAvailability.Claimable -> GoldLight
                isToday -> PlayerGreen
                else -> null
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pixelInset(accent = accent, fill = if (isPast) PanelFace else PanelDark)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.toString(),
                    color = if (isToday) Color.White else MutedText,
                    style = type.label
                )
                Text(
                    text = "+${TanksDailyRewards.rewardFor(day).bonusLives}",
                    color = if (isToday) AccentGold else MutedText,
                    style = type.caption
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
            PixelTitle(
                stringResource(
                    TanksStrings.collectionProgress,
                    meta.collectionUnlocked,
                    meta.collectionTotal
                )
            )
            Spacer(modifier = Modifier.height(18.dp))

            TanksCardGroup.entries.forEach { group ->
                val cards = TanksCollection.cards.filter { it.group == group }
                if (cards.isEmpty()) return@forEach

                Text(
                    text = stringResource(groupLabel(group)),
                    color = MutedText,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                cards.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Spacer(modifier = Modifier.height(8.dp))
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
            .pixelInset(accent = if (unlocked) AccentGold else null)
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
                    style = LocalPixelType.current.heading
                )
            }
        }
        Text(
            text = "$progress / ${card.requirement}",
            color = if (unlocked) PlayerGreen else MutedText,
            style = LocalPixelType.current.caption
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
    val type = LocalPixelType.current

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
                        style = type.caption
                    )
                    Text(
                        text = meta.nickname,
                        color = Color.White,
                        style = type.heading.shadowed()
                    )
                }
                PixelButton(
                    text = stringResource(TanksStrings.nicknameChange),
                    onClick = { editing = true },
                    material = PixelMaterial.Steel
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            PixelSegmented(
                options = listOf(LeaderboardTab.Campaign, LeaderboardTab.Endless),
                selected = selectedTab,
                label = { tab ->
                    stringResource(
                        if (tab == LeaderboardTab.Campaign) TanksStrings.leaderboardTabCampaign
                        else TanksStrings.leaderboardTabEndless
                    )
                },
                onSelect = { selectedTab = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            val board = if (selectedTab == LeaderboardTab.Endless) {
                meta.endlessLeaderboard
            } else {
                meta.leaderboard
            }
            if (board.entries.isEmpty()) {
                Text(
                    text = stringResource(TanksStrings.leaderboardEmpty),
                    color = MutedText
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
                style = type.caption
            )
            if (selectedTab == LeaderboardTab.Endless && meta.hasPortalLeaderboard) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(TanksStrings.leaderboardPortalNote),
                    color = PlayerGreen,
                    style = type.caption
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
private fun LeaderboardRow(place: Int, entry: TanksScoreEntry, isEndless: Boolean) {
    val type = LocalPixelType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pixelInset(accent = if (place == 1) AccentGold else null)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$place.",
            color = if (place == 1) AccentGold else MutedText,
            style = type.label,
            modifier = Modifier.width(36.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.name, color = Color.White)
            Text(
                text = stringResource(
                    if (isEndless) TanksStrings.leaderboardWaveRow else TanksStrings.leaderboardRow,
                    entry.stage
                ),
                color = MutedText,
                style = type.caption
            )
        }
        Text(
            text = entry.score.toString(),
            color = Color.White,
            style = type.heading.shadowed()
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
    val field = rememberInitialFocus()

    PixelDialog(
        title = stringResource(TanksStrings.nicknameTitle),
        onDismiss = onDismiss,
        menuKeys = false,
        buttons = {
            PixelButton(stringResource(TanksStrings.commonCancel), onDismiss, material = PixelMaterial.Steel)
            PixelButton(stringResource(TanksStrings.nicknameSave), { onSave(text) })
        }
    ) {
        PixelTextField(
            value = text,
            onValueChange = { text = it.take(TanksNickname.MaxLength) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            focusRequester = field
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(TanksStrings.nicknameHint, TanksNickname.MaxLength),
            color = MutedText,
            style = LocalPixelType.current.caption
        )
    }
}
