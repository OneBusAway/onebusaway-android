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
package org.onebusaway.android.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure region-status decision functions extracted from
 * `HomeActivity.checkRegionStatus()` and `ObaRegionsTask.onPostExecute`. The blocking IO wrapper
 * (`DefaultRegionRepository.refresh`) is exercised at the ViewModel layer (P8); here we cover the
 * branch logic that the legacy code never had a seam to test.
 */
class RegionStatusTest {

    // --- shouldForceReload ---

    @Test
    fun `force reload when there is no region yet`() {
        assertTrue(
            shouldForceReload(hasRegion = false, lastUpdate = 0, now = 0, oldVer = 5, newVer = 5)
        )
    }

    @Test
    fun `force reload when the cache is older than the threshold`() {
        val now = REGION_UPDATE_THRESHOLD_MS * 3
        val stale = now - REGION_UPDATE_THRESHOLD_MS - 1
        assertTrue(
            shouldForceReload(hasRegion = true, lastUpdate = stale, now = now, oldVer = 5, newVer = 5)
        )
    }

    @Test
    fun `force reload when the app version increased`() {
        val now = REGION_UPDATE_THRESHOLD_MS * 3
        val fresh = now - 1000
        assertTrue(
            shouldForceReload(hasRegion = true, lastUpdate = fresh, now = now, oldVer = 4, newVer = 5)
        )
    }

    @Test
    fun `no force reload when region is fresh and version is unchanged`() {
        val now = REGION_UPDATE_THRESHOLD_MS * 3
        val fresh = now - 1000
        assertFalse(
            shouldForceReload(hasRegion = true, lastUpdate = fresh, now = now, oldVer = 5, newVer = 5)
        )
    }

    // --- resolveRegionStatus (auto-select on) ---

    @Test
    fun `no current region and a closest match auto-selects it`() {
        val closest = region(1)
        assertEquals(
            RegionStatus.Changed(closest),
            resolveRegionStatus(current = null, closest = closest, autoSelect = true)
        )
    }

    @Test
    fun `no current region and no closest match needs manual selection`() {
        assertTrue(
            resolveRegionStatus(current = null, closest = null, autoSelect = true)
                is RegionStatus.NeedsManualSelection
        )
    }

    @Test
    fun `a closer different region replaces the current one`() {
        val closest = region(2)
        assertEquals(
            RegionStatus.Changed(closest),
            resolveRegionStatus(current = region(1), closest = closest, autoSelect = true)
        )
    }

    @Test
    fun `the same closest region as current is unchanged`() {
        assertEquals(
            RegionStatus.Unchanged,
            resolveRegionStatus(current = region(1), closest = region(1), autoSelect = true)
        )
    }

    @Test
    fun `a current region with no closest match is unchanged`() {
        assertEquals(
            RegionStatus.Unchanged,
            resolveRegionStatus(current = region(1), closest = null, autoSelect = true)
        )
    }

    // --- resolveRegionStatus (auto-select off) ---

    @Test
    fun `auto-select off with no current region needs manual selection`() {
        assertTrue(
            resolveRegionStatus(current = null, closest = null, autoSelect = false)
                is RegionStatus.NeedsManualSelection
        )
    }

    @Test
    fun `auto-select off with a current region is unchanged`() {
        assertEquals(
            RegionStatus.Unchanged,
            resolveRegionStatus(current = region(1), closest = region(2), autoSelect = false)
        )
    }

}
