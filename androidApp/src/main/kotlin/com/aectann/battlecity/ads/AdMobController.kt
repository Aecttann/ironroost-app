package com.aectann.battlecity.ads

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aectann.battlecity.BuildConfig
import com.aectann.battlecity.ResurrectionAdAvailability
import com.aectann.battlecity.ResurrectionAdResult
import com.aectann.battlecity.TanksAds
import com.aectann.battlecity.TanksAdsState
import com.aectann.battlecity.TanksAdAudience
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Activity-scoped SDK owner. All ad requests and presentation callbacks run on the main thread. */
class AdMobController(
    private val activity: ComponentActivity,
    private val audience: TanksAdAudience
) : TanksAds {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private val mutableState = MutableStateFlow(TanksAdsState())
    override val state = mutableState.asStateFlow()
    override val supportsResurrection = true

    private var closed = false
    private var consentStarted = false
    private var consentBusy = false
    private var initializationStarted = false
    private var initialized = false
    private var loadGeneration = 0
    private var loading = false
    private var cachedAd: RewardedInterstitialAd? = null
    private var loadedAt = 0L
    private var retryDelayMillis = InitialRetryMillis
    private var refreshJob: Job? = null
    private var presentation: Presentation? = null

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                if (!consentBusy && (!consentStarted || !consent.canRequestAds())) gatherConsent()
                else if (!consentBusy) loadResurrection()
            }
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }

    init {
        val protectedAudience = audience == TanksAdAudience.MinorOrUnknown
        MobileAds.setRequestConfiguration(
            MobileAds.getRequestConfiguration().toBuilder()
                .setAgeRestrictedTreatment(if (protectedAudience) AgeRestrictedTreatment.CHILD else AgeRestrictedTreatment.UNSPECIFIED)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .setPublisherPrivacyPersonalizationState(
                    if (protectedAudience) RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
                    else RequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT
                )
                .build()
        )
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    private fun gatherConsent() {
        consentStarted = true
        consentBusy = true
        mutableState.value = mutableState.value.copy(canRequestAds = false)
        invalidateCache()
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(audience == TanksAdAudience.MinorOrUnknown)
                .build(),
            {
                if (!closed) {
                    mutableState.value = mutableState.value.copy(fullScreenShowing = true)
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        if (error != null) Log.w(Tag, "Consent form: ${error.errorCode}: ${error.message}")
                        consentBusy = false
                        if (!closed) mutableState.value = mutableState.value.copy(fullScreenShowing = false)
                        updateConsent()
                    }
                }
            },
            { error ->
                Log.w(Tag, "Consent update: ${error.errorCode}: ${error.message}")
                consentBusy = false
                updateConsent()
            }
        )
    }

    private fun updateConsent() {
        if (closed) return
        val allowed = consent.canRequestAds() && !consentBusy && !mutableState.value.privacyOptionsBusy
        mutableState.value = mutableState.value.copy(
            canRequestAds = allowed && initialized,
            privacyOptionsRequired = consent.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        )
        if (!allowed) {
            invalidateCache()
            return
        }
        if (!initializationStarted) {
            initializationStarted = true
            scope.launch(Dispatchers.IO) {
                MobileAds.initialize(activity.applicationContext) {
                    scope.launch {
                        initialized = true
                        updateConsent()
                    }
                }
            }
        } else if (initialized) {
            loadResurrection()
        }
    }

    override fun showPrivacyOptions() {
        if (closed || consentBusy || mutableState.value.privacyOptionsBusy ||
            presentation != null || !mutableState.value.privacyOptionsRequired || !isResumed()) return
        mutableState.value = mutableState.value.copy(
            privacyOptionsBusy = true,
            privacyOptionsFailed = false,
            canRequestAds = false
        )
        invalidateCache()
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (!closed) {
                mutableState.value = mutableState.value.copy(
                    privacyOptionsBusy = false,
                    privacyOptionsFailed = error != null
                )
                if (error != null) Log.w(Tag, "Privacy options: ${error.errorCode}: ${error.message}")
                updateConsent()
            }
        }
    }

    private fun loadResurrection() {
        if (closed || !isResumed() || !mutableState.value.canRequestAds || !consent.canRequestAds() ||
            loading || presentation != null) return
        if (cachedAd != null && SystemClock.elapsedRealtime() - loadedAt < CacheLifetimeMillis) return
        cachedAd = null
        loading = true
        refreshJob?.cancel()
        val generation = ++loadGeneration
        mutableState.value = mutableState.value.copy(resurrection = ResurrectionAdAvailability.Loading)
        RewardedInterstitialAd.load(
            activity.applicationContext,
            BuildConfig.RESURRECTION_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    if (closed || generation != loadGeneration) return
                    loading = false
                    cachedAd = ad
                    loadedAt = SystemClock.elapsedRealtime()
                    retryDelayMillis = InitialRetryMillis
                    mutableState.value = mutableState.value.copy(resurrection = ResurrectionAdAvailability.Ready)
                    scheduleLoad(CacheLifetimeMillis)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (closed || generation != loadGeneration) return
                    loading = false
                    mutableState.value = mutableState.value.copy(resurrection = ResurrectionAdAvailability.Unavailable)
                    Log.w(Tag, "Resurrection load: ${error.code}: ${error.message}")
                    scheduleLoad(retryDelayMillis)
                    retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MaxRetryMillis)
                }
            }
        )
    }

    private fun scheduleLoad(delayMillis: Long) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(delayMillis)
            loadResurrection()
        }
    }

    override suspend fun showResurrection(): ResurrectionAdResult = withContext(Dispatchers.Main.immediate) {
        val ad = cachedAd
        if (closed || ad == null || !isResumed() || presentation != null ||
            !mutableState.value.canRequestAds || !consent.canRequestAds() ||
            SystemClock.elapsedRealtime() - loadedAt >= CacheLifetimeMillis) {
            loadResurrection()
            return@withContext ResurrectionAdResult.Unavailable
        }
        cachedAd = null
        refreshJob?.cancel()
        mutableState.value = mutableState.value.copy(
            resurrection = ResurrectionAdAvailability.Unavailable,
            fullScreenShowing = true
        )
        suspendCancellableCoroutine { continuation ->
            val showing = Presentation(continuation)
            presentation = showing
            continuation.invokeOnCancellation {
                scope.launch { if (presentation === showing) showing.continuation = null }
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    completePresentation(showing, ResurrectionAdResult.NotEarned)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(Tag, "Resurrection show: ${error.code}: ${error.message}")
                    completePresentation(showing, ResurrectionAdResult.Failed)
                }
            }
            try {
                ad.show(activity) { if (presentation === showing) showing.earned = true }
            } catch (error: RuntimeException) {
                Log.w(Tag, "Resurrection show failed", error)
                completePresentation(showing, ResurrectionAdResult.Failed)
            }
        }
    }

    private fun completePresentation(showing: Presentation, fallback: ResurrectionAdResult) {
        if (presentation !== showing) return
        presentation = null
        mutableState.value = mutableState.value.copy(fullScreenShowing = false)
        val continuation = showing.continuation
        showing.continuation = null
        if (continuation?.isActive == true) {
            continuation.resume(if (showing.earned) ResurrectionAdResult.Earned else fallback)
        }
        loadResurrection()
    }

    private fun invalidateCache() {
        loadGeneration++
        cachedAd = null
        loading = false
        refreshJob?.cancel()
        mutableState.value = mutableState.value.copy(resurrection = ResurrectionAdAvailability.Unavailable)
    }

    private fun isResumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        !activity.isFinishing && !activity.isDestroyed

    fun close() {
        if (closed) return
        closed = true
        mutableState.value = mutableState.value.copy(canRequestAds = false, fullScreenShowing = false)
        activity.lifecycle.removeObserver(lifecycleObserver)
        invalidateCache()
        presentation?.let { completePresentation(it, ResurrectionAdResult.Unavailable) }
        scope.cancel()
    }

    private class Presentation(var continuation: CancellableContinuation<ResurrectionAdResult>?) {
        var earned = false
    }

    private companion object {
        const val Tag = "BattleCityAds"
        const val CacheLifetimeMillis = 55 * 60 * 1000L
        const val InitialRetryMillis = 30_000L
        const val MaxRetryMillis = 300_000L
    }
}
