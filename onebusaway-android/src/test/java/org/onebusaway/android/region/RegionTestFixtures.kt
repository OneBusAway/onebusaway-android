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
    private val _state = MutableStateFlow<RegionState>(RegionState.Active(initial))
    override val state: StateFlow<RegionState> = _state

    /** The outcome [refresh] returns; set per test (default mirrors a no-op refresh). */
    var refreshResult: RegionStatus = RegionStatus.Unchanged
    var refreshCount = 0
    val chosen = mutableListOf<Region>()
    val customApiUrls = mutableListOf<Pair<String?, String?>>()

    override suspend fun refresh(): RegionStatus { refreshCount++; return refreshResult }
    override suspend fun choose(region: Region) { chosen.add(region); emit(region) }
    override fun clear() = emit(null)
    override fun applyRegion(region: Region?, regionChanged: Boolean) = emit(region)

    override fun applyCustomApiUrls(obaUrl: String?, otpUrl: String?) {
        customApiUrls.add(obaUrl to otpUrl)
        // Mirror the real repo's observable effect: a custom OBA endpoint clears the current region.
        if (obaUrl != null) emit(null)
    }

    fun emit(region: Region?) { _region.value = region; _state.value = RegionState.Active(region) }
    /** Drives the richer [state] flow directly (Resolving / NeedsManualChoice / Failed). */
    fun emitState(state: RegionState) { _state.value = state }
}

/** Minimal [Region] fixture — only the id matters to the selection logic (compared by id). */
internal fun region(
    id: Long,
    supportsOtpBikeshare: Boolean = false,
    twitterUrl: String? = null,
): Region = Region(
        id,            // id
        "Region $id",  // name
        true,          // active
        null,          // obaBaseUrl
        null,          // siriBaseUrl
        emptyArray(),  // bounds
        emptyArray(),  // open311Servers
        null,          // lang
        null,          // contactEmail
        true,          // supportsObaDiscoveryApis
        true,          // supportsObaRealtimeApis
        false,         // supportsSiriRealtimeApis
        twitterUrl,    // twitterUrl
        false,         // experimental
        null,          // stopInfoUrl
        null,          // otpBaseUrl
        null,          // otpContactEmail
        supportsOtpBikeshare, // supportsOtpBikeshare
        false,         // supportsEmbeddedSocial
        null,          // paymentAndroidAppId
        null,          // paymentWarningTitle
        null,          // paymentWarningBody
        false,         // travelBehaviorDataCollectionEnabled
        false,         // enrollParticipantsInStudy
        null,          // sidecarBaseUrl
        null,          // plausibleAnalyticsServerUrl
        null           // umamiAnalytics (UmamiAnalyticsConfig)
    )
