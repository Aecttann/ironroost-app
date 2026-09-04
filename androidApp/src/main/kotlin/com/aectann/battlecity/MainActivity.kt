package com.aectann.battlecity

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash hands over without a white flash.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // A debuggable build can jump to any stage; a release build unlocks them by playing.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        setContent {
            App(allStagesUnlocked = debuggable)
        }
    }
}
