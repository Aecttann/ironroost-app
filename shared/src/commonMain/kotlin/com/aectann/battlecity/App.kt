package com.aectann.battlecity

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aectann.battlecity.engine.BattleCityMaxStage
import com.aectann.battlecity.engine.FreePlayWallet
import com.aectann.battlecity.engine.NoopTanksAnalytics
import com.aectann.battlecity.engine.TanksDailyAvailability
import com.aectann.battlecity.ui.AboutScreen
import com.aectann.battlecity.ui.CollectionScreen
import com.aectann.battlecity.ui.DailyRewardScreen
import com.aectann.battlecity.ui.LeaderboardScreen
import com.aectann.battlecity.ui.MenuScreen
import com.aectann.battlecity.ui.SettingsScreen
import com.aectann.battlecity.ui.TanksGameScreen
import kotlinx.coroutines.delay

private val TankColors = darkColorScheme(
    primary = Color(0xFFE0A03C),
    onPrimary = Color(0xFF1A1206),
    secondary = Color(0xFF9BD16B),
    onSecondary = Color(0xFF10200A),
    background = Color(0xFF1A1F28),
    onBackground = Color(0xFFE7EAEF),
    surface = Color(0xFF2A2F38),
    onSurface = Color(0xFFE7EAEF),
    error = Color(0xFFD13D2F),
    onError = Color(0xFFFFFFFF)
)

private enum class Destination {
    Menu,
    Game,
    Daily,
    Collection,
    Leaderboard,
    Settings,
    About
}

@Composable
fun TanksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TankColors, content = content)
}

/**
 * Root of the app on every platform: a small menu in front of the game, with the sprite set
 * and the run's view model owned here so they survive moving between screens.
 *
 * @param allStagesUnlocked lets a development build jump straight to any stage.
 */
@Composable
fun App(
    allStagesUnlocked: Boolean = false,
    ads: TanksAds = NoopTanksAds,
    menuBanner: @Composable () -> Unit = {}
) {
    TanksTheme {
        val platform = rememberTanksPlatform()

        val viewModel: TanksViewModel = viewModel(
            factory = remember(platform, allStagesUnlocked) {
                viewModelFactory {
                    initializer {
                        TanksViewModel(
                            wallet = FreePlayWallet,
                            progress = platform.progressStore,
                            analytics = NoopTanksAnalytics,
                            metaRepository = TanksMetaRepository(
                                store = platform.keyValueStore,
                                clock = platform::nowEpochMillis
                            ),
                            portal = platform.portal,
                            resurrectionEnabled = ads.supportsResurrection,
                            allStagesUnlocked = allStagesUnlocked
                        )
                    }
                }
            }
        )

        val session by viewModel.session.collectAsState()
        val meta by viewModel.meta.collectAsState()
        val adsState by ads.state.collectAsState()
        LaunchedEffect(adsState.fullScreenShowing, adsState.privacyOptionsBusy, session.phase) {
            if (adsState.fullScreenShowing || adsState.privacyOptionsBusy) viewModel.pause()
        }
        var destination by rememberSaveable { mutableStateOf(Destination.Menu) }
        var assets by remember { mutableStateOf<TanksAssets?>(null) }
        // The seat count belongs to the menu, not to the run: it has to survive between runs so
        // a pair playing together does not re-pick two players every time they lose.
        var playerCount by rememberSaveable { mutableStateOf(1) }

        // The second seat is keyboard-only. Everything that starts a run reads this rather than
        // playerCount directly, so a stale two-player choice — carried over from a restored
        // state, or from a keyboard that has since been unplugged — cannot put an undriveable
        // tank on the board.
        //
        // Polled while the menu is up rather than read once. A keyboard can be paired at any
        // moment, and the browser build cannot detect one at all until a key is actually
        // pressed — so the answer has to be allowed to change under a player who is looking
        // straight at the disabled button.
        var coopAvailable by remember { mutableStateOf(platform.hasPhysicalKeyboard) }
        LaunchedEffect(platform, destination) {
            while (destination == Destination.Menu) {
                coopAvailable = platform.hasPhysicalKeyboard
                delay(1000)
            }
        }
        val seats = if (coopAvailable) playerCount else 1

        // Decoded once for the whole app: the menu shows the tank, the board draws everything.
        LaunchedEffect(Unit) {
            runCatching { TanksAssets.load() }.onSuccess { assets = it }
        }

        LaunchedEffect(platform.portal, session.highestCompletedStage) {
            platform.portal.reportProgress(
                completedStage = session.highestCompletedStage,
                totalStages = BattleCityMaxStage
            )
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (destination) {
                    Destination.Menu -> MenuScreen(
                        highestCompletedStage = session.highestCompletedStage,
                        titleSprite = assets?.menuTankSprite(),
                        dailyClaimable = meta.daily.availability == TanksDailyAvailability.Claimable,
                        collectionUnlocked = meta.collectionUnlocked,
                        collectionTotal = meta.collectionTotal,
                        bestEndlessWave = meta.stats.bestEndlessWave,
                        playerCount = seats,
                        coopAvailable = coopAvailable,
                        onPlayerCountChange = { playerCount = it },
                        onDaily = { destination = Destination.Daily },
                        onCollection = { destination = Destination.Collection },
                        onLeaderboard = { destination = Destination.Leaderboard },
                        onNewGame = {
                            viewModel.selectStage(1, seats)
                            destination = Destination.Game
                        },
                        onContinue = {
                            val next = (session.highestCompletedStage + 1).coerceAtMost(BattleCityMaxStage)
                            viewModel.selectStage(next, seats)
                            destination = Destination.Game
                        },
                        onEndless = {
                            viewModel.startEndless(seats)
                            destination = Destination.Game
                        },
                        onSettings = { destination = Destination.Settings },
                        onAbout = { destination = Destination.About }
                    )

                    Destination.Game -> TanksGameScreen(
                        platform = platform,
                        viewModel = viewModel,
                        assets = assets,
                        ads = ads,
                        onExitToMenu = {
                            viewModel.finishLostRun()
                            viewModel.pause()
                            destination = Destination.Menu
                        }
                    )

                    Destination.Daily -> DailyRewardScreen(
                        meta = meta,
                        onClaim = viewModel::claimDaily,
                        onBack = { destination = Destination.Menu }
                    )

                    Destination.Collection -> CollectionScreen(
                        meta = meta,
                        assets = assets,
                        onBack = { destination = Destination.Menu }
                    )

                    Destination.Leaderboard -> LeaderboardScreen(
                        meta = meta,
                        onNicknameChange = viewModel::setNickname,
                        onBack = { destination = Destination.Menu }
                    )

                    Destination.Settings -> SettingsScreen(
                        soundEnabled = session.soundEnabled,
                        onSoundToggled = viewModel::setSoundEnabled,
                        onResetProgress = viewModel::resetProgress,
                        privacyOptionsRequired = adsState.privacyOptionsRequired,
                        privacyOptionsBusy = adsState.privacyOptionsBusy,
                        privacyOptionsFailed = adsState.privacyOptionsFailed,
                        onPrivacyOptions = ads::showPrivacyOptions,
                        onBack = { destination = Destination.Menu }
                    )

                    Destination.About -> AboutScreen(
                        appVersion = platform.appVersion,
                        onBack = { destination = Destination.Menu }
                    )
                }
            }
            if (destination != Destination.Game) menuBanner()
        }

        PlatformBackHandler(enabled = destination != Destination.Menu && destination != Destination.Game) {
            destination = Destination.Menu
        }
    }
}
