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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.EtaDurationText
import org.onebusaway.android.ui.tripplan.pinned.PinnedLabel
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripCardState
import org.onebusaway.android.ui.tripresults.ModeSymbolSummary

/**
 * The parked trip's marker info-window content (shared across map flavors): where the trip goes, the
 * legs it rides, and how long it takes — the same summary the rider chose the option from.
 *
 * It describes the trip in exactly the language the picker used: [ModeSymbolSummary] *is* the option
 * card's own summary line and the duration *is* the same [EtaDurationText], so nothing here is a second
 * description that could drift from the plan it claims to describe.
 *
 * Carries no actions of its own. Google draws an info window as a static bitmap of a detached view, so
 * nothing inside one can be independently tappable on that flavor — the whole window is one target, and
 * tapping it resumes the trip (`onPinnedTripInfoWindowClick`). Unpinning therefore lives where the trip
 * is being read rather than merely remembered: a long press on its option card.
 */
@Composable
fun PinnedTripInfoWindow(state: PinnedTripCardState) {
    val destination = when (val label = state.destination) {
        is PinnedLabel.Text -> label.value
        is PinnedLabel.Resource -> stringResource(label.id)
    }
    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.trip_plan_pinned_trip_title, destination),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeSymbolSummary(state.symbols)
            EtaDurationText(minutes = state.durationMinutes)
        }
        Text(
            text = stringResource(R.string.trip_plan_pinned_resume),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
