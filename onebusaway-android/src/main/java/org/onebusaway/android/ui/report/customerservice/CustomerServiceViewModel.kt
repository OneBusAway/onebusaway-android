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
package org.onebusaway.android.ui.report.customerservice

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.onebusaway.android.ui.compose.ListLoadingViewModel

/** ViewModel for the customer-service contact list. */
@HiltViewModel
class CustomerServiceViewModel @Inject constructor(private val repository: CustomerServiceRepository) :
    ListLoadingViewModel<AgencyContact>() {

    init {
        load()
    }

    /** Loads (or retries loading) the agency contact list. */
    fun load() = load { repository.getAgencies() }
}
