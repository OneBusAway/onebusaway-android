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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onebusaway.android.io.elements.ObaRegion

/**
 * The observable region state ([region] + [state]) that [DefaultRegionRepository] exposes. Extracted
 * so the state transitions stay JVM-unit-testable without a `Context` — the
 * repository's resolution is `Context`-coupled (it calls `RegionUtils.getRegions` etc.), but the
 * holder is pure. Both the repository's `refresh`/`choose` and the legacy `Application.setCurrentRegion`
 * bridge feed it.
 *
 * [region] holds the last-set region (null when a custom API URL is configured); [state] adds the
 * transient [RegionState.Resolving] / [RegionState.NeedsManualChoice] / [RegionState.Failed] nuance.
 * [region] is left untouched by [resolving]/[needsChoice]/[failed] (the last region still applies while
 * a refresh is in flight or after a failure); only [activated] moves it.
 */
class RegionStateHolder(seed: ObaRegion?) {

    private val _region = MutableStateFlow(seed)
    val region: StateFlow<ObaRegion?> = _region.asStateFlow()

    private val _state = MutableStateFlow<RegionState>(RegionState.Active(seed))
    val state: StateFlow<RegionState> = _state.asStateFlow()

    /** A region (or null for a custom API URL) became active: moves both [region] and [state]. */
    fun activated(region: ObaRegion?) {
        _region.value = region
        _state.value = RegionState.Active(region)
    }

    /** A resolution is in flight; [region] keeps its last value. */
    fun resolving() {
        _state.value = RegionState.Resolving
    }

    /** No region could be auto-selected; the user must pick from [regions]. [region] keeps its value. */
    fun needsChoice(regions: List<ObaRegion>) {
        _state.value = RegionState.NeedsManualChoice(regions)
    }

    /** Region info could not be loaded; [region] keeps its last value. */
    fun failed() {
        _state.value = RegionState.Failed
    }
}
