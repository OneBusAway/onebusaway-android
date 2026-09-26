package org.onebusaway.android.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.adapters.toOnDemandService
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.OnDemandServiceDto
import org.onebusaway.android.models.EligibilityRequirement

class OnDemandEligibilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an absent eligibility decodes to null`() {
        val dto = json.decodeFromString<OnDemandServiceDto>("""{"id":"a_1","agencyId":"a","name":"Ride","serviceKind":"zone"}""")
        assertNull(dto.eligibility)
        assertNull(EntryWithReferences(dto).toOnDemandService().eligibility)
    }

    @Test
    fun `certificationRequired adapts with its info url`() {
        val dto = json.decodeFromString<OnDemandServiceDto>(
            """{"id":"a_1","agencyId":"a","name":"Ride","serviceKind":"zone","eligibility":{"requirement":"certificationRequired","infoUrl":"https://example.org/apply"}}"""
        )
        val service = EntryWithReferences(dto).toOnDemandService()
        assertEquals(EligibilityRequirement.CERTIFICATION_REQUIRED, service.eligibility?.requirement)
        assertEquals("https://example.org/apply", service.eligibility?.infoUrl)
    }

    @Test
    fun `an unrecognised requirement reads as unknown`() {
        val dto = json.decodeFromString<OnDemandServiceDto>(
            """{"id":"a_1","agencyId":"a","name":"Ride","serviceKind":"zone","eligibility":{"requirement":"ageRestricted","infoUrl":null}}"""
        )
        assertEquals(EligibilityRequirement.UNKNOWN, EntryWithReferences(dto).toOnDemandService().eligibility?.requirement)
    }
}
