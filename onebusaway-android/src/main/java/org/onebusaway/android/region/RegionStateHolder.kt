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
import org.onebusaway.android.region.Region

/**
 * The observable region state ([region] + [state]) that [DefaultRegionRepository] exposes. Extracted
 * so the state transitions stay JVM-unit-testable without a `Context` — the
 * repository's resolution is `Context`-coupled (it calls `RegionUtils.getRegions` etc.), but the
 * holder is pure. The repository's `refresh`/`choose`/`applyRegion` writers feed it.
 *
 * Both flows start empty — a null [region] and a [RegionState.Resolving] [state] — and [activated] is the
 * only writer that installs a region; [region] is left untouched by [resolving]/[needsChoice]/[failed] (the
 * last region still applies while a refresh is in flight or after a failure). [state] adds the transient
 * [RegionState.Resolving] / [RegionState.NeedsManualChoice] / [RegionState.Failed] nuance ([region] is null
 * whenever a custom API URL is configured).
 *
 * [state] starts [RegionState.Resolving], **not** [RegionState.Active]: at construction no resolution has
 * completed, so a consumer that takes a one-shot action on the first state it sees can tell "not resolved
 * yet" (transient — a value is coming) apart from a deliberate `Active(null)` (a custom API URL is
 * configured, so there is no region). [DefaultRegionRepository]'s `init` settles this once the persisted
 * region has loaded — see its `init` block (#1969).
 *
 * The writers are `@Synchronized` so a compound transition (the [settleIfResolving] check-and-write) is
 * atomic with respect to the others. That serializes *writers* only: [region] and [state] are separate
 * flows, so a reader collecting one can still momentarily observe it against the other's prior value.
 * Readers that need both consistent should derive from [state] alone (its [RegionState.Active] carries the
 * region), not pair [region] with [state].
 */
class RegionStateHolder {

    private val _region = MutableStateFlow<Region?>(null)
    val region: StateFlow<Region?> = _region.asStateFlow()

    private val _state = MutableStateFlow<RegionState>(RegionState.Resolving)
    val state: StateFlow<RegionState> = _state.asStateFlow()

    /** A region (or null for a custom API URL) became active: moves both [region] and [state]. */
    @Synchronized
    fun activated(region: Region?) {
        _region.value = region
        _state.value = RegionState.Active(region)
    }

    /**
     * Settles the initial [RegionState.Resolving] seed to [RegionState.Active] — the repository init's
     * persisted-region write. A no-op unless [state] is still [RegionState.Resolving], so a concurrent
     * refresh that has already settled to Active/NeedsManualChoice/Failed is never clobbered — and unlike
     * a bare check at the call site, the check and the write are atomic with the other writers (all
     * `@Synchronized`), closing the interleaving where a refresh settles between them (#1969). A refresh's
     * own transient [resolving] *is* settled over deliberately: it publishes its result regardless, so
     * the early seed only fills the wait.
     */
    @Synchronized
    fun settleIfResolving(region: Region?) {
        if (_state.value == RegionState.Resolving) activated(region)
    }

    /** A resolution is in flight; [region] keeps its last value. */
    @Synchronized
    fun resolving() {
        _state.value = RegionState.Resolving
    }

    /** No region could be auto-selected; the user must pick from [regions]. [region] keeps its value. */
    @Synchronized
    fun needsChoice(regions: List<Region>) {
        _state.value = RegionState.NeedsManualChoice(regions)
    }

    /** Region info could not be loaded; [region] keeps its last value. */
    @Synchronized
    fun failed() {
        _state.value = RegionState.Failed
    }
}
