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
package org.onebusaway.android.ui.compose.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

/**
 * How loudly a warning is drawn — the app's single alert-severity vocabulary, shared by every surface
 * that shows one (the arrivals banner, the trip planner's directions, #2143, and the one caution that
 * isn't a feed alert at all, #2218). Each feed's own severity scale is mapped onto these three at its
 * own wire boundary: the OBA `situation` scale by
 * `severityOf` (`ArrivalsRepository`), OTP2's `AlertSeverityLevelType` by `TripAlertSeverity.tone`
 * (`TripAlerts`). Presentation lives here so a severe alert can't read as urgent on one screen and
 * merely advisory on another.
 *
 * **Declaration order is loudness order**, quietest first, and callers rely on it: the loudest alert of
 * a set is its `max()`, and a list of alerts sorts worst-first by `sortedByDescending`. Any new tone
 * must be declared in its place on that scale, not appended.
 */
enum class AlertSeverity(@StringRes val labelRes: Int) {
    INFO(R.string.service_alert_severity_info),
    WARNING(R.string.service_alert_severity_warning),
    ERROR(R.string.service_alert_severity_severe)
}

/**
 * The colour a service alert of [severity] is marked in when it *annotates* something else rather than
 * filling a container of its own — the warning triangle beside an itinerary option's mode symbols
 * (#2143). Foreground roles, since these are drawn on an ordinary card surface rather than on the
 * matching `…Container` [AlertSurface] fills.
 *
 * Deliberately not the `md_theme_severity*` palette [TripPlanError][
 * org.onebusaway.android.ui.tripplan.TripPlanError] uses: those are tuned to be legible on the
 * *inverting* `inverseSurface` snackbar bar, so on an ordinary surface they read inverted.
 */
@Composable
fun alertAccentColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.ERROR -> MaterialTheme.colorScheme.error
    AlertSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
    AlertSeverity.INFO -> MaterialTheme.colorScheme.secondary
}

/**
 * The severity-tinted rounded card a warning is drawn on. Only the container is shared: each surface
 * lays out its own contents (the arrivals banner is one line, the directions banner expands to the
 * full description, and `DirectionsCautionBanner` (#2218) is the one caution that isn't a feed alert
 * at all), and what they must agree on is the *colour* — which is exactly the part a second copy of
 * the table below would eventually get wrong.
 *
 * [onClick] makes the whole card the tap target; a card with nothing to open passes null and takes no
 * clickable at all rather than an empty one. The card also exposes [severity] as a localized state
 * description, because its container colour is otherwise invisible to a screen reader.
 */
@Composable
fun AlertSurface(
    severity: AlertSeverity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val severityDescription = stringResource(severity.labelRes)
    val (container, onContainer) = when (severity) {
        AlertSeverity.ERROR ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AlertSeverity.WARNING ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        AlertSeverity.INFO ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .semantics { stateDescription = severityDescription },
        content = content
    )
}
