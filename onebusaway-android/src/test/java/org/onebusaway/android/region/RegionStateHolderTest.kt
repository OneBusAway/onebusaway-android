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
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [RegionStateHolder] — the observable region-state holder the repository exposes. */
class RegionStateHolderTest {

    @Test
    fun `starts with a null region and a Resolving state`() {
        // The state starts Resolving (not Active) so cold-start consumers can tell "not resolved yet" from
        // a real Active (#1969); the repository's init settles it via activated().
        val holder = RegionStateHolder()
        assertNull(holder.region.value)
        assertEquals(RegionState.Resolving, holder.state.value)
    }

    @Test
    fun `activated updates region and state, including back to null`() {
        val holder = RegionStateHolder()
        holder.activated(region(2))
        assertEquals(region(2), holder.region.value)
        assertEquals(RegionState.Active(region(2)), holder.state.value)
        holder.activated(null)
        assertNull(holder.region.value)
        assertEquals(RegionState.Active(null), holder.state.value)
    }

    @Test
    fun `resolving and failed change state but keep the last region`() {
        // Region equality is id-based, so region(1) matches the activated region(1).
        val holder = RegionStateHolder().apply { activated(region(1)) }
        holder.resolving()
        assertEquals(region(1), holder.region.value)
        assertEquals(RegionState.Resolving, holder.state.value)
        holder.failed()
        assertEquals(region(1), holder.region.value)
        assertEquals(RegionState.Failed, holder.state.value)
    }

    @Test
    fun `needsChoice carries the regions and keeps the last region`() {
        val holder = RegionStateHolder()
        val regions = listOf(region(1), region(2))
        holder.needsChoice(regions)
        assertNull(holder.region.value)
        assertEquals(RegionState.NeedsManualChoice(regions), holder.state.value)
    }
}
