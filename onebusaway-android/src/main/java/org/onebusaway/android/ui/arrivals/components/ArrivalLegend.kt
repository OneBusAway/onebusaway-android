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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.util.ScheduleDeviation
import org.onebusaway.android.util.ScheduleDeviation.Status

/**
 * What an ETA pill's colour and its corner marks mean — the app's single definition of the arrivals
 * legend, shown both by the help menu's Legend dialog and inline in the scripted tutorial's "what the
 * colours mean" step (#2164).
 *
 * One composable rather than one per host, because a legend that has drifted from what the app paints
 * is worse than none. The swatches come from [ScheduleDeviation.Status] rather than being spelled out
 * as colour resources, so this cannot document a palette the app no longer paints (#2043), and the
 * glyph rows reuse the very indicators the pills draw. The row *labels* still have to be kept truthful
 * by hand: they name both the hue and the ±1.5 minute on-time band.
 *
 * [compact] tightens the row spacing and drops the canceled row for the tutorial caption, which has to
 * fit a card rather than a scrollable dialog.
 */
@Composable
fun ArrivalLegend(modifier: Modifier = Modifier, compact: Boolean = false) {
    val rowPadding = if (compact) 5.dp else 12.dp
    Column(modifier) {
        LegendRow(Status.ON_TIME, predicted = true, label = R.string.main_help_legend_ontime, rowPadding)
        LegendRow(Status.EARLY, predicted = true, label = R.string.main_help_legend_early, rowPadding)
        LegendRow(Status.DELAYED, predicted = true, label = R.string.main_help_legend_late, rowPadding)
        LegendRow(Status.SCHEDULED, predicted = false, label = R.string.main_help_legend_scheduled, rowPadding)
        if (!compact) {
            LegendRow(
                Status.SCHEDULED,
                predicted = false,
                label = R.string.main_help_legend_canceled,
                rowPadding = rowPadding,
                canceled = true
            )
        }
        // The two pill glyphs. Both mean "this arrival is real-time"; the pin additionally means the
        // vehicle is drawn on the map right now, so tapping the pill flies the camera to it.
        GlyphRow(R.string.main_help_legend_on_map, rowPadding) {
            OnMapIndicator(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize())
        }
        GlyphRow(R.string.main_help_legend_realtime, rowPadding) {
            RealtimeIndicator(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize())
        }
    }
}

/** One colour swatch. Takes the [Status] rather than a colour so the tier choice lives in one place. */
@Composable
private fun LegendRow(
    status: Status,
    predicted: Boolean,
    @StringRes label: Int,
    rowPadding: Dp,
    canceled: Boolean = false
) {
    LegendLine(label, rowPadding) {
        // The pill paints white text on this, so it takes the on-fill tier — as [EtaPill] always does.
        EtaPill(
            eta = 5L,
            color = colorResource(status.fillColorRes),
            predicted = predicted,
            canceled = canceled
        )
    }
}

/** One glyph row, sized to the same footprint the pills give their indicators. */
@Composable
private fun GlyphRow(
    @StringRes label: Int,
    rowPadding: Dp,
    glyph: @Composable () -> Unit
) {
    LegendLine(label, rowPadding) {
        // Centred in a pill-width box so the glyph column lines up under the swatches above it.
        Box(Modifier.width(LEGEND_SWATCH_WIDTH), contentAlignment = Alignment.Center) {
            Box(Modifier.size(LEGEND_GLYPH_SIZE)) { glyph() }
        }
    }
}

@Composable
private fun LegendLine(
    @StringRes label: Int,
    rowPadding: Dp,
    leading: @Composable () -> Unit
) {
    Row(
        // Height pinned to the row's own content. [EtaPill] fills its parent's height on purpose — the
        // ETA strip stretches every pill to a common height — so in a parent that offers unbounded
        // height (the tutorial's caption card) an unconstrained row let the first swatch swallow the
        // whole card and squeezed the rest of the legend out. The dialog only ever looked right because
        // AlertDialog happens to constrain its text slot.
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = rowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Roughly an [EtaPill]'s width, so the glyph rows align with the swatch rows above them. */
private val LEGEND_SWATCH_WIDTH = 56.dp

/** The glyph footprint on a pill. */
private val LEGEND_GLYPH_SIZE = 14.dp
