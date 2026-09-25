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
package org.onebusaway.android.ui.arrivals

import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind

/** One on-demand service named by the stop's pointer field, as the arrivals card shows it. */
data class OnDemandServiceItem(
    val id: String,
    val name: String,
    val kind: OnDemandServiceKind,
    /** The pickup booking rule's phone number, in rule order, or null when none publishes one. */
    val phoneNumber: String?
)

internal fun OnDemandService.toItem(): OnDemandServiceItem = OnDemandServiceItem(
    id = id,
    name = name,
    kind = kind,
    phoneNumber = rules.firstNotNullOfOrNull { pickupBookingRule(it)?.phoneNumber }
)

/**
 * Resolves a stop's pointer [ids] to card items, in pointer order, through [cache] (services are
 * static data; a 60-second poll must not refetch them). A service that fails to load is simply left
 * off the card — the arrivals themselves are the screen's job, and the pointer will be tried again on
 * the next load.
 */
internal suspend fun loadOnDemandItems(
    ids: List<String>,
    cache: MutableMap<String, OnDemandServiceItem>,
    fetch: suspend (String) -> OnDemandResult<OnDemandService>
): List<OnDemandServiceItem> = ids.mapNotNull { id ->
    cache[id] ?: (fetch(id) as? OnDemandResult.Loaded)?.value?.toItem()?.also { cache[id] = it }
}
