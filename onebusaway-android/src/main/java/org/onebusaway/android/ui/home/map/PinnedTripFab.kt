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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
 * The symbols and the duration are the whole of it — one line, no "Pinned trip to X" heading. The pin
 * glyph already says which button this is, and a destination name is the widest thing a trip can carry:
 * spelling it out doubled the button's height and let one long place name set its width. The name is not
 * lost, it just isn't drawn — it is the glyph's content description, so the button still announces itself
 * as the trip it is rather than as a bare pair of numbers.
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
    // Naming the trip is a getString + String.format, and the pin behind it changes only when the rider
    // pins something, so the work is held rather than repeated on every recomposition of the chrome.
    val resources = LocalResources.current
    val name = remember(resources, state.destination) {
        val destination = when (val label = state.destination) {
            is PinnedLabel.Text -> label.value
            is PinnedLabel.Resource -> resources.getString(label.id)
        }
        resources.getString(R.string.trip_plan_pinned_trip_title, destination)
    }
    val resumeLabel = stringResource(R.string.trip_plan_pinned_resume)
    ExtendedFloatingActionButton(
        onClick = onResume,
        // No width of its own: the host sizes this button against the screen, and a cap here would
        // quietly win on any display wide enough to reach it.
        modifier = modifier,
        // The whole row rather than M3's icon + text slots: those lead with the icon and leave the label
        // where it falls, which reads as a half-empty button once the host makes it wider than its
        // contents. Here the glyph travels with what it labels and the group sits in the middle.
        content = {
            Row(
                // "Resume this trip" is what the tap does, not what the button is, so it is announced as
                // the click's label rather than drawn as a line of its own. Set on a descendant of the
                // FAB, because that is the direction the merge runs: the button contributes the action,
                // the child the label (an action property merges as parent-label-or-child's,
                // parent-action-or-child's).
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { onClick(label = resumeLabel, action = null) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP, Alignment.CenterHorizontally)
            ) {
                Icon(
                    painterResource(R.drawable.ic_pin_filled),
                    // Where the trip goes, which the button no longer draws. It rides the glyph so a
                    // screen reader announces the parked trip by name and not merely as a route and a
                    // duration.
                    contentDescription = name
                )
                // Elastic, so the duration — short, fixed, and the one number the rider is checking — is
                // measured first and always gets its natural width. The summary packs its symbols
                // unbounded by design (see SymbolFlow), so without this a trip with a wide multi-route
                // badge would take the whole line and leave "32 min" nothing. `fill = false` so it still
                // gives its slack back to the arrangement, which is what keeps the group centred.
                ModeSymbolSummary(state.symbols, modifier = Modifier.weight(1f, fill = false))
                EtaDurationText(minutes = state.durationMinutes)
            }
        }
    )
}

/** Air between the glyph, the route summary and the duration — one gap, so the group reads as one thing. */
private val CONTENT_GAP = 8.dp
