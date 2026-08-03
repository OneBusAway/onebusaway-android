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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.AlertSurface
import org.onebusaway.android.ui.compose.components.linkifyUrls
import org.onebusaway.android.ui.icons.AppIcons

/**
 * One service alert over the trip planner's directions (#2143), drawn on the app's shared
 * severity-tinted [AlertSurface] so a suspension reads the same here as it does on the arrivals
 * screen.
 *
 * Collapsed it is the feed's headline alone — enough, under the leg's own header, to know that ride is
 * in doubt. Tapping expands the full description in place, plus the agency's own
 * "more details" page when the alert names one. Expanding inline rather than opening a dialog keeps
 * the alert beside the itinerary it contradicts, which is the comparison the rider is actually making;
 * it also means this composable needs nothing from the host (no Activity, no callback threading)
 * beyond the item itself.
 *
 * An alert with nothing behind the headline takes no tap target and draws no chevron, rather than
 * offering an expansion that reveals nothing.
 */
@Composable
internal fun TripAlertBanner(alert: TripAlertItem, modifier: Modifier = Modifier) {
    // Keyed on the alert's content identity, so a re-plan that returns the same alert keeps it open and
    // one that returns a different alert doesn't inherit the old one's expansion.
    var expanded by rememberSaveable(alert.contentId) { mutableStateOf(false) }
    AlertSurface(
        severity = alert.severity,
        modifier = modifier,
        onClick = if (alert.hasDetail) ({ expanded = !expanded }) else null
    ) {
        // Top-aligned: the glyph and the chevron mark the alert, and on an expanded one they belong
        // beside its first line rather than floating down beside the middle of the description.
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.baseline_warning_24),
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = alert.summary, style = MaterialTheme.typography.bodyMedium)
                if (expanded) {
                    AlertDetail(alert)
                }
            }
            if (alert.hasDetail) {
                Icon(
                    imageVector = if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) {
                            R.string.directions_alert_hide_details
                        } else {
                            R.string.directions_alert_show_details
                        }
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * The expanded body: the feed's description with its bare URLs made tappable (GTFS-realtime
 * `description_text` is plain text, so there is no markup to render — only links to find), then the
 * alert's own page when it names one.
 */
@Composable
private fun ColumnScope.AlertDetail(alert: TripAlertItem) {
    // The banner's own content colour: the link has to stay legible on a severity-tinted container,
    // which the theme's link colour isn't chosen against. Underlining is what marks it as a link.
    val linkColor = LocalContentColor.current
    alert.description?.let { description ->
        Text(
            text = linkifyUrls(description, linkColor),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    alert.url?.let { url ->
        val label = stringResource(R.string.situation_more_details)
        Text(
            text = buildAnnotatedString {
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        )
                    )
                ) {
                    append(label)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
