package com.aectann.battlecity

enum class TanksRewardedPlacement(val cooldownMillis: Long) {
    Resurrection(5 * 60 * 1000L),
    StreakFreeze(48 * 60 * 60 * 1000L)
}

/** Local display caps supplement AdMob caps and survive Activity/process recreation. */
class TanksAdFrequencyStore(
    private val store: TanksKeyValueStore,
    private val clock: () -> Long
) {
    fun remainingMillis(placement: TanksRewardedPlacement): Long {
        val shownAt = store.getString(key(placement))?.toLongOrNull()?.takeIf { it >= 0 } ?: return 0
        val now = clock()
        if (now < shownAt) return placement.cooldownMillis
        return (placement.cooldownMillis - (now - shownAt)).coerceAtLeast(0)
    }

    fun recordShown(placement: TanksRewardedPlacement) {
        store.putString(key(placement), clock().coerceAtLeast(0).toString())
    }

    private fun key(placement: TanksRewardedPlacement) = "tanks_ad_last_shown_${placement.name}"
}
