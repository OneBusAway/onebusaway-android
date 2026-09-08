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
package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.rememberLiveServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo

/** A flat departure uses the same ETA, clock correction, menus and live countdown as a route strip. */
@Composable
internal fun ChronologicalArrivalContent(
    arrival: ArrivalInfo,
    direction: String,
    stopLabel: String?,
    actions: ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    focus: EtaPillFocus?,
    modifier: Modifier = Modifier,
    // A spotlight anchor for the ETA pill, threaded down from the host (see ArrivalRowAnchors).
    etaModifier: Modifier = Modifier
) {
    val description: @Composable () -> Unit = {
        val decoration = strikeThroughIf(arrival.status == Status.CANCELED)
        if (direction.isNotBlank()) {
            Text(direction, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, textDecoration = decoration)
        }
        if (stopLabel != null) Text(stopLabel, style = MaterialTheme.typography.bodySmall)
        Text(arrival.statusText, style = MaterialTheme.typography.bodySmall, color = colorResource(arrival.deviationStatus.textColorRes))
    }
    val eta: @Composable () -> Unit = {
        val context = LocalContext.current
        val clock = remember(arrival, context) { arrival.arrivalClock(context) }
        val liveNow = rememberLiveServerTime(arrival.serverNow)
        val selected = focus?.tripId == arrival.tripId
        Box(etaModifier.height(IntrinsicSize.Min)) {
            EtaPillWithMenu(
                modifier = if (selected) Modifier.semantics { this.selected = true } else Modifier,
                trip = arrival,
                clock = clock,
                liveNow = liveNow,
                actions = actions,
                callbacks = callbacks,
                outline = focus?.takeIf { it.tripId == arrival.tripId }?.outline
            )
        }
    }
    // At large text sizes give the destination full width, and let the ETA sit below it.
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            description()
            eta()
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { description() }
            eta()
        }
    }
}
