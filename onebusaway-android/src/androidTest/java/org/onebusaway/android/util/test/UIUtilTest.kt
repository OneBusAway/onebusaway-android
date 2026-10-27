package org.onebusaway.android.util.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.text.DateFormat
import java.util.Date
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
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.MyTextUtils

@RunWith(AndroidJUnit4::class)
class UIUtilTest : ObaTestCase() {
    /** Converts all-caps headsigns to title case, and keeps mixed-case text and acronyms unchanged. */
    @Test fun formatDisplayText() {
        assertEquals("SDSU Transit Center", MyTextUtils.formatDisplayText("SDSU Transit Center"))
        assertEquals("North To University Area Tc", MyTextUtils.formatDisplayText("NORTH TO UNIVERSITY AREA TC"))
        assertEquals("SPLC / SR 513", MyTextUtils.formatDisplayText("SPLC / SR 513"))
    }

    /** Finds the first upcoming and preferred arrivals in a HART fixture, and checks their status labels. */
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

    /**
     * The "Every N mins from/until" label a frequency-based (`exact_times=0`) trip shows in place of
     * an ETA status (issue #430), against the USF Bull Runner response from that issue: two routes
     * running every 600 s between 07:00 and 00:00 EST.
     */
    @Test fun frequencyStatusLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val envelope = ArrivalsFixtures.load(context, "arrivals_and_departures_for_stop_usf_203_frequency")
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT)
        val windowStart = 1_456_747_200_000L
        val windowEnd = 1_456_808_400_000L

        // The fixture's own currentTime falls inside the window, so service runs until its end.
        val until = context.resources.getQuantityString(R.plurals.stop_info_frequency_until, 10, 10, clock.format(Date(windowEnd)))
        val during = ArrivalsFixtures.convert(context, envelope, true)
        assertEquals(2, during.size)
        during.forEach { assertEquals(until, it.statusText) }

        // An hour before service starts, the label names the start instead.
        val from = context.resources.getQuantityString(R.plurals.stop_info_frequency_from, 10, 10, clock.format(Date(windowStart)))
        val before = ArrivalsFixtures.convert(context, envelope, true, now = ServerTime(windowStart - 3_600_000L))
        assertEquals(2, before.size)
        before.forEach { assertEquals(from, it.statusText) }
    }

    /** Separates stop-level service alerts from route alerts in two MTS fixtures. */
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
