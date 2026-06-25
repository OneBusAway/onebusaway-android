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

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.onebusaway.android.ui.compose.ListLoadingViewModel

/** ViewModel for the region picker screen. */
@HiltViewModel
class RegionsViewModel @Inject constructor(private val repository: RegionsRepository) :
    ListLoadingViewModel<RegionItem>() {

    init {
        load()
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
}
