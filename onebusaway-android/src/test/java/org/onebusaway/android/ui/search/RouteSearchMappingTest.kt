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
package org.onebusaway.android.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpURLConnection
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.listOrEmpty
import org.onebusaway.android.util.routeDisplayNames

/**
 * Pure-logic coverage for the routes-for-location handling: [listOrEmpty] (OK reply vs server
 * error code) and the field-based [routeDisplayNames] fallback the route mapper relies on.
 */
class RouteSearchMappingTest {

    private fun envelope(code: Int, routes: List<RouteReference>) =
        ObaEnvelope(code = code, data = ListWithReferences(list = routes))

    @Test
    fun listOrEmptyReturnsListOnOk() {
        val routes = listOf(RouteReference(id = "1_8", shortName = "8"))
        assertEquals(routes, envelope(HttpURLConnection.HTTP_OK, routes).listOrEmpty())
    }

    @Test
    fun listOrEmptyReturnsEmptyOnErrorCode() {
        val routes = listOf(RouteReference(id = "1_8", shortName = "8"))
        // A server error code is treated as "no results", not a failure.
        assertEquals(emptyList<RouteReference>(), envelope(HttpURLConnection.HTTP_NOT_FOUND, routes).listOrEmpty())
    }

    @Test
    fun listOrEmptyReturnsEmptyWhenDataNull() {
        val empty = ObaEnvelope<ListWithReferences<RouteReference>>(code = HttpURLConnection.HTTP_OK, data = null)
        assertEquals(emptyList<RouteReference>(), empty.listOrEmpty())
    }

    @Test
    fun displayNamesFallBackToLongNameAndDescription() {
        // Short name present, long name blank -> secondary line falls back to the description.
        val names = routeDisplayNames("8", "", "Capitol Hill - Rainier Beach")
        assertEquals("8", names.shortName)
        assertEquals("Capitol Hill - Rainier Beach", names.longName)
    }

    @Test
    fun displayNamesShortFallsBackToLongWhenShortMissing() {
        val names = routeDisplayNames(null, "Link", null)
        assertEquals("Link", names.shortName)
        // long name equals the resolved short name, and no description -> no secondary line.
        assertNull(names.longName)
    }
}
