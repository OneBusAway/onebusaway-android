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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.AlertSeverity

/**
 * The caution at the head of every itinerary's step-by-step detail (#2218): the walking legs below it
 * are planned from open map data that is often missing crosswalks, signals and sidewalks, so a drawn
 * route is not a promise that it can be safely walked.
 *
 * It sits under the option picker rather than on the option cards because it is true of every option,
 * not a property of the one being compared — and above the timeline because the steps are what it
 * qualifies. It scrolls away with the rest of the header, as the picker does: a bar pinned over a
 * bottom sheet would cost real estate the sheet hasn't got.
 *
 * Collapsed it is the one line; tapping expands the same explanation the one-time
 * [DirectionsSafetyNotice][org.onebusaway.android.ui.home.directions.DirectionsSafetyNotice] gave,
 * which is the only way back to that text once acknowledged. Both read the one
 * `directions_safety_body` string, so the two can't come to say different things. The expansion is
 * local state, so this needs nothing from the host.
 */
@Composable
internal fun DirectionsCautionBanner(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExpandableAlertBanner(
        severity = AlertSeverity.WARNING,
        summary = stringResource(R.string.directions_caution_banner),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        showDetailsRes = R.string.directions_caution_show_details,
        hideDetailsRes = R.string.directions_caution_hide_details,
        modifier = modifier
    ) {
        Text(
            text = stringResource(
                R.string.directions_safety_body,
                stringResource(R.string.app_name)
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
