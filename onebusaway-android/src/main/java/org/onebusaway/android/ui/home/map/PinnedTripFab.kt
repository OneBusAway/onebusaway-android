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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.tripplan.pinned.PinnedLabel
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripCardState
import org.onebusaway.android.ui.tripresults.ModeSymbolSummary

/**
 * The rider's parked trip plan, as a button on the map (#2229): the legs it rides, and a tap that takes
 * the rider back into it.
 *
 * This is the same summary the map pin's info window used to carry (#2053), moved off the map because a
 * marker was the wrong home for it. A pin has to stand *somewhere*, and the only place a parked trip has
 * is its own head — which is wherever the plan happens to start, not anywhere the rider is looking. So
 * the one handle on a pinned trip sat off screen for most of the exploring the pin exists to support, and
 * reaching it meant hunting the trip's origin down and then landing a tap on a marker competing with
 * every stop around it. As chrome it is simply always there, at a fixed place, at full tap size.
 *
 * It describes the trip in exactly the language the picker used: [ModeSymbolSummary] *is* the option
 * card's own summary line, so nothing here is a second description that could drift from the plan it
 * claims to describe.
 *
 * The symbols are the whole of it. A "Pinned trip to X" heading came out because the pin glyph already
 * says which button this is and a destination name is the widest thing a trip can carry — spelling it out
 * doubled the button's height and let one long place name set its width. The trip's duration came out
 * after it: it is a fact about the plan, not about the way back into it, and it is on screen again the
 * moment the rider taps through. The destination is not lost, it just isn't drawn — it is the glyph's
 * content description, so the button still announces itself as the trip it is.
 *
 * Two actions, side by side: the pill resumes the trip, and the ✕ lets it go. Unpinning is confirmed
 * because the pin is the only thing holding that plan — nothing else remembers it — so a mis-tap on a
 * small glyph would spend the very journey pinning exists to protect. (Unpinning used to live only on
 * the option card, back when the info window this replaced could not hold a second tap target; being
 * able to let go of the trip from the same place it is offered is worth more than that rule.)
 */
@Composable
fun PinnedTripFab(
    state: PinnedTripCardState,
    onResume: () -> Unit,
    onUnpin: () -> Unit,
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
    var confirmingUnpin by remember { mutableStateOf(false) }
    // A Surface wearing the extended FAB's shape rather than a FloatingActionButton, for the same reason
    // the rental control is one: this pill holds *two* actions, and a FAB is a single click target that
    // merges everything inside it — the ✕ would have been swallowed into the resume button, unreachable
    // to a screen reader and fighting it for the gesture. Two plain targets side by side instead, each
    // with its own label. (It also drops the extended FAB's fixed content padding, which walled the
    // contents off both ends of a button the host had already given room to.)
    Surface(
        // No width of its own: the host sizes this button against the screen.
        modifier = modifier,
        shape = FloatingActionButtonDefaults.extendedFabShape,
        color = FloatingActionButtonDefaults.containerColor,
        contentColor = contentColorFor(FloatingActionButtonDefaults.containerColor),
        shadowElevation = PINNED_TRIP_FAB_ELEVATION
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    // "Resume this trip" is what the tap does, not what the button is, so it is the
                    // click's own label rather than a line drawn inside it. Merged so the region
                    // announces as one button carrying the trip's name, not as a glyph and a row of
                    // route badges a screen reader has to walk separately.
                    .clickable(onClickLabel = resumeLabel, onClick = onResume)
                    .semantics(mergeDescendants = true) {}
                    .padding(vertical = CONTENT_GAP),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP, Alignment.CenterHorizontally)
            ) {
                Icon(
                    painterResource(R.drawable.ic_pin_filled),
                    // Where the trip goes, which the button no longer draws. It rides the glyph so a
                    // screen reader announces the parked trip by name and not merely as a string of
                    // routes.
                    contentDescription = name
                )
                // Wrapped against this button rather than against the option card the summary was drawn
                // for: that card's line is 176dp, so on a button the width of most of the screen the
                // symbols packed themselves into a card's worth of it and left the rest empty. Null puts
                // the break wherever this button actually runs out.
                ModeSymbolSummary(state.symbols, wrapAt = null)
            }
            IconButton(onClick = { confirmingUnpin = true }) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.trip_plan_unpin),
                    modifier = Modifier.size(UNPIN_GLYPH)
                )
            }
        }
    }
    // Confirmed rather than immediate: the pin is the only thing holding that plan, so a mis-tap on a
    // small glyph would spend the whole journey the rider parked — the very cost pinning exists to
    // avoid. The resume half needs no such guard; it costs a tap to undo.
    if (confirmingUnpin) {
        AlertDialog(
            onDismissRequest = { confirmingUnpin = false },
            title = { Text(stringResource(R.string.trip_plan_pinned_unpin_title)) },
            text = { Text(stringResource(R.string.trip_plan_pinned_unpin_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUnpin = false
                        onUnpin()
                    }
                ) { Text(stringResource(R.string.trip_plan_pinned_unpin_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnpin = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Air between the glyph and the route summary it labels, so the two read as one thing. */
private val CONTENT_GAP = 8.dp

/** The ✕, a touch smaller than a FAB glyph: it is the secondary of the two actions on this pill. */
private val UNPIN_GLYPH = 20.dp

/** Matches the elevation a FloatingActionButton rests at, since this pill stands in for one. */
private val PINNED_TRIP_FAB_ELEVATION = 6.dp
