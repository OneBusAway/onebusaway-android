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
import org.onebusaway.android.region.Region

/**
 * A controllable [RegionRepository] for ViewModel tests; shared across the region and home test
 * packages (see [region]). It drives both the observable [region]/[state] flows and the resolve
 * action ([refresh]/[choose]) — folding in the former `FakeRegionStatusRepository` now that
 * resolution lives in the repository.
 */
internal class FakeRegionRepository(initial: Region? = null) : RegionRepository {
    private val _region = MutableStateFlow(initial)
    override val region: StateFlow<Region?> = _region
    private val _regionPresent = MutableStateFlow(initial != null)
    override val regionPresent: StateFlow<Boolean> = _regionPresent
    private val _state = MutableStateFlow<RegionState>(RegionState.Active(initial))
    override val state: StateFlow<RegionState> = _state

    /** The outcome [refresh] returns; set per test (default mirrors a no-op refresh). */
    var refreshResult: RegionStatus = RegionStatus.Unchanged
    var refreshCount = 0
    val chosen = mutableListOf<Region>()

    /** Custom regions added via [addCustomRegion], in order. */
    val addedCustomRegions = mutableListOf<CustomRegionRequest>()

    /** Custom regions passed to [deleteCustomRegion], in order. */
    val deletedCustomRegions = mutableListOf<Region>()

    /** When false, [addCustomRegion] reports the request as unusable (the invalid-URL path). */
    var addCustomRegionSucceeds = true

    override suspend fun refresh(): RegionStatus {
        refreshCount++
        return refreshResult
    }
    override suspend fun choose(region: Region) {
        chosen.add(region)
        emit(region)
    }
    override fun clear() = emit(null)
    override fun applyRegion(region: Region?, regionChanged: Boolean) = emit(region)

    override suspend fun addCustomRegion(request: CustomRegionRequest): Region? {
        // Reject before recording, mirroring the real repository: an invalid request is never saved, so a
        // fake that records it first would let a test pass on behaviour production doesn't have.
        if (!addCustomRegionSucceeds) return null
        addedCustomRegions.add(request)
        // Mirror the real repo's observable effect: the new region becomes current.
        val added = customRegion(FIRST_CUSTOM_REGION_ID, request)
        emit(added)
        return added
    }

    override suspend fun deleteCustomRegion(region: Region) {
        // The real repository ignores a directory region outright; so must this, or a test could rely on
        // deleting one working.
        if (!region.custom) return
        deletedCustomRegions.add(region)
        if (_region.value?.id == region.id) emit(null)
    }

    fun emit(region: Region?) {
        _region.value = region
        _regionPresent.value = region != null
        _state.value = RegionState.Active(region)
    }

    /** Drives the richer [state] flow directly (Resolving / NeedsManualChoice / Failed). */
    fun emitState(state: RegionState) {
        _state.value = state
    }
}

/** Minimal [Region] fixture — only the id matters to the selection logic (compared by id). */
internal fun region(
    id: Long,
    supportsOtpBikeshare: Boolean = false,
    twitterUrl: String? = null,
    otpContactEmail: String? = null,
    custom: Boolean = false,
    sidecarBaseUrl: String? = null,
    sidecarRegionId: Long? = null
): Region = Region(
    id = id,
    name = "Region $id",
    active = true,
    obaBaseUrl = null,
    siriBaseUrl = null,
    bounds = emptyArray(),
    open311Servers = emptyArray(),
    language = null,
    contactEmail = null,
    supportsObaDiscoveryApis = true,
    supportsObaRealtimeApis = true,
    supportsSiriRealtimeApis = false,
    twitterUrl = twitterUrl,
    experimental = false,
    stopInfoUrl = null,
    otpBaseUrl = null,
    otpContactEmail = otpContactEmail,
    supportsOtpBikeshare = supportsOtpBikeshare,
    otpBaseGraphqlUrl = null,
    supportsOtpGraphqlBikeshare = false,
    supportsEmbeddedSocial = false,
    paymentAndroidAppId = null,
    paymentWarningTitle = null,
    paymentWarningBody = null,
    sidecarBaseUrl = sidecarBaseUrl,
    plausibleAnalyticsServerUrl = null,
    umamiAnalytics = null,
    custom = custom,
    sidecarRegionId = sidecarRegionId
)
