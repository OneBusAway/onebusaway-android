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

import org.onebusaway.android.util.GeoPoint

/**
 * The target of a "send feedback / report a problem" launch, derived by [HomeViewModel.reportTarget]:
 * the focused stop, else the last-known location, else nothing. Choosing the variant is VM logic; the
 * host just opens `ReportActivity` for whichever it gets.
 */
sealed interface ReportTarget {
    /**
     * Report against the currently focused stop. [point] is the stop's location, resolved by the VM:
     * the report flow opens a map on it, and a focus revealed by id alone has none until its arrivals
     * land — so this variant exists only once the location is known, and the host needn't re-check.
     */
    data class Stop(val stop: FocusedStop, val point: GeoPoint) : ReportTarget

    /** No focused stop; report against the last-known device location. */
    data class Location(val point: GeoPoint) : ReportTarget

    /** No focused stop and no known location; open the generic report screen. */
    object Generic : ReportTarget
}
