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
package org.onebusaway.android.ui.home.directions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.EtaDurationText
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.tripplan.pinned.PinnedLabel
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripCardState
import org.onebusaway.android.ui.tripresults.ModeSymbolSummary

/**
 * The way back to a parked trip plan (#2053): a floating card naming where the pinned trip goes, drawn
 * over the map whenever the rider is *not* in directions. Tapping it resumes the trip; the ✕ throws the
 * pin away.
 *
 * Shaped like [org.onebusaway.android.ui.home.map.FocusBanner] — the same rounded container, surface
 * colour and elevation — because the two stack in one column below the top chrome and should read as
 * one family rather than as two unrelated floating things.
 *
 * The trip is described in exactly the language the rider chose it in: [ModeSymbolSummary] is the option
 * card's own summary line, and the duration is the same [EtaDurationText]. Nothing here is a stored
 * label that could drift from the snapshot it claims to describe.
 */
@Composable
fun PinnedTripCard(
    state: PinnedTripCardState,
    onResume: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val destination = state.destination.resolve()
    Surface(
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.trip_plan_pinned_resume),
            onClick = onResume
        ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The identity mark, and the only place the card says "pinned" in a glyph. Decorative: the
            // heading below already names the card in words, and TalkBack shouldn't hear it twice.
            Icon(
                painter = painterResource(R.drawable.ic_pin_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
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
                    // Weighted so a long trip's symbol line yields rather than pushing the duration off
                    // the card: the summary wraps at its own width and would otherwise take whatever it
                    // asked for. `fill = false` keeps a short trip's symbols beside its duration rather
                    // than stranding the number at the far edge.
                    ModeSymbolSummary(state.symbols, Modifier.weight(1f, fill = false))
                    EtaDurationText(minutes = state.durationMinutes)
                }
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.trip_plan_unpin_content_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The label as text, resolving the fixed-kind resource the rider's own naming didn't supply. */
@Composable
private fun PinnedLabel.resolve(): String = when (this) {
    is PinnedLabel.Text -> value
    is PinnedLabel.Resource -> stringResource(id)
}
