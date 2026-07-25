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
package org.onebusaway.android.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.region.Region

/**
 * Guards [RegionUtils.open311ServersFrom], the fixed-region build flavor's Open311 array.
 *
 * A plain JVM test: the helper takes its three values as parameters rather than reading
 * `BuildConfig` (which is a per-variant constant a unit test can't vary), so the no-Open311 case is
 * reachable without building a flavor that has one.
 */
class RegionUtilsOpen311Test {

    /**
     * The regression. A flavor with no `FIXED_REGION_OPEN311_BASE_URL` used to produce a **null**
     * array, which `Region`'s non-null `open311Servers` rejected — crashing the app at launch, with
     * an R8-rewritten message naming no field. Having no Open311 endpoint is ordinary configuration,
     * so it must yield a region with no Open311 servers.
     */
    @Test
    fun noBaseUrlYieldsAnEmptyArrayNotNull() {
        val servers = RegionUtils.open311ServersFrom("jurisdiction", "key", null)
        assertArrayEquals(emptyArray<Region.Open311Server>(), servers)
        // The point of the fix: this is exactly what Region's own default is, so it constructs.
        assertEquals("a region with no Open311 servers still builds", 0, Region(open311Servers = servers).open311Servers.size)
    }

    /**
     * A blank base URL is the same fact as a missing one. `buildConfigField` values are hand-written
     * in a Groovy flavor file, where "no endpoint" is spelled `null` by convention but `""` is the
     * equally natural typo; letting the two diverge would register an endpoint with an empty base URL
     * against `Open311Manager` — live, and broken — instead of registering none.
     */
    @Test
    fun aBlankBaseUrlCountsAsNoBaseUrl() {
        assertEquals(0, RegionUtils.open311ServersFrom("jurisdiction", "key", "").size)
        assertEquals(0, RegionUtils.open311ServersFrom("jurisdiction", "key", "   ").size)
    }

    @Test
    fun aBaseUrlYieldsThatOneServer() {
        val servers = RegionUtils.open311ServersFrom("jurisdiction", "key", "https://example.org/open311/v2/")
        assertEquals(1, servers.size)
        assertEquals("jurisdiction", servers[0].jurisdictionId)
        assertEquals("key", servers[0].apiKey)
        assertEquals("https://example.org/open311/v2/", servers[0].baseUrl)
    }

    /**
     * A null jurisdiction id / api key alongside a real base URL is a shipped configuration
     * (`agencyY` sets `FIXED_REGION_OPEN311_JURISDICTION_ID` to null), and both are nullable on
     * [Region.Open311Server] — so it must build rather than throw.
     */
    @Test
    fun nullJurisdictionAndKeyAreCarriedThrough() {
        val servers = RegionUtils.open311ServersFrom(null, null, "https://example.org/open311/v2/")
        assertEquals(1, servers.size)
        assertEquals(null, servers[0].jurisdictionId)
        assertEquals(null, servers[0].apiKey)
    }
}
