package com.aectann.battlecity

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aectann.battlecity.ui.AdsAgeScreen
import com.aectann.battlecity.ads.AdMobController
import com.aectann.battlecity.ads.MenuBanner

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash hands over without a white flash.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // A debuggable build can jump to any stage; a release build unlocks them by playing.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        setContent {
            val ageState: AdAudienceViewModel = viewModel()
            val audience = ageState.audience
            if (audience == null) {
                TanksTheme { AdsAgeScreen(onAgeSelected = ageState::chooseAge) }
            } else {
                val ads = remember(audience) { AdMobController(this, audience) }
                DisposableEffect(ads) { onDispose { ads.close() } }
                App(allStagesUnlocked = debuggable, ads = ads, menuBanner = { MenuBanner(ads) })
            }
        }
    }
}

class AdAudienceViewModel : ViewModel() {
    var audience by mutableStateOf<TanksAdAudience?>(null)
        private set

    fun chooseAge(age: Int?) {
        if (audience == null) audience = adAudienceForAge(age)
    }
}
