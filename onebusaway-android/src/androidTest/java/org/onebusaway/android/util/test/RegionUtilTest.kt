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

    @Test fun regionSpan() {
        val results = DoubleArray(4)
        RegionUtils.getRegionSpan(tampa, results)
        listOf(0.542461f, 0.576357f, 27.9769105f, -82.445851f).forEachIndexed { index, value ->
            assertEquals(value, results[index].toFloat(), SPAN_TOLERANCE_DEG)
        }
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
         * Degrees, not metres: the span assertions below compare coordinate spans/centres, so the
         * metre-scale tolerance above would make them vacuous.
         */
        const val SPAN_TOLERANCE_DEG = 1e-4f
    }
}
