package com.aectann.battlecity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TanksAdAudienceRepositoryTest {
    @Test
    fun onlyMissingStorageRequiresAgeSelection() {
        assertNull(TanksAdAudienceRepository(MemoryStorage()).currentAudience())
        listOf("", "18", "Adult", "unknown").forEach { stored ->
            assertEquals(TanksAdAudience.MinorOrUnknown,
                TanksAdAudienceRepository(MemoryStorage(stored)).currentAudience())
        }
    }

    @Test
    fun protectedSelectionSurvivesRepositoryRecreationAndCannotBecomeAdult() {
        val storage = MemoryStorage()
        TanksAdAudienceRepository(storage).select(adAudienceForAge(12))
        val restarted = TanksAdAudienceRepository(storage)
        assertEquals(TanksAdAudience.MinorOrUnknown, restarted.currentAudience())
        assertEquals(TanksAdAudience.MinorOrUnknown, restarted.select(TanksAdAudience.Adult))
        assertEquals("protected", storage.value)
        assertEquals(1, storage.writes)
    }

    @Test
    fun skippingAgePersistsProtectedTreatment() {
        val storage = MemoryStorage()
        TanksAdAudienceRepository(storage).select(adAudienceForAge(null))
        assertEquals(TanksAdAudience.MinorOrUnknown, TanksAdAudienceRepository(storage).currentAudience())
    }

    @Test
    fun adultSelectionSurvivesRepositoryRecreation() {
        val storage = MemoryStorage()
        TanksAdAudienceRepository(storage).select(adAudienceForAge(18))
        assertEquals(TanksAdAudience.Adult, TanksAdAudienceRepository(storage).currentAudience())
        assertEquals("adult", storage.value)
    }

    @Test
    fun malformedStorageCannotBeOverwrittenWithAdultTreatment() {
        val storage = MemoryStorage("invalid")
        assertEquals(TanksAdAudience.MinorOrUnknown,
            TanksAdAudienceRepository(storage).select(TanksAdAudience.Adult))
        assertEquals(0, storage.writes)
    }

    @Test
    fun selectionUsesTheValueCommittedByAnotherCaller() {
        val storage = object : TanksAdAudienceStorage {
            override fun read(): String? = null
            override fun getOrPut(value: String) = "protected"
        }
        assertEquals(TanksAdAudience.MinorOrUnknown,
            TanksAdAudienceRepository(storage).select(TanksAdAudience.Adult))
    }

    @Test
    fun failedPersistenceDoesNotAcceptSelectionAndCanBeRetried() {
        val storage = MemoryStorage(failWrites = true)
        val repository = TanksAdAudienceRepository(storage)
        assertFailsWith<StorageFailure> { repository.select(TanksAdAudience.Adult) }
        assertNull(repository.currentAudience())
        storage.failWrites = false
        assertEquals(TanksAdAudience.Adult, repository.select(TanksAdAudience.Adult))
        assertEquals(TanksAdAudience.Adult, TanksAdAudienceRepository(storage).currentAudience())
    }

    private class StorageFailure : Exception()

    private class MemoryStorage(var value: String? = null, var failWrites: Boolean = false) : TanksAdAudienceStorage {
        var writes = 0
            private set

        override fun read() = value

        override fun getOrPut(value: String): String {
            this.value?.let { return it }
            if (failWrites) throw StorageFailure()
            this.value = value
            writes++
            return value
        }
    }
}
