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

import org.onebusaway.android.directions.model.TripAlert
import org.onebusaway.android.directions.model.TripAlertSeverity
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.routeDisplayLabel
import org.onebusaway.android.ui.compose.components.AlertSeverity

/**
 * One service alert affecting the shown itinerary, as the directions banner draws it (#2143).
 *
 * Identity is [contentId] — a key over the rider-visible text — not the alert id, for the same reason
 * the arrivals banner keys on content (#1593): a feed that republishes the same disruption under a
 * fresh id would otherwise read as a new alert, and within a single itinerary the *same* alert arrives
 * once per leg it applies to, which must collapse to one row.
 *
 * [routeLabels] names the rides the alert came attached to, in travel order. It is what turns "service
 * is suspended" into something the rider can act on when a trip has more than one ride, and it is a
 * structural fact — which legs OTP hung the alert on — not an inference from the alert text.
 */
data class TripAlertItem(
    val contentId: String,
    val summary: String,
    val description: String?,
    val url: String?,
    val severity: AlertSeverity,
    val routeLabels: List<String>
) {
    /** Whether tapping the row reveals anything beyond [summary]. */
    val hasDetail: Boolean get() = !description.isNullOrBlank() || !url.isNullOrBlank()
}

/**
 * Every alert on this itinerary's legs, collapsed to one row per distinct alert and ordered
 * loudest-first so a suspension can't sit below a parking notice.
 *
 * No active-window check: OTP has already scoped each leg's alerts to that leg's own time window (see
 * the `alerts` selection in `Plan.graphql`), so re-deciding it here would mean re-implementing the
 * server's rule against a `to`/`from` pair this query doesn't even ask for.
 *
 * An alert with no rider-visible text at all is dropped rather than drawn as an empty row — there is
 * nothing to tell the rider, and a blank severity-tinted card reads as a rendering failure.
 *
 * Pure, so it is unit-testable without a plan, a `Context`, or a renderer.
 */
fun TripItinerary.alertItems(): List<TripAlertItem> = legs
    .flatMap { leg -> leg.alerts.map { alert -> alert to leg.routeDisplayLabel() } }
    .groupBy { (alert, _) -> alert.contentKey() }
    .mapNotNull { (contentId, group) ->
        val alert = group.first().first
        val summary = alert.header ?: alert.description ?: return@mapNotNull null
        TripAlertItem(
            contentId = contentId,
            summary = summary,
            // Suppressed when it *is* the summary, so a header-less alert doesn't print its text twice.
            description = alert.description?.takeIf { it != summary },
            url = alert.url,
            severity = alert.severity.tone(),
            routeLabels = group.mapNotNull { (_, label) -> label }.distinct()
        )
    }
    .sortedByDescending { it.severity }

/**
 * The loudest tone among this leg's own alerts, or null when it carries none — what marks the leg's
 * symbol on an itinerary option card (see [ModeSymbol.alert]).
 */
internal fun TripLeg.loudestAlertTone(): AlertSeverity? = alerts.map { it.severity.tone() }.maxOrNull()

/** [loudestAlertTone] across several legs — what one symbol standing for all of them is marked with. */
internal fun List<TripLeg>.loudestAlertTone(): AlertSeverity? = mapNotNull { it.loudestAlertTone() }.maxOrNull()

/**
 * A key over the alert's rider-visible text — the same content-identity idea as
 * [contentKey][org.onebusaway.android.models.contentKey] on the OBA situation path, over the fields
 * OTP publishes. NUL-separated so adjacent values can't run together and collide across field
 * boundaries.
 */
private fun TripAlert.contentKey(): String = listOf(header, description, url, severity.name)
    .joinToString("\u0000") { it.orEmpty() }

/**
 * OTP2's severity scale mapped onto the app's shared banner tones. `UNKNOWN_SEVERITY` lands on
 * [AlertSeverity.WARNING] — the same place the OBA situation path puts a severity it can't read
 * (`severityOf`) — because an alert whose severity is unstated is still an alert, and silently
 * demoting it to INFO would hide the loudest thing OTP had to say about a leg.
 */
internal fun TripAlertSeverity.tone(): AlertSeverity = when (this) {
    TripAlertSeverity.INFO -> AlertSeverity.INFO
    TripAlertSeverity.SEVERE -> AlertSeverity.ERROR
    TripAlertSeverity.WARNING, TripAlertSeverity.UNKNOWN_SEVERITY -> AlertSeverity.WARNING
}
