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
package org.onebusaway.android.ui.regions

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.ui.compose.ListLoadingViewModel

/** ViewModel for the region picker screen. */
@HiltViewModel
class RegionsViewModel @Inject constructor(
    private val repository: RegionsRepository,
    regionRepository: RegionRepository
) : ListLoadingViewModel<RegionItem>() {

    /**
     * The current region's id, or null when none is set. The screen compares each row against this
     * rather than reading a flag stored on the row: there is one current region, and
     * `RegionRepository.region` already owns it, so copying it into every [RegionItem] at load time
     * only created something that could go stale — which it did. The region genuinely can change while
     * this list is on screen: removing the current region re-resolves, and with auto-selection off that
     * raises the forced-choice picker over this screen, so the rider's pick happens elsewhere.
     *
     * Read from the domain [RegionRepository] directly rather than relayed through [RegionsRepository],
     * matching how the other region-observing view models do it (see
     * [org.onebusaway.android.ui.home.weather.WeatherViewModel]).
     */
    private val _currentRegionId = MutableStateFlow<Long?>(null)
    val currentRegionId: StateFlow<Long?> = _currentRegionId.asStateFlow()

    init {
        load()
        // A manual collector rather than stateIn(SharingStarted...): the other region VMs use this
        // idiom because a never-completing sharing collector leaks across JVM unit tests.
        viewModelScope.launch {
            regionRepository.region.collect { _currentRegionId.value = it?.id }
        }
    }

    /**
     * Loads the region list. Used for the initial load, retry-after-error (both with
     * [refresh] = false, which reads the local provider first), and the explicit refresh
     * action ([refresh] = true, which forces a server fetch).
     */
    fun load(refresh: Boolean = false) = load { repository.getRegions(refresh) }

    /**
     * Makes [item] the current region.
     *
     * @return true if this selection disabled automatic region selection
     */
    suspend fun selectRegion(item: RegionItem): Boolean = repository.selectRegion(item.id)

    /**
     * Removes a custom region the rider added ([RegionItem.custom]) and reloads the list so it
     * disappears. Reads from the local cache rather than forcing a server fetch — the removal is a
     * local write, and the directory has nothing to say about it.
     */
    suspend fun removeRegion(item: RegionItem) {
        repository.removeRegion(item.id)
        load()
    }
}
