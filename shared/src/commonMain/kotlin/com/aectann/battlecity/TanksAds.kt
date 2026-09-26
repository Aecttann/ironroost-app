package com.aectann.battlecity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RewardedAdAvailability { Unavailable, Loading, Ready, CoolingDown }

enum class RewardedAdResult { Earned, NotEarned, Unavailable, Failed }

enum class TanksAdAudience { MinorOrUnknown, Adult }

/** Unknown or invalid ages receive the same conservative ad treatment as minors. */
fun adAudienceForAge(age: Int?): TanksAdAudience =
    if (age != null && age in 18..130) TanksAdAudience.Adult else TanksAdAudience.MinorOrUnknown

data class TanksAdsState(
    val canRequestAds: Boolean = false,
    val resurrection: RewardedAdAvailability = RewardedAdAvailability.Unavailable,
    val streakFreeze: RewardedAdAvailability = RewardedAdAvailability.Unavailable,
    val resurrectionCooldownSeconds: Int = 0,
    val streakFreezeCooldownHours: Int = 0,
    val privacyOptionsRequired: Boolean = false,
    val privacyOptionsBusy: Boolean = false,
    val privacyOptionsFailed: Boolean = false,
    val fullScreenShowing: Boolean = false
)

interface TanksAds {
    val supportsResurrection: Boolean
    val supportsStreakFreeze: Boolean get() = false
    val state: StateFlow<TanksAdsState>

    /** Completes after fullscreen dismissal; Earned requires the SDK reward callback. */
    suspend fun showResurrection(): RewardedAdResult
    suspend fun showStreakFreeze(): RewardedAdResult = RewardedAdResult.Unavailable
    fun prepareStreakFreeze(enabled: Boolean) = Unit
    fun showPrivacyOptions()
}

object NoopTanksAds : TanksAds {
    override val supportsResurrection = false
    override val state = MutableStateFlow(TanksAdsState()).asStateFlow()
    override suspend fun showResurrection() = RewardedAdResult.Unavailable
    override fun showPrivacyOptions() = Unit
}
