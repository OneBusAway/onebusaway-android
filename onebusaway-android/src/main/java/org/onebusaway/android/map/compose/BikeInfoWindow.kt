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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.opentripplanner.routing.bike_rental.BikeRentalStation

// Info-window contents sit on a white bubble, so text uses fixed dark colors.
private val BikePrimary = Color(0xDE000000)
private val BikeSecondary = Color(0x99000000)

/**
 * The bike-station marker info-window content (shared across map flavors): a floating bike shows just
 * its name; a docking station shows its name plus available bikes and open spaces. On Google it is the
 * content of a `MarkerInfoWindowContent` (the SDK draws the white bubble); on MapLibre it is hosted in
 * a `ComposeView` wrapped in its own bubble.
 */
@Composable
fun BikeInfoWindow(station: BikeRentalStation) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = station.name,
            color = BikePrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        if (station.isFloatingBike) {
            Text(
                text = stringResource(R.string.floating_bike_title),
                color = BikeSecondary,
                fontSize = 12.sp,
            )
        } else {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BikeCount(stringResource(R.string.bike_info_window_bikes_title), station.bikesAvailable)
                BikeCount(stringResource(R.string.bike_info_window_spaces_title), station.spacesAvailable)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.bike_station_title),
                    color = BikeSecondary,
                    fontSize = 12.sp,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_navigation_chevron_right),
                    contentDescription = null,
                    tint = BikeSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BikeCount(title: String, count: Int) {
    Column {
        Text(text = title, color = BikeSecondary, fontSize = 11.sp)
        Text(text = count.toString(), color = BikePrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
