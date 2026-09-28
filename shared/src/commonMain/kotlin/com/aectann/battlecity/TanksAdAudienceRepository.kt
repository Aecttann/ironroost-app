package com.aectann.battlecity

interface TanksAdAudienceStorage {
    fun read(): String?

    /** Atomically returns the existing value or persists a new one; failures must throw. */
    fun getOrPut(value: String): String
}

class TanksAdAudienceRepository(private val storage: TanksAdAudienceStorage) {
    fun currentAudience(): TanksAdAudience? = storage.read()?.let(::audienceForStoredValue)

    fun select(audience: TanksAdAudience): TanksAdAudience {
        val stored = storage.getOrPut(if (audience == TanksAdAudience.Adult) AdultValue else ProtectedValue)
        return audienceForStoredValue(stored)
    }

    private fun audienceForStoredValue(value: String): TanksAdAudience =
        if (value == AdultValue) TanksAdAudience.Adult else TanksAdAudience.MinorOrUnknown

    private companion object {
        const val AdultValue = "adult"
        const val ProtectedValue = "protected"
    }
}
