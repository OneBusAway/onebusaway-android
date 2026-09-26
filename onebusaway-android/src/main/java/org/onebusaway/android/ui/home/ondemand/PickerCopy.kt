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
package org.onebusaway.android.ui.home.ondemand

import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.zoneCount

/** The picker row's area text (spec §2.8): named areas joined with ", ", else the zone count plural. */
fun pickerAreasText(service: OnDemandService): Any {
    val names = service.areas.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
    return if (names.isEmpty()) zoneCount(service.areas.size) else names.joinToString(", ")
}
