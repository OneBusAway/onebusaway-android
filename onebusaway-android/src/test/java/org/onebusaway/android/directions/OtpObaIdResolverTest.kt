package org.onebusaway.android.directions

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.data.AgenciesDataSource
import org.onebusaway.android.api.data.StopsForRouteRepository
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.models.AgencyContact
import org.onebusaway.android.testing.FakeStopsForRouteRepository

/**
 * JVM tests for [OtpObaIdResolver]'s derive → verify → name-fallback route resolution and its
 * resolve-against-the-route's-own-stops stop resolution (#2170), over Puget-Sound-shaped data (verified
 * against the live OTP/OBA deployments).
 */
class OtpObaIdResolverTest {

    // The covered OBA agencies a test asserts on, with the ids/names Puget Sound's
    // agencies-with-coverage reports (checked 2026-08-04). Note 97 is Everett Transit — Skagit and
    // Whatcom are in the OTP graph but are NOT covered OBA agencies, hence the null case below.
    private val coverage = listOf(
        agency("1", "Metro Transit"),
        agency("19", "Intercity Transit"),
        agency("40", "Sound Transit"),
        agency("97", "Everett Transit")
    )

    private fun resolver(
        agencies: Result<List<AgencyContact>> = Result.success(coverage),
        routeStops: StopsForRouteRepository = FakeStopsForRouteRepository()
    ) = OtpObaIdResolver(
        object : AgenciesDataSource {
            override suspend fun getAgencies() = agencies
        },
        routeStops
    )

    private fun agency(id: String, name: String) = AgencyContact(id = id, name = name, email = null, url = null, phone = null)

    private fun routeStops(vararg stops: Pair<String, List<String>>) = FakeStopsForRouteRepository(stopIds = mutableMapOf(*stops))

    /** A transit leg on [routeGtfsId] under [agencyGtfsId], boarding at [from] and alighting at [to]. */
    private fun leg(routeGtfsId: String, agencyGtfsId: String, agencyName: String, from: String? = null, to: String? = null) = TripLeg(
        routeId = routeGtfsId,
        agencyId = agencyGtfsId,
        agencyName = agencyName,
        from = TripPlace(stopId = from),
        to = TripPlace(stopId = to)
    )

    @Test
    fun derivedAgencySuffix_whenCovered() = runTest {
        // kcm:1 → suffix "1" is a covered agency → OBA route 1_102574. The feed prefix carries no
        // weight of its own: Sound Transit is published under four Puget Sound feeds (kcm, Pierce,
        // CommTrans, and its own "40"), all suffixed :40, and every one resolves to OBA agency 40.
        assertEquals(
            "1_102574",
            resolver().obaRouteId("kcm:102574", agencyGtfsId = "kcm:1", agencyName = "Metro Transit")
        )
    }

    @Test
    fun stopDoesNotTakeItsRouteAgencyPrefix() = runTest {
        // #2170: ST 522 is agency 40 in both OTP (kcm:40) and OBA (40_100232), but every stop it calls
        // at is a Metro stop — 40_23561 does not exist. The route's own stop list is what says so.
        val stops = routeStops("40_100232" to listOf("1_23561", "1_76305"))
        val ids = resolver(routeStops = stops).resolveLegs(
            listOf(leg("kcm:100232", "kcm:40", "Sound Transit", from = "kcm:23561", to = "kcm:76305"))
        )

        assertEquals(ResolvedLegIds("40_100232", "1_23561", "1_76305"), ids.single())
    }

    @Test
    fun stopIsFoundWhenARouteSpansTwoAgenciesStops() = runTest {
        // KCM 931 calls at both Metro (1_*) and Community Transit (29_*) stops, so no single prefix
        // could have been guessed for the leg at all. Its board stop even comes from another OTP feed.
        val stops = routeStops("1_102558" to listOf("1_75995", "29_2229"))
        val ids = resolver(routeStops = stops).resolveLegs(
            listOf(leg("kcm:102558", "kcm:1", "Metro Transit", from = "CommTrans:2229", to = "kcm:75995"))
        )

        assertEquals(ResolvedLegIds("1_102558", "29_2229", "1_75995"), ids.single())
    }

    @Test
    fun stopUnresolvable_whenTwoAgenciesShareTheSameEntityId() = runTest {
        // Two of the route's stops split to the same suffix ("123") under different agencies — nothing
        // says which one the leg's board stop actually is, so it's dropped rather than picking one.
        val stops = routeStops("40_100232" to listOf("1_123", "29_123"))
        val ids = resolver(routeStops = stops).resolveLegs(
            listOf(leg("kcm:100232", "kcm:40", "Sound Transit", from = "kcm:123"))
        )

        assertNull(ids.single().fromStopId)
    }

