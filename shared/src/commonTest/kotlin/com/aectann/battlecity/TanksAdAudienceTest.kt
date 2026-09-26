package com.aectann.battlecity

import kotlin.test.Test
import kotlin.test.assertEquals

class TanksAdAudienceTest {
    @Test
    fun minorsUnknownAndInvalidAgesReceiveRestrictedTreatment() {
        listOf(null, -1, 0, 12, 13, 16, 17, 131).forEach { age ->
            assertEquals(TanksAdAudience.MinorOrUnknown, adAudienceForAge(age))
        }
    }

    @Test
    fun onlyValidAdultAgesReceiveAdultTreatment() {
        listOf(18, 19, 99, 130).forEach { age ->
            assertEquals(TanksAdAudience.Adult, adAudienceForAge(age))
        }
    }
}
