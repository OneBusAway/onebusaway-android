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
package org.onebusaway.android.map.compose

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.map.rental.RentalDetail
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalNotice
import org.onebusaway.android.map.rental.RentalPlace
import org.onebusaway.android.map.rental.rentalDetailOf
import org.onebusaway.android.ui.compose.components.RouteBadgeChip
import org.onebusaway.android.ui.compose.components.rentalVehicleRes
import org.onebusaway.android.ui.compose.unitsAreMetric
import org.onebusaway.android.ui.tripresults.RentalLink
import org.onebusaway.android.util.PreferenceUtils

// Info-window contents sit on a white bubble, so text uses fixed dark colors.
private val RentalPrimary = Color(0xDE000000)
private val RentalSecondary = Color(0x99000000)

/** The operator chip is a route badge at this scale — small enough to sit in an info-window line. */
private const val OPERATOR_CHIP_SCALE = 0.8f

/**
 * The rental marker's detail window (shared across map flavors): what this is, whose it is, how much
 * charge it has left, what a dock is holding, and anything stopping the rider from using it right now.
 * On Google it is the content of a `MarkerInfoWindowContent` (the SDK draws the white bubble); on
 * MapLibre it is hosted in a `ComposeView` wrapped in its own bubble.
 *
 * Replaces `BikeInfoWindow`, which could only say "Bikes / Spaces" — the shape OTP1 gave everything,
 * including the free-floating vehicles it disguised as one-bike stations (#2168).
 */
@Composable
fun RentalInfoWindow(place: RentalPlace) {
    val detail = rentalDetailOf(place)
    val context = LocalContext.current
    val metric = unitsAreMetric()
    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = rentalTitle(detail),
            color = RentalPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        detail.pickup?.operator?.let { operator ->
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RouteBadgeChip(
                    shortName = operator.displayName,
                    routeColor = operator.brandColor,
                    scale = OPERATOR_CHIP_SCALE
                )
                rentalChargeLine(detail, context, metric)?.let {
                    Text(text = it, color = RentalSecondary, fontSize = 12.sp)
                }
            }
        }
        if (detail.kind == RentalKind.STATION && (detail.vehiclesAvailableCount != null || detail.docksAvailableCount != null)) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                detail.vehiclesAvailableCount?.let {
                    RentalCount(stringResource(R.string.rental_window_vehicles_title), it)
                }
                detail.docksAvailableCount?.let {
                    RentalCount(stringResource(R.string.rental_window_docks_title), it)
                }
            }
        }
        for (notice in detail.notices) {
            Text(
                text = stringResource(rentalNoticeRes(notice)),
                color = RentalSecondary,
                fontSize = 12.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rentalActionLabel(detail),
                color = RentalSecondary,
                fontSize = 12.sp
            )
            Icon(
                painter = painterResource(R.drawable.ic_navigation_chevron_right),
                contentDescription = null,
                tint = RentalSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RentalCount(title: String, count: Int) {
    Column {
        Text(text = title, color = RentalSecondary, fontSize = 11.sp)
        Text(text = count.toString(), color = RentalPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The marker's accessibility text: everything the detail window says, in one line, so a rider using
 * TalkBack hears the occupancy and the charge from the marker itself rather than only seeing them in
 * the label painted beside it.
 *
 * Not a composable — the renderers set it as the marker's `snippet`, which is what the map SDKs read
 * for a marker's content description, and they hold a `Context` rather than a composition.
 */
fun rentalContentDescription(context: Context, place: RentalPlace): String {
    val detail = rentalDetailOf(place)
    val metric = PreferenceUtils.getUnitsAreMetricFromPreferences(context)
    val parts = buildList {
        detail.pickup?.operator?.displayName?.let {
            add(context.getString(R.string.trip_plan_rental_operator_description, it))
        }
        detail.vehiclesAvailableCount?.let {
            add("${context.getString(R.string.rental_window_vehicles_title)}: $it")
        }
        detail.docksAvailableCount?.let {
            add("${context.getString(R.string.rental_window_docks_title)}: $it")
        }
        rentalChargeLine(detail, context, metric)?.let { add(it) }
        detail.notices.forEach { add(context.getString(rentalNoticeRes(it))) }
    }
    val what = rentalTitle(context, detail)
    return if (parts.isEmpty()) what else context.getString(R.string.rental_marker_description, what, parts.joinToString(", "))
}

/**
 * The charge line — how far it can still go, and how full it is — or null when the feed said neither.
 * Range leads because it is the fact a rider acts on; the percentage qualifies it.
 */
private fun rentalChargeLine(detail: RentalDetail, context: Context, metric: Boolean): String? = listOfNotNull(
    detail.rangeMeters?.let {
        context.getString(
            R.string.trip_plan_rental_range,
            ConversionUtils.getFormattedDistance(it.toDouble(), context, metric)
        )
    },
    detail.fuelPercent?.let { context.getString(R.string.rental_window_battery, it) }
).joinToString(" · ").ifEmpty { null }

/**
 * What to head the window with: the place's own name when it has one (docks do), else what the feed
 * says the vehicle is, else the generic word for its shape. A free-floating vehicle publishes no
 * useful name — see `RentalPlaceAdapters` — so its kind *is* its title.
 */
private fun rentalTitle(context: Context, detail: RentalDetail): String = detail.title
    ?: detail.vehicle?.let { context.getString(rentalVehicleRes(it)) }
    ?: context.getString(
        if (detail.kind == RentalKind.STATION) R.string.rental_window_station else R.string.trip_plan_rental_bike
    )

@Composable
private fun rentalTitle(detail: RentalDetail): String = rentalTitle(LocalContext.current, detail)

private fun rentalNoticeRes(notice: RentalNotice): Int = when (notice) {
    RentalNotice.NOT_IN_SERVICE -> R.string.rental_window_not_in_service
    RentalNotice.NO_PICKUP_NOW -> R.string.rental_window_no_pickup
    RentalNotice.NO_DROPOFF_NOW -> R.string.rental_window_no_dropoff
}

/**
 * The window's tap affordance. When the feed named an operator the tap opens *them* and says so;
 * nothing here claims a reservation was made (#2138). The OTP1 path names no operator, so it keeps
 * the wording it has always had.
 */
@Composable
private fun rentalActionLabel(detail: RentalDetail): String {
    val operator = detail.pickup?.operator?.displayName
    val link = detail.pickup?.link
    return when {
        operator != null && link != null -> stringResource(
            if (link is RentalLink.Deep) {
                R.string.trip_plan_rental_open_in
            } else {
                R.string.trip_plan_rental_rent_with
            },
            operator
        )

        detail.kind == RentalKind.STATION -> stringResource(R.string.bike_station_title)
        else -> stringResource(R.string.floating_bike_title)
    }
}
