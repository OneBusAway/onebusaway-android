package org.onebusaway.android.util.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.ui.report.ServiceUtils

@RunWith(AndroidJUnit4::class)
class ReportUtilTest {
    @Test fun serviceKeywordMatching() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val matches = arrayOf("Incorrect/Missing Stop ID", "Trash at Bus Stop", "WiFi on bus isn't working", "Positive comments (complement bus driver, etc.)", "Route/trip is missing")
        val tripMatches = arrayOf("Arrival times/schedules", "PSTA - Arrival times/schedules")
        matches.forEach { assertTrue(ServiceUtils.isTransitStopServiceByText(context, it)) }
        tripMatches.forEach { assertTrue(ServiceUtils.isTransitTripServiceByText(context, it)) }
        val misses = arrayOf("Business", "Monkey Business", "Somethingbus With More Words After It")
        misses.forEach { assertFalse(ServiceUtils.isTransitStopServiceByText(context, it)) }
        misses.forEach { assertFalse(ServiceUtils.isTransitTripServiceByText(context, it)) }
    }
}
