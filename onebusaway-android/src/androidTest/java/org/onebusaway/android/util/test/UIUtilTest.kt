package org.onebusaway.android.util.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.R
import org.onebusaway.android.api.test.ObaTestCase
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.mock.ArrivalsFixtures
import org.onebusaway.android.mock.MockRegion
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.MyTextUtils

@RunWith(AndroidJUnit4::class)
class UIUtilTest : ObaTestCase() {
    @Test fun formatDisplayText() {
        assertEquals("SDSU Transit Center", MyTextUtils.formatDisplayText("SDSU Transit Center"))
        assertEquals("North To University Area Tc", MyTextUtils.formatDisplayText("NORTH TO UNIVERSITY AREA TC"))
        assertEquals("SPLC / SR 513", MyTextUtils.formatDisplayText("SPLC / SR 513"))
    }

    @Test fun arrivalTimeIndexSearchAndLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tampa = MockRegion.getTampa(context)
        assertNotNull(tampa)
        RegionEntryPoint.get(context).applyRegion(tampa, true)
        val envelope = ArrivalsFixtures.load(context, "arrivals_and_departures_for_stop_hart_6497")
        val arrivals = ArrivalsFixtures.convert(context, envelope, true)
        assertEquals(32, arrivals.size)
        assertEquals(5, ArrivalInfoUtils.findFirstNonNegativeArrival(arrivals))
        assertEquals(
            listOf(11, 13),
            ArrivalInfoUtils.findPreferredArrivalIndexes(arrivals, setOf("Hillsborough Area Regional Transit_6"))?.take(2)
        )
        assertEquals(listOf(5, 6), ArrivalInfoUtils.findPreferredArrivalIndexes(arrivals, emptySet()))
        assertEquals("Arrived on time", arrivals[0].statusText)
        assertEquals("5 min delay", arrivals[7].statusText)
        assertEquals("On time", arrivals[31].statusText)
    }

    @Test fun getAllSituations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferencesEntryPoint.get(context).setString(R.string.preference_key_oba_api_url, "sdmts.onebusway.org/api")
        val routesOnly = ArrivalsFixtures.load(context, "arrivals_and_departures_for_stop_mts_11670_route_alerts")
        assertEquals(0, ArrivalsFixtures.stopSituations(routesOnly).size)
        assertEquals(setOf("MTS_38", "MTS_37", "MTS_28", "MTS_34", "MTS_11", "MTS_33", "MTS_3"), ArrivalsFixtures.allSituations(routesOnly).map { it.id }.toSet())
        val routesAndStop = ArrivalsFixtures.load(context, "arrivals_and_departures_for_stop_mts_13353_route_and_stop_alerts")
        assertEquals(1, ArrivalsFixtures.stopSituations(routesAndStop).size)
        assertEquals(5, ArrivalsFixtures.allSituations(routesAndStop).map { it.id }.toSet().size)
    }
}
