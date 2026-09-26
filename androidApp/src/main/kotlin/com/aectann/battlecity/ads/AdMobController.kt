package com.aectann.battlecity.ads

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aectann.battlecity.BuildConfig
import com.aectann.battlecity.RewardedAdAvailability
import com.aectann.battlecity.RewardedAdResult
import com.aectann.battlecity.TanksAds
import com.aectann.battlecity.TanksAdsState
import com.aectann.battlecity.TanksAdAudience
import com.aectann.battlecity.TanksAdFrequencyStore
import com.aectann.battlecity.TanksRewardedPlacement
import com.aectann.battlecity.createAndroidTanksKeyValueStore
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
    override val supportsStreakFreeze = true

    private var closed = false
    private var consentStarted = false
    private var consentBusy = false
    private var initializationStarted = false
    private var initialized = false
    private val frequency = TanksAdFrequencyStore(createAndroidTanksKeyValueStore(activity), System::currentTimeMillis)
    private val slots = mapOf(
        TanksRewardedPlacement.Resurrection to RewardedSlot(TanksRewardedPlacement.Resurrection, BuildConfig.RESURRECTION_AD_UNIT_ID, true),
        TanksRewardedPlacement.StreakFreeze to RewardedSlot(TanksRewardedPlacement.StreakFreeze, BuildConfig.STREAK_FREEZE_AD_UNIT_ID, false)
    )
    private var presentation: Presentation? = null

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                if (!consentBusy && (!consentStarted || !consent.canRequestAds())) gatherConsent()
                else if (!consentBusy) loadRewardedAds()
            }
            Lifecycle.Event.ON_PAUSE -> slots.values.forEach { it.refreshJob?.cancel() }
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
            loadRewardedAds()
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

    private fun loadRewardedAds() = slots.values.forEach { it.load() }

    override fun prepareStreakFreeze(enabled: Boolean) {
        val slot = slots.getValue(TanksRewardedPlacement.StreakFreeze)
        if (slot.requested == enabled) return
        slot.requested = enabled
        if (enabled) slot.load() else slot.invalidate()
    }

    override suspend fun showResurrection() = showRewarded(TanksRewardedPlacement.Resurrection)

    override suspend fun showStreakFreeze() = showRewarded(TanksRewardedPlacement.StreakFreeze)

    private suspend fun showRewarded(placement: TanksRewardedPlacement): RewardedAdResult = withContext(Dispatchers.Main.immediate) {
        val slot = slots.getValue(placement)
        val ad = slot.cachedAd
        if (closed || ad == null || !isResumed() || presentation != null ||
            !slot.requested || !mutableState.value.canRequestAds || !consent.canRequestAds() ||
            mutableState.value.fullScreenShowing || mutableState.value.privacyOptionsBusy ||
            frequency.remainingMillis(placement) > 0 ||
            SystemClock.elapsedRealtime() - slot.loadedAt >= CacheLifetimeMillis) {
            slot.load()
            return@withContext RewardedAdResult.Unavailable
        }
        slot.cachedAd = null
        slot.refreshJob?.cancel()
        slot.publish(RewardedAdAvailability.Unavailable)
        mutableState.value = mutableState.value.copy(fullScreenShowing = true)
        suspendCancellableCoroutine { continuation ->
            val showing = Presentation(continuation)
            presentation = showing
            continuation.invokeOnCancellation {
                scope.launch { if (presentation === showing) showing.continuation = null }
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    if (presentation === showing && !showing.shown) {
                        showing.shown = true
                        frequency.recordShown(placement)
                    }
                }

                override fun onAdDismissedFullScreenContent() {
                    completePresentation(showing, RewardedAdResult.NotEarned)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(Tag, "$placement show: ${error.code}: ${error.message}")
                    completePresentation(showing, RewardedAdResult.Failed)
                }
            }
            try {
                ad.show(activity) { if (presentation === showing) showing.earned = true }
            } catch (error: RuntimeException) {
                Log.w(Tag, "$placement show failed", error)
                completePresentation(showing, RewardedAdResult.Failed)
            }
        }
    }

    private fun completePresentation(showing: Presentation, fallback: RewardedAdResult) {
        if (presentation !== showing) return
        presentation = null
        mutableState.value = mutableState.value.copy(fullScreenShowing = false)
        val continuation = showing.continuation
        showing.continuation = null
        if (continuation?.isActive == true) {
            continuation.resume(if (showing.earned) RewardedAdResult.Earned else fallback)
        }
        loadRewardedAds()
    }

    private fun invalidateCache() {
        slots.values.forEach { it.invalidate() }
    }

    private inner class RewardedSlot(
        val placement: TanksRewardedPlacement,
        val adUnitId: String,
        var requested: Boolean
    ) {
        var cachedAd: RewardedInterstitialAd? = null
        var loadedAt = 0L
        var refreshJob: Job? = null
        private var generation = 0
        private var loading = false
        private var retryDelayMillis = InitialRetryMillis

        fun load() {
            if (closed || !requested || !isResumed() || !mutableState.value.canRequestAds ||
                !consent.canRequestAds() || mutableState.value.fullScreenShowing ||
                mutableState.value.privacyOptionsBusy || presentation != null) return
            val remaining = frequency.remainingMillis(placement)
            if (remaining > 0) {
                invalidate()
                publish(RewardedAdAvailability.CoolingDown, remaining)
                schedule(remaining.coerceAtMost(if (placement == TanksRewardedPlacement.Resurrection) 1000L else 60_000L))
                return
            }
            if (loading) return
            val lifetimeLeft = CacheLifetimeMillis - (SystemClock.elapsedRealtime() - loadedAt)
            if (cachedAd != null && lifetimeLeft > 0) {
                publish(RewardedAdAvailability.Ready)
                schedule(lifetimeLeft)
                return
            }
            cachedAd = null
            loading = true
            refreshJob?.cancel()
            val request = ++generation
            publish(RewardedAdAvailability.Loading)
            RewardedInterstitialAd.load(activity.applicationContext, adUnitId, AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        if (closed || request != generation) return
                        loading = false
                        cachedAd = ad
                        loadedAt = SystemClock.elapsedRealtime()
                        retryDelayMillis = InitialRetryMillis
                        publish(RewardedAdAvailability.Ready)
                        schedule(CacheLifetimeMillis)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (closed || request != generation) return
                        loading = false
                        publish(RewardedAdAvailability.Unavailable)
                        Log.w(Tag, "$placement load: ${error.code}: ${error.message}")
                        schedule(retryDelayMillis)
                        retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MaxRetryMillis)
                    }
                })
        }

        fun publish(availability: RewardedAdAvailability, remaining: Long = 0) {
            val current = mutableState.value
            mutableState.value = when (placement) {
                TanksRewardedPlacement.Resurrection -> current.copy(resurrection = availability,
                    resurrectionCooldownSeconds = ((remaining + 999) / 1000).toInt())
                TanksRewardedPlacement.StreakFreeze -> current.copy(streakFreeze = availability,
                    streakFreezeCooldownHours = ((remaining + 3_599_999) / 3_600_000).toInt())
            }
        }

        private fun schedule(delayMillis: Long) {
            refreshJob?.cancel()
            refreshJob = scope.launch { delay(delayMillis); load() }
        }

        fun invalidate() {
            generation++
            cachedAd = null
            loading = false
            refreshJob?.cancel()
            publish(RewardedAdAvailability.Unavailable)
        }
    }

    private fun isResumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        !activity.isFinishing && !activity.isDestroyed

    fun close() {
        if (closed) return
        closed = true
        mutableState.value = mutableState.value.copy(canRequestAds = false, fullScreenShowing = false)
        activity.lifecycle.removeObserver(lifecycleObserver)
        invalidateCache()
        presentation?.let { completePresentation(it, RewardedAdResult.Unavailable) }
        scope.cancel()
    }

    private class Presentation(var continuation: CancellableContinuation<RewardedAdResult>?) {
        var earned = false
        var shown = false
    }

    private companion object {
        const val Tag = "BattleCityAds"
        const val CacheLifetimeMillis = 55 * 60 * 1000L
        const val InitialRetryMillis = 30_000L
        const val MaxRetryMillis = 300_000L
    }
}
