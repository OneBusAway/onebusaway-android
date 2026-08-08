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
package org.onebusaway.android.ui.compose.components

import android.content.Context
import androidx.annotation.StringRes
import org.onebusaway.android.R
import org.onebusaway.android.ui.tripresults.RentalLink
import org.onebusaway.android.ui.tripresults.RentalVehicleKind
import org.onebusaway.android.util.ExternalIntents

/**
 * The two things every rental surface does with a rental: name the vehicle, and follow the rider's
 * tap to the operator.
 *
 * Shared by the directions rows (#2150) and the rental map layer (#2168) so a bike named "E-bike" in
 * an itinerary is named "E-bike" on the map, and a tap on either goes to the same place in the same
 * order.
 */

/** What to call the rented vehicle — total over [RentalVehicleKind], so a new kind needs its word. */
@StringRes
fun rentalVehicleRes(kind: RentalVehicleKind): Int = when (kind) {
    RentalVehicleKind.BIKE -> R.string.trip_plan_rental_bike
    RentalVehicleKind.EBIKE -> R.string.trip_plan_rental_ebike
    RentalVehicleKind.CARGO_BIKE -> R.string.trip_plan_rental_cargo_bike
    RentalVehicleKind.ELECTRIC_CARGO_BIKE -> R.string.trip_plan_rental_electric_cargo_bike
    RentalVehicleKind.SCOOTER -> R.string.trip_plan_rental_scooter
    RentalVehicleKind.ESCOOTER -> R.string.trip_plan_rental_escooter
    RentalVehicleKind.MOPED -> R.string.trip_plan_rental_moped
    RentalVehicleKind.CAR -> R.string.trip_plan_rental_car
}

/**
 * Follows a rental action, falling back once when a deep link's scheme means nothing on this device
 * (see [RentalLink.Deep.mayNeedTheirApp]). The fallback is never itself a link that could fail the
 * same way, so the recursion is one level deep by construction.
 */
fun openRental(context: Context, link: RentalLink, fallback: RentalLink?) {
    when (link) {
        is RentalLink.Deep -> if (!ExternalIntents.openFeedUri(context, link.uri)) {
            fallback?.let { openRental(context, it, fallback = null) }
        }

        is RentalLink.OperatorApp -> ExternalIntents.openAppOrStoreListing(context, link.packageName)
        is RentalLink.Web -> ExternalIntents.goToUrl(context, link.url)
    }
}
