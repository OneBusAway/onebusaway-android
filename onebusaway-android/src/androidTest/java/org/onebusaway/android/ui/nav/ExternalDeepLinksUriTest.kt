/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.nav

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [ExternalDeepLinks]'s `Uri` decomposition — the one Android-dependent part of the deep-link
 * vocabulary, and so the one part `ExternalDeepLinksTest` (a pure JVM test over the already-decomposed
 * `Link`) can't reach. `android.net.Uri` is stubbed in unit tests, so these run on a device.
 *
 * What's actually at risk here is the decomposition's edge behaviour rather than the vocabulary:
 * `getQueryParameterNames()` throws on an opaque URI, and scheme/host arrive with whatever case the
 * sender used while the parser compares them by equality.
 */
@RunWith(AndroidJUnit4::class)
class ExternalDeepLinksUriTest {

    @Test
    fun `a custom-scheme stop URI parses`() {
        assertEquals(
            ExternalDeepLinks.Target.Stop("1_75403"),
            ExternalDeepLinks.parse("onebusaway://view-stop?stopID=1_75403&regionID=1".toUri())
        )
    }

    @Test
    fun `a percent-encoded stop id is decoded`() {
        assertEquals(
            ExternalDeepLinks.Target.Stop("1_7 5403"),
            ExternalDeepLinks.parse("onebusaway://view-stop?stopID=1_7%205403".toUri())
        )
    }

    @Test
    fun `a web trip URI parses`() {
        assertEquals(
            ExternalDeepLinks.Target.Trip(tripId = "1_18196913", stopId = "1_75403"),
            ExternalDeepLinks.parse(
                (
                    "https://onebusaway.co/regions/1/stops/1_75403/trips" +
                        "?trip_id=1_18196913&service_date=1698307200.0&stop_sequence=5"
                    ).toUri()
            )
        )
    }

    @Test
    fun `an opaque URI is rejected rather than throwing`() {
        // No `//`, so this is opaque: Uri.getQueryParameterNames() throws UnsupportedOperationException
        // on it. toLink() guards that with isHierarchical; without the guard this test crashes.
        assertNull(ExternalDeepLinks.parse("onebusaway:view-stop?stopID=1_75403".toUri()))
        assertNull(ExternalDeepLinks.parse("mailto:someone@example.com".toUri()))
    }

    @Test
    fun `scheme and host are matched case-insensitively`() {
        // IntentFilter matches these case-insensitively, so a link that reaches us may carry any case;
        // toLink() normalizes before the parser's equality comparisons.
        assertEquals(
            ExternalDeepLinks.Target.Stop("1_75403"),
            ExternalDeepLinks.parse("OneBusAway://view-stop?stopID=1_75403".toUri())
        )
        assertEquals(
            ExternalDeepLinks.Target.Trip(tripId = "1_18196913", stopId = "1_75403"),
            ExternalDeepLinks.parse(
                "HTTPS://OneBusAway.CO/regions/1/stops/1_75403/trips?trip_id=1_18196913".toUri()
            )
        )
    }

    @Test
    fun `the app's internal content URIs are not external deep links`() {
        val internal = "content://com.joulespersecond.oba/stops/1_75403".toUri()
        assertNull(ExternalDeepLinks.parse(internal))
        assertFalse(ExternalDeepLinks.isUnhandledWebLink(internal))
    }

    @Test
    fun `a claimed but unroutable web URI is reported as an unhandled web link`() {
        // The intent-filter can't require trip_id, so this URL launches the app with nowhere to go;
        // HomeActivity hands it back to the browser on the strength of this predicate.
        val trimmed = "https://onebusaway.co/regions/1/stops/1_75403/trips".toUri()
        assertNull(ExternalDeepLinks.parse(trimmed))
        assertTrue(ExternalDeepLinks.isUnhandledWebLink(trimmed))
    }
}
