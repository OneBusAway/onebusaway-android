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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * The standing caution at the head of every itinerary's step-by-step detail (#2218): the walking legs
 * below it are planned from open map data that is often missing crosswalks, signals and sidewalks, so
 * a drawn route is not a promise that it can be safely walked.
 *
 * It sits under the option picker rather than on the option cards because it is true of every option,
 * not a property of the one being compared — and above the timeline because the steps are what it
 * qualifies.
 *
 * Collapsed it is the one line; tapping expands the same explanation the one-time
 * [DirectionsSafetyNotice][org.onebusaway.android.ui.home.directions.DirectionsSafetyNotice] gave,
 * which is the only way back to that text once acknowledged. The expansion is local state, so this
 * needs nothing from the host.
 *
 * Drawn on the shared [AlertSurface] so it carries the app's one warning tint rather than a second
 * copy of that colour table.
 */
@Composable
internal fun DirectionsCautionBanner(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    AlertSurface(
        severity = AlertSeverity.WARNING,
        modifier = modifier,
        onClick = { expanded = !expanded }
    ) {
        // Top-aligned for the same reason the service-alert banner is: the glyph and the chevron mark
        // the caution, so on an expanded one they belong beside its first line.
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.baseline_warning_24),
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.directions_caution_banner),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (expanded) {
                    Text(
                        text = stringResource(
                            R.string.directions_safety_body,
                            stringResource(R.string.app_name)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.directions_safety_body_secondary),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Icon(
                imageVector = if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.directions_caution_hide_details
                    } else {
                        R.string.directions_caution_show_details
                    }
                ),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