    @Test
    fun stopEntityIdKeepsItsOwnUnderscores() = runTest {
        // OBA ids split at their *first* underscore, so an entity id containing one still matches.
        val stops = routeStops("40_TLINE" to listOf("40_T05_T1"))
        val ids = resolver(routeStops = stops).resolveLegs(
            listOf(leg("40:TLINE", "40:40", "Sound Transit", from = "40:T05_T1"))
        )

        assertEquals("40_T05_T1", ids.single().fromStopId)
    }

    @Test
    fun stopUnresolvable_whenTheRouteDoesNotServeItOrCannotBeReached() = runTest {
        // Nothing to fall back on in either case: guessing the prefix is exactly the bug (#2170), so
        // callers degrade instead. "40_100479" is unstubbed, i.e. the stops-for-route fetch failed.
        val stops = routeStops("40_100232" to listOf("1_23561"))
        val ids = resolver(routeStops = stops).resolveLegs(
            listOf(
                leg("kcm:100232", "kcm:40", "Sound Transit", from = "kcm:99999"),
                leg("40:100479", "40:40", "Sound Transit", from = "40:99")
            )
        )

        assertEquals(listOf(null, null), ids.map { it.fromStopId })
        // The routes themselves still resolve — only their stops are unreachable.
        assertEquals(listOf("40_100232", "40_100479"), ids.map { it.routeId })
    }

    @Test
    fun nonTransitLegResolvesToNothingAndCostsNoFetch() = runTest {
        val stops = routeStops()
        val ids = resolver(routeStops = stops).resolveLegs(listOf(TripLeg()))

        assertEquals(ResolvedLegIds(null, null, null), ids.single())
        assertEquals(emptyList<String>(), stops.stopIdCalls)
    }

    @Test
    fun eachRouteIsFetchedOnceForTheWholeItinerary() = runTest {
        // Three legs, two distinct routes — the shared route's stops are fetched once, and both legs
        // read their own ends out of it.
        val stops = routeStops(
            "40_100232" to listOf("1_23561", "1_76305"),
            "1_102558" to listOf("29_2229", "1_75995")
        )
        val ids = resolver(routeStops = stops).resolveLegs(
            listOf(
                leg("kcm:100232", "kcm:40", "Sound Transit", from = "kcm:23561", to = "kcm:76305"),
                leg("kcm:102558", "kcm:1", "Metro Transit", from = "CommTrans:2229", to = "kcm:75995"),
                leg("kcm:100232", "kcm:40", "Sound Transit", from = "kcm:76305", to = "kcm:23561")
            )
        )

        assertEquals(listOf("1_23561", "29_2229", "1_76305"), ids.map { it.fromStopId })
        assertEquals(setOf("40_100232", "1_102558"), stops.stopIdCalls.toSet())
        assertEquals(2, stops.stopIdCalls.size)
    }

    @Test
    fun numericFeedPrefix_resolvesToItself() = runTest {
        // Sound Transit's own feed is numeric: 40:40 → "40", covered → 40_2LINE.
        assertEquals(
            "40_2LINE",
            resolver().obaRouteId("40:2LINE", agencyGtfsId = "40:40", agencyName = "Sound Transit")
        )
    }

    @Test
    fun nameFallback_whenDerivedAgencyNotCovered() = runTest {
        // Intercity is 19:0 in OTP (suffix "0" isn't covered) but agency "19" in OBA — matched by name.
        assertEquals(
            "19_600",
            resolver().obaRouteId("19:600", agencyGtfsId = "19:0", agencyName = "Intercity Transit")
        )
    }

    @Test
    fun unresolvable_forAnAgencyTheRegionDoesNotCover() = runTest {
        // Skagit is in the Puget Sound OTP graph (with a UUID agency id, so nothing is derivable from
        // it) but is not a covered OBA agency, so neither rule can name it. OTP will happily plan a leg
        // on it; the drawer degrades rather than inventing an id.
        assertNull(
            resolver().obaRouteId(
                "Skagit:42",
                agencyGtfsId = "Skagit:e0e4541a-2714-487b-b30c-f5c6cb4a310f",
                agencyName = "Skagit Transit"
            )
        )
    }

    @Test
    fun unresolvable_whenNeitherSuffixNorNameMatches() = runTest {
        assertNull(
            resolver().obaRouteId("foo:9", agencyGtfsId = "foo:9", agencyName = "Nowhere Transit")
        )
    }

    @Test
    fun offline_trustsDerivedSuffix() = runTest {
        // No coverage data (fetch failed): fall back to the derived suffix (correct for the common case).
        assertEquals(
            "1_102574",
            resolver(Result.failure(RuntimeException("offline")))
                .obaRouteId("kcm:102574", agencyGtfsId = "kcm:1", agencyName = "Metro Transit")
        )
    }

    @Test
    fun nullRoute_returnsNull() = runTest {
        assertNull(resolver().obaRouteId(null, agencyGtfsId = "kcm:1", agencyName = "Metro Transit"))
    }
}
