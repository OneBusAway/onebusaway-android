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

import android.content.Context
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.ArrivalInfoUtils

/**
 * Builds the display [ArrivalInfo]s from [arrivals]: drops past arrivals unless the user opted in, and
 * sorts by ETA. Callers feed it the [ArrivalData] the api arrivals fetch
 * ([org.onebusaway.android.api.data.StopArrivals.arrivals]) produces.
 *
 * Favorite state is not baked in here — it's a live overlay the arrivals UI applies from the reactive
 * starred-route set (so a star toggle from any surface re-flags rows without a re-fetch), keyed by
 * `ArrivalInfo.routeId`.
 */
fun convertArrivals(
    context: Context,
    arrivals: List<ArrivalData>,
    ms: ServerTime,
    includeArrivalDepartureInStatusLabel: Boolean,
): List<ArrivalInfo> {
    val showNegativeArrivals = PreferencesEntryPoint.get(context)
        .getBoolean(R.string.preference_key_show_negative_arrivals, true)
    return arrivals
        .map { ArrivalInfo(context, it, ms, includeArrivalDepartureInStatusLabel) }
        .filter { it.eta >= 0 || showNegativeArrivals }
        .sortedWith(ArrivalInfoUtils.InfoComparator())
}
