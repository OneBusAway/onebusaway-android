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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.ObaRequestErrors

/**
 * The Android-flavored presentation edge of the arrivals load path: building the display
 * [ArrivalInfo]s (label strings and connectivity-aware error messages both need a [Context]).
 * Extracted as an interface so [DefaultArrivalsRepository] carries no [Context] and its
 * stale-fallback/CAS concurrency is JVM-unit-testable with a fake (#1909). [ArrivalInfo] itself
 * tolerates a null context (empty labels), so a fake can still build real display models — real
 * ETA/prediction math — on the JVM.
 */
interface ArrivalsDisplay {

    /** Builds the display models for [arrivals] against the server-clock [now] (see [convertArrivals]). */
    fun convert(
        arrivals: List<ArrivalData>,
        now: ServerTime,
        includeArrivalDepartureInStatusLabel: Boolean,
    ): List<ArrivalInfo>

    /** The user-facing message for a failed arrivals fetch (see [ObaRequestErrors]). */
    fun stopErrorMessage(code: Int): String
}

/** Production implementation over the app [Context] — where the arrivals load path touches Android. */
class DefaultArrivalsDisplay @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ArrivalsDisplay {

    override fun convert(
        arrivals: List<ArrivalData>,
        now: ServerTime,
        includeArrivalDepartureInStatusLabel: Boolean,
    ): List<ArrivalInfo> =
        convertArrivals(context, arrivals, now, includeArrivalDepartureInStatusLabel)

    override fun stopErrorMessage(code: Int): String =
        ObaRequestErrors.getStopErrorString(context, code)
}
