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
package org.onebusaway.android.ui.tripresults

import android.net.Uri
import java.util.concurrent.TimeUnit
import org.onebusaway.android.time.WallTime

/**
 * Assembles the URI a [RentalLink.Synthesized] stands for (#2158) — the one Android-dependent step of
 * the link the app builds itself, kept out of `RentalPickups` so that file stays pure and JVM-tested.
 * Covered by the instrumented `RentalDeepLinksTest`, since `android.net.Uri` is stubbed off-device.
 */
object RentalDeepLinks {

    /**
     * [link] as the URI to hand `ACTION_VIEW`, stamped at [now].
     *
     * Built out of components, which is the point of holding them: `appendQueryParameter` escapes each
     * value, and a value carrying `&` or `=` (a vehicle id is the operator's string, not ours) becomes
     * one parameter rather than three. Interpolating the same template by hand escapes nothing, and the
     * mistake is invisible until the day an operator issues an id with a delimiter in it.
     *
     * [now] is a parameter, not a clock read here: it belongs to the moment the rider tapped, and the
     * caller is the only one that knows when that was (see `RentalVehicleUriTemplate.timestampParam` for
     * why a stamp from when the row was drawn is the wrong one). Epoch seconds is the unit the operator
     * publishes these in — the conversion is here, at the boundary, so `WallTime` stays unit-free.
     */
    fun vehicleUri(link: RentalLink.Synthesized, now: WallTime): Uri {
        val template = link.template
        val builder = Uri.Builder()
            .scheme(template.scheme)
            .authority(template.host)
            .appendQueryParameter(template.vehicleIdParam, link.vehicleId)
        template.timestampParam?.let {
            builder.appendQueryParameter(it, TimeUnit.MILLISECONDS.toSeconds(now.epochMs).toString())
        }
        return builder.build()
    }
}
