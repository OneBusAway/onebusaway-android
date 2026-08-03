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
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.ui.compose.components.AlertSeverity

/**
 * One service alert affecting a leg of the shown itinerary, as the directions draw it (#2143).
 *
 * Identity is [contentId] — a key over the rider-visible text — not the alert id, for the same reason
 * the arrivals banner keys on content (#1593): a feed that republishes the same disruption under a
 * fresh id would otherwise read as a new alert, and one leg can be handed the same alert twice.
 *
 * Nothing here names the affected ride, because nothing needs to: the row is drawn under that ride's
 * own header in the directions, so its position states what a route label used to have to.
 */
data class TripAlertItem(
    val contentId: String,
    val summary: String,
    val description: String?,
    val url: String?,
    val severity: AlertSeverity
) {
    /** Whether tapping the row reveals anything beyond [summary]. */
    val hasDetail: Boolean get() = !description.isNullOrBlank() || !url.isNullOrBlank()
}

/**
 * This leg's own alerts as rows, one per distinct alert and loudest first so a suspension can't sit
 * below a parking notice.
 *
 * Per **leg**, not per itinerary: the rows are drawn with the leg they belong to, which is what tells
 * the rider *where* in the trip the trouble is. An alert OTP hangs on two legs of one trip therefore
 * draws once under each — they are two separate facts about two separate rides, and collapsing them to
 * a single row somewhere else is what the head-of-itinerary banner used to do.
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
internal fun TripLeg.alertItems(): List<TripAlertItem> = alerts
    .mapNotNull { alert ->
        val summary = alert.header ?: alert.description ?: return@mapNotNull null
        TripAlertItem(
            contentId = alert.contentKey(),
            summary = summary,
            // Suppressed when it *is* the summary, so a header-less alert doesn't print its text twice.
            description = alert.description?.takeIf { it != summary },
            url = alert.url,
            severity = alert.severity.tone()
        )
    }
    .asRows()

/**
 * Several legs' alerts as one leg's worth of rows — what a folded interline chain draws, the rider
 * being aboard for all of them with only the leader's header to hang them under.
 *
 * Mirrors [loudestAlertTone]'s single/collection pair, and exists so "one row per distinct alert,
 * loudest first" is stated once, here, rather than re-derived by whoever merges legs next.
 */
internal fun List<TripAlertItem>.mergedWith(others: List<TripAlertItem>): List<TripAlertItem> = (this + others).asRows()

/** The shared rule: one row per distinct alert, loudest first. */
private fun List<TripAlertItem>.asRows(): List<TripAlertItem> = distinctBy { it.contentId }.sortedByDescending { it.severity }

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
