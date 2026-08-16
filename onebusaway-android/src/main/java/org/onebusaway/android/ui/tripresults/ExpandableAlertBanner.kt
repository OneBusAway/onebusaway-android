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
package org.onebusaway.android.ui.tripresults

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.AlertSeverity
import org.onebusaway.android.ui.compose.components.AlertSurface
import org.onebusaway.android.ui.icons.AppIcons

/**
 * The warning banner shape the trip planner draws over its directions: a glyph, a one-line [summary],
 * and — when there is more behind it — a chevron that expands [detail] in place, all on the app's
 * shared severity-tinted [AlertSurface].
 *
 * Two things wear it, and they must not drift apart: a leg's service alert ([TripAlertBanner]) and the
 * standing "these directions may be wrong" caution ([DirectionsCautionBanner], #2218). What they share
 * is not just the colour but the whole row — the metrics, the top alignment, the whole-card tap target,
 * and the accessibility contract that the chevron is what announces expand/collapse. That last one is
 * the reason this is a composable rather than a convention: an a11y fix applied to one copy of the row
 * and not the other regresses silently, on a screen nobody thought to re-check.
 *
 * Top-aligned, because the glyph and the chevron mark the banner — on an expanded one they belong
 * beside its first line rather than floating down beside the middle of the body.
 *
 * [expanded] and [onToggle] are hoisted: each caller keys its own expansion the way its content
 * demands (a service alert on the alert's identity, so a re-plan returning the same alert keeps it
 * open). A banner with nothing to expand passes a null [onToggle] and gets neither chevron nor tap
 * target, rather than an affordance that reveals nothing.
 */
@Composable
internal fun ExpandableAlertBanner(
    severity: AlertSeverity,
    summary: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    @StringRes showDetailsRes: Int,
    @StringRes hideDetailsRes: Int,
    modifier: Modifier = Modifier,
    detail: @Composable ColumnScope.() -> Unit
) {
    AlertSurface(severity = severity, modifier = modifier, onClick = onToggle) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.baseline_warning_24),
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
                if (expanded) {
                    detail()
                }
            }
            if (onToggle != null) {
                Icon(
                    imageVector = if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) hideDetailsRes else showDetailsRes
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
