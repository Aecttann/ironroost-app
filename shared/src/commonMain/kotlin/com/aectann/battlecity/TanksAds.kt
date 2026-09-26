package com.aectann.battlecity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ResurrectionAdAvailability { Unavailable, Loading, Ready }

enum class ResurrectionAdResult { Earned, NotEarned, Unavailable, Failed }

enum class TanksAdAudience { MinorOrUnknown, Adult }

/** Unknown or invalid ages receive the same conservative ad treatment as minors. */
fun adAudienceForAge(age: Int?): TanksAdAudience =
    if (age != null && age in 18..130) TanksAdAudience.Adult else TanksAdAudience.MinorOrUnknown

data class TanksAdsState(
    val canRequestAds: Boolean = false,
    val resurrection: ResurrectionAdAvailability = ResurrectionAdAvailability.Unavailable,
    val privacyOptionsRequired: Boolean = false,
    val privacyOptionsBusy: Boolean = false,
    val privacyOptionsFailed: Boolean = false,
    val fullScreenShowing: Boolean = false
)

interface TanksAds {
    val supportsResurrection: Boolean
    val state: StateFlow<TanksAdsState>

    /** Completes after fullscreen dismissal; Earned requires the SDK reward callback. */
    suspend fun showResurrection(): ResurrectionAdResult
    fun showPrivacyOptions()
}

object NoopTanksAds : TanksAds {
    override val supportsResurrection = false
    override val state = MutableStateFlow(TanksAdsState()).asStateFlow()
    override suspend fun showResurrection() = ResurrectionAdResult.Unavailable
    override fun showPrivacyOptions() = Unit
}
