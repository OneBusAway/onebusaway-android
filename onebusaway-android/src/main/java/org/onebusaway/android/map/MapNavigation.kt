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
package org.onebusaway.android.map

import android.content.Context
import org.onebusaway.android.R
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalPlace
import org.onebusaway.android.map.rental.rentalDetailOf
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.ui.compose.components.openRental
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.RegionUtils

/**
 * Navigation launched from a map info-window tap. Flavor-neutral (no map-SDK types), so both the
 * Google host (via [org.onebusaway.android.map.compose.ObaMapCallbacks]) and the maplibre
 * host (via its info-window-click listener) route here instead of each carrying the policy — and the
 * renderers stay pure rendering. Lifted from the old `ObaMapContent` private functions.
 */
object MapNavigation {

    /** Opens TripDetails for the tapped vehicle, scoped to the focused stop when there is one. */
    fun openVehicleTripDetails(context: Context, status: ObaTripStatus, focusedStopId: String?) {
        val tripId = status.activeTripId ?: return
        val builder = TripDetailsLauncher.Builder(context, tripId)
            .setScrollMode(TripDetailsLauncher.SCROLL_MODE_VEHICLE)
        focusedStopId?.let { builder.setStopId(it) }
        builder.start()
    }

    /**
     * The rental info-window tap: opens the operator the feed named, in the order the directions rows
     * offer them (their deep link to this vehicle, then their app, then their site) — one definition of
     * "where a rental tap goes", shared via [openRental]. Nothing here claims a reservation was made
     * (#2138).
     *
     * Falls back to the pre-#2168 behaviour when the feed named no operator, which is the whole OTP1
     * path: a deep link hard-coded to the Tampa region's Hopr app, preserved verbatim from the legacy
     * `BikeStationOverlay.onInfoWindowClick`. Everywhere else on that path there is genuinely nowhere
     * to send the rider, and the tap does nothing, exactly as before.
     */
    fun openRentalLink(context: Context, place: RentalPlace) {
        AnalyticsEntryPoint.get(context).reportUiEvent(
            PlausibleAnalytics.REPORT_BIKE_EVENT_URL,
            context.getString(
                if (place.kind == RentalKind.STATION) {
                    R.string.analytics_label_bike_station_balloon_clicked
                } else {
                    R.string.analytics_label_floating_bike_balloon_clicked
                }
            ),
            null
        )
        val pickup = rentalDetailOf(place).pickup
        val link = pickup?.link
        if (link != null) {
            openRental(context, link, pickup.fallback)
            return
        }
        val region = RegionEntryPoint.get(context).currentRegion() ?: return
        if (region.id == RegionUtils.TAMPA_REGION_ID.toLong()) {
            ExternalIntents.launchTampaHoprApp(context)
        }
    }
}
