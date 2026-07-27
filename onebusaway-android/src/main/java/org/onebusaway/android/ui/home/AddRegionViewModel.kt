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
package org.onebusaway.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.region.CustomRegionRequest
import org.onebusaway.android.region.RegionRepository

/**
 * The consent gate on the `add-region` deep link (#2027, #2030).
 *
 * The link's intent-filter is `BROWSABLE`, so **any web page** can fire one at the app, and accepting it
 * repoints every transit request the app makes at a server of the sender's choosing. That is a decision
 * only the rider can make, so nothing is written until [confirm]: `HomeActivity` hands the parsed request
 * here via [request], the dialog renders it, and only a confirm reaches [RegionRepository]. This is a
 * deliberate divergence from OneBusAway for iOS, which applies the region with no prompt.
 *
 * [invalid] carries the one failure the rider needs to hear about — a request the region domain rejected
 * because its OBA URL isn't a valid URL — mirroring iOS's error alert.
 */
@HiltViewModel
class AddRegionViewModel @Inject constructor(
    private val regionRepo: RegionRepository
) : ViewModel() {

    // The request awaiting the rider's decision, or null when there is nothing to confirm.
    private val _pending = MutableStateFlow<CustomRegionRequest?>(null)
    val pending: StateFlow<CustomRegionRequest?> = _pending.asStateFlow()

    // Set when a confirmed request was rejected as unusable; cleared by [dismissInvalid].
    private val _invalid = MutableStateFlow(false)
    val invalid: StateFlow<Boolean> = _invalid.asStateFlow()

    /**
     * Stages an incoming `add-region` link for confirmation. A second link arriving before the first is
     * answered replaces it — the newest link is the one the rider just followed, and stacking consent
     * dialogs would invite answering the wrong one.
     */
    fun request(request: CustomRegionRequest) {
        _pending.value = request
    }

    /** The rider accepted: add the region and switch to it. */
    fun confirm() {
        val request = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            if (regionRepo.addCustomRegion(request) == null) _invalid.value = true
        }
    }

    /** The rider declined (or dismissed the dialog): drop the request, write nothing. */
    fun decline() {
        _pending.value = null
    }

    /** Acknowledges the [invalid] alert. */
    fun dismissInvalid() {
        _invalid.value = false
    }
}
