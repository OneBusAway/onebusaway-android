package org.onebusaway.android.util.test

import android.content.Context
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.mock.MockRegion
import org.onebusaway.android.region.Region
import org.onebusaway.android.util.RegionUtils
import org.onebusaway.android.util.locationOf

@RunWith(AndroidJUnit4::class)
class RegionUtilTest {
    private lateinit var context: Context
    private lateinit var pugetSound: Region
    private lateinit var tampa: Region
    private lateinit var seattle: Location
    private lateinit var tampaLocation: Location

    @Before fun before() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        pugetSound = MockRegion.getPugetSound(context)
        tampa = MockRegion.getTampa(context)
        seattle = locationOf(47.6097, -122.3331)
        tampaLocation = locationOf(27.9681, -82.4764)
    }

    @Test fun distanceAway() {
        assertEquals(1210f, requireNotNull(RegionUtils.getDistanceAway(pugetSound, seattle)), DISTANCE_TOLERANCE_M)
        assertEquals(3160f, requireNotNull(RegionUtils.getDistanceAway(tampa, tampaLocation)), DISTANCE_TOLERANCE_M)
        assertNull(RegionUtils.getDistanceAway(Region(id = -2, name = "No Bounds", active = true), seattle))
    }

    @Test fun closestRegion() {
        val regions = listOf(pugetSound, tampa)
        assertEquals(RegionUtils.PUGET_SOUND_REGION_ID.toLong(), RegionUtils.getClosestRegion(context, regions, seattle, false)?.id)
        assertEquals(RegionUtils.TAMPA_REGION_ID.toLong(), RegionUtils.getClosestRegion(context, regions, tampaLocation, true)?.id)
        assertNull(RegionUtils.getClosestRegion(context, regions, locationOf(51.5072, -0.1275), true))
    }

    /**
     * [RegionUtils.getRegionSpan] returns the bounding box of *all* a region's bounds, so it is tested
     * against a synthetic two-bound region rather than a bundled one.
     *
     * Two reasons. A single-bound region just echoes its own `latSpan`/`lonSpan` back, asserting
     * nothing about the union math — which is the only thing this function does. And pinning a bundled
     * region couples the test to `regions_v3.json`, a live config source that legitimately drifts
     * (`RegionsDecodeTest` is deliberately the only test that pins a bundled value).
     *
     *   bound A: lat 10.0 ± 1.0 -> 9.0..11.0    lon 20.0 ± 2.0  -> 18.0..22.0
     *   bound B: lat 12.0 ± 1.0 -> 11.0..13.0   lon 19.0 ± 0.5  -> 18.5..19.5
     *   union:   lat 9.0..13.0  (span 4.0, centre 11.0)
     *            lon 18.0..22.0 (span 4.0, centre 20.0)
     */
    @Test fun regionSpan() {
        val region = Region(
            id = -3,
            name = "Test-TwoBounds",
            active = true,
            bounds = arrayOf(
                Region.Bounds(10.0, 20.0, 2.0, 4.0),
                Region.Bounds(12.0, 19.0, 2.0, 1.0)
            )
        )
        val results = DoubleArray(4)
        RegionUtils.getRegionSpan(region, results)
        assertEquals(4.0, results[0], SPAN_TOLERANCE_DEG)
        assertEquals(4.0, results[1], SPAN_TOLERANCE_DEG)
        assertEquals(11.0, results[2], SPAN_TOLERANCE_DEG)
        assertEquals(20.0, results[3], SPAN_TOLERANCE_DEG)
    }

    @Test fun locationWithinRegion() {
        assertTrue(RegionUtils.isLocationWithinRegion(seattle, pugetSound))
        assertFalse(RegionUtils.isLocationWithinRegion(tampaLocation, pugetSound))
        assertTrue(RegionUtils.isLocationWithinRegion(tampaLocation, tampa))
    }

    @Test fun regionUsable() {
        assertTrue(RegionUtils.isRegionUsable(context, pugetSound))
        assertFalse(RegionUtils.isRegionUsable(context, MockRegion.getRegionWithoutObaApis()))
        assertFalse(RegionUtils.isRegionUsable(context, MockRegion.getInactiveRegion()))
    }

    private companion object {
        const val DISTANCE_TOLERANCE_M = 2f

        /**
         * Degrees, not metres: [regionSpan] compares coordinate spans/centres, so the metre-scale
         * tolerance above would make those assertions vacuous. The inputs are exact decimals, so this
         * only absorbs floating-point noise.
         */
        const val SPAN_TOLERANCE_DEG = 1e-9
    }
}
