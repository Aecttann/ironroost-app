package com.aectann.battlecity

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.aectann.battlecity.ui.AdsAgeScreen
import com.aectann.battlecity.ads.AdMobController
import com.aectann.battlecity.ads.AdAudienceViewModel
import com.aectann.battlecity.ads.MenuBanner

class MainActivity : ComponentActivity() {
    private val ageState: AdAudienceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash hands over without a white flash.
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { ageState.isLoading }

        // A debuggable build can jump to any stage; a release build unlocks them by playing.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        setContent {
            val audience = ageState.audience
            if (ageState.isLoading) {
                return@setContent
            } else if (audience == null) {
                TanksTheme {
                    AdsAgeScreen(
                        onAgeSelected = ageState::chooseAge,
                        isSaving = ageState.isSaving,
                        saveFailed = ageState.saveFailed
                    )
                }
            } else {
                val ads = remember(audience) { AdMobController(this, audience) }
                DisposableEffect(ads) { onDispose { ads.close() } }
                App(allStagesUnlocked = debuggable, ads = ads, menuBanner = { MenuBanner(ads) })
            }
        }
    }
}
