package com.aectann.battlecity.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aectann.battlecity.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun MenuBanner(ads: AdMobController) {
    val state by ads.state.collectAsState()
    if (!state.canRequestAds) return
    val context = LocalContext.current
    val orientation = LocalConfiguration.current.orientation
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .background(Color(0xFF1A1F28))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val width = maxWidth.value.toInt()
        if (width <= 0) return@BoxWithConstraints
        key(width, orientation) {
            val size = remember(context, width, orientation) {
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, width)
            }
            val view = remember(context, size) {
                AdView(context).apply {
                    adUnitId = BuildConfig.MENU_BANNER_AD_UNIT_ID
                    setAdSize(size)
                }
            }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(view, lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> view.resume()
                        Lifecycle.Event.ON_PAUSE -> view.pause()
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                view.loadAd(AdRequest.Builder().build())
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.pause()
                onDispose { lifecycle.removeObserver(observer) }
            }
            val height = with(density) { size.getHeightInPixels(context).toDp() }
            Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                AndroidView(factory = { view }, onRelease = { it.destroy() })
            }
        }
    }
}
