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
package org.onebusaway.android.ui.home.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.EtaDurationText
import org.onebusaway.android.ui.tripplan.pinned.PinnedLabel
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripCardState
import org.onebusaway.android.ui.tripresults.ModeSymbolSummary

/**
 * The rider's parked trip plan, as a button on the map (#2229): where the trip goes, the legs it rides,
 * how long it takes — and a tap that takes them back into it.
 *
 * This is the same summary the map pin's info window used to carry (#2053), moved off the map because a
 * marker was the wrong home for it. A pin has to stand *somewhere*, and the only place a parked trip has
 * is its own head — which is wherever the plan happens to start, not anywhere the rider is looking. So
 * the one handle on a pinned trip sat off screen for most of the exploring the pin exists to support, and
 * reaching it meant hunting the trip's origin down and then landing a tap on a marker competing with
 * every stop around it. As chrome it is simply always there, at a fixed place, at full tap size.
 *
 * It describes the trip in exactly the language the picker used: [ModeSymbolSummary] *is* the option
 * card's own summary line and the duration *is* the same [EtaDurationText], so nothing here is a second
 * description that could drift from the plan it claims to describe.
 *
 * Carries the resume action only. Unpinning deliberately stays where the trip is being *read* rather
 * than merely remembered — a long press on its option card — which is the rule #2053 set and which the
 * info window's inability to hold a second target was never the whole reason for.
 */
@Composable
fun PinnedTripFab(
    state: PinnedTripCardState,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val destination = when (val label = state.destination) {
        is PinnedLabel.Text -> label.value
        is PinnedLabel.Resource -> stringResource(label.id)
    }
    val resumeLabel = stringResource(R.string.trip_plan_pinned_resume)
    ExtendedFloatingActionButton(
        onClick = onResume,
        // Capped so a long destination name ellipsizes instead of growing the button across the map —
        // the summary beside it already wraps at its own width, and this keeps the two in the same
        // column rather than letting the title alone decide how wide the button is.
        modifier = modifier.widthIn(max = PINNED_TRIP_FAB_MAX_WIDTH)
    ) {
        Icon(
            painterResource(R.drawable.ic_pin_filled),
            // The button's own text says what it is; a description here would only repeat it.
            contentDescription = null,
            modifier = Modifier.size(PINNED_TRIP_FAB_GLYPH)
        )
        // The gap M3's own icon+text extended FAB puts between the two; this overload takes the whole
        // row, so it has to be stated.
        Spacer(Modifier.width(12.dp))
        Column(
            // "Resume this trip" is what the tap does, not what the button is, so it is announced as the
            // click's label rather than drawn as a third line. Set on a descendant of the FAB, because
            // that is the direction the merge runs: the button contributes the action, the child the
            // label (an action property merges as parent-label-or-child's, parent-action-or-child's).
            modifier = Modifier.semantics { onClick(label = resumeLabel, action = null) },
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
                // The elastic half of the line, so the duration — short, fixed, and the one number the
                // rider is checking — is measured first and always gets its natural width. The summary
                // packs its symbols unbounded by design (see SymbolFlow), so without this a trip with a
                // wide multi-route badge would take the whole line and leave "32 min" nothing.
                ModeSymbolSummary(state.symbols, modifier = Modifier.weight(1f, fill = false))
                EtaDurationText(minutes = state.durationMinutes)
            }
        }
    }
}

/**
 * How wide the button may grow. Wide enough for a destination name plus the mode summary beside it,
 * narrow enough that it can't reach the map chrome on the far edge on a small phone — the layout in
 * [MapChrome] reserves that column, so this is about the button looking like a button rather than a bar.
 */
private val PINNED_TRIP_FAB_MAX_WIDTH = 260.dp

/** The pin glyph, at the size a FAB icon takes. */
private val PINNED_TRIP_FAB_GLYPH = 24.dp
