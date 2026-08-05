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
package org.onebusaway.android.tracking

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.ColorRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.nav.putRouteReveal
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.ScheduleDeviation

/**
 * The rendered content of one tracked row's notification.
 *
 * A value type on purpose: [TripTrackingService] re-renders every tick so the countdown keeps
 * advancing between polls, and comparing this to the previous render is what lets it skip the
 * re-post when nothing the rider can see has changed — the direct comparison of the meaningful
 * thing, rather than a revision counter that has to be remembered to bump.
 */
data class TrackedRouteCard(
    val rowId: String,
    val notificationId: Int,
    val stopId: String,
    val stopName: String,
    val routeId: String,
    /** The row's direction, so the map frames the one the rider is watching. */
    val headsign: String,
    val title: String,
    /** The row's strip, as one line: "4 min · 12 min · 24 min". The card's text on every platform,
     *  and the whole of it below Android 16, where there are no metrics to lay out. */
    val text: String,
    /** The status-bar chip text on Android 16+ ("4 min"); null while there is no number to show. */
    val shortText: String?,
    @param:ColorRes val colorRes: Int,
    /** One tile per upcoming departure, soonest first; empty until the first response lands. */
    val metrics: List<TrackedMetric>,
    /**
     * The most-recently-tracked session: the one promoted to the status-bar chip and ranked to the
     * top. onebusaway-ios does this with ActivityKit's `relevanceScore` (their #1243); Android's
     * equivalent levers are the promoted-ongoing request, notification priority, and the sort key.
     */
    val primary: Boolean,
    /** Ranks the cards against each other, most-recently-tracked first. */
    val sortKey: String
)

/**
 * One departure as a metric tile: a countdown over the clock time it is due.
 *
 * The split mirrors the arrivals drawer's ETA pill exactly — a big number with its unit, and the
 * scheduled time small underneath — which is what makes the card read as the row it came from.
 * [semanticStyle] is the platform's own vocabulary for "this is fine" / "watch out", and is how
 * lateness reaches a template that has no colour of its own to spend on it.
 */
data class TrackedMetric(
    val value: String,
    /** Null for a value that is already a whole phrase ("Now", "Canceled"). */
    val unit: String?,
    val label: String,
    val semanticStyle: Int
)

/**
 * The notification id for a tracked row. Derived from the row's own identity so a card keeps it
 * across re-renders, service restarts, and reordering — an index-derived id would make two rows swap
 * cards when the rider tracks a third.
 *
 * Namespaced into its own high range so a hash collision cannot land on one of the app's other fixed
 * notification ids (the trip-plan monitor's, the destination reminder's), which are small constants.
 */
fun trackingNotificationId(rowId: String): Int = TRACKING_ID_BASE or (rowId.hashCode() and TRACKING_ID_MASK)

private const val TRACKING_ID_BASE = 0x7B000000
private const val TRACKING_ID_MASK = 0x00FFFFFF

/**
 * Separates the departures on the card's one line. The same separator the My-tab reminder rows use
 * for their subtitle, so the two read as the same kind of list.
 */
private const val DEPARTURE_SEPARATOR = "  ·  "

/**
 * Builds the Live Update notification for a tracked route row.
 *
 * On Android 16+ this is a `ProgressStyle` notification requesting the promoted-ongoing treatment,
 * which is what puts the countdown in the status-bar chip and on the Lock Screen without the rider
 * unlocking anything — the closest platform analogue to the iOS Live Activity this mirrors. Below
 * that it degrades to a plain ongoing notification carrying the same departures in its text plus a
 * legacy determinate progress bar; `NotificationCompat` is used throughout so the degradation is a
 * single explicit branch rather than two builders.
 *
 * The card is deliberately **not** a rendering of the Compose `EtaStrip`: a custom `RemoteViews`
 * layout disqualifies a notification from the Live Update treatment entirely, so the row is
 * expressed inside the standard template — its departures as the text line, and the road they are
 * driving down as the progress bar (see the diagram in [TrackingPolicy]).
 */
class TripTrackingNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** The card shown before the first arrivals response lands, so the service can promote to the
     *  foreground immediately (the platform gives it seconds, not a network round trip). */
    fun pending(route: TrackedRoute): Notification = builder(
        TrackedRouteCard(
            rowId = route.id,
            notificationId = trackingNotificationId(route.id),
            stopId = route.key.stopId,
            stopName = route.stopName,
            routeId = route.key.routeId,
            headsign = route.key.headsign,
            title = title(route),
            text = context.getString(R.string.trip_tracking_pending),
            shortText = null,
            colorRes = R.color.theme_primary,
            metrics = emptyList(),
            primary = true,
            sortKey = sortKey(0)
        )
    ).build()

    fun build(card: TrackedRouteCard): Notification = builder(card).build()

    private fun builder(card: TrackedRouteCard): NotificationCompat.Builder {
        val color = ContextCompat.getColor(context, card.colorRes)
        val builder = NotificationCompat.Builder(context, NotificationChannels.TRIP_TRACKING_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(card.title)
            .setContentText(card.text)
            .setSubText(card.stopName)
            .setColor(color)
            // Deliberately NOT setColorized(true): a colorized notification is disqualified from the
            // Android 16 Live Update treatment, and this one would have qualified as colorized on
            // every device (isColorized() is true for a colorized *foreground-service* notification
            // even without the colorized-notification permission). The lateness colour still reaches
            // the card through setColor and the progress segment.
            .setOngoing(true)
            // The only re-alert guard the card needs: it re-posts every few seconds as the countdown
            // advances, and this keeps the platform from treating each re-post as a fresh arrival.
            // Deliberately NOT setSilent(true) — that stamps the notification FLAG_SILENT, which asks
            // the platform to treat it as a low-attention notification and is at odds with the
            // promoted-ongoing request below. Actual silence is the channel's job
            // (NotificationChannels.TRIP_TRACKING_ID: no sound, no vibration, no lights).
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSortKey(card.sortKey)
            // The rider explicitly asked to watch this row, so the promoted session outranks the
            // ones it superseded; both stay silent (the channel has no sound, vibration, or lights).
            .setPriority(
                if (card.primary) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW
            )
            .setContentIntent(openRouteOnMap(card))
            // Swiping the card away is the same intent as the action: stop watching this row. Without
            // it a dismissed card would come straight back on the next tick.
            .setDeleteIntent(untrack(card))
            .addAction(
                R.drawable.ic_bus,
                context.getString(R.string.trip_tracking_stop_action),
                untrack(card)
            )

        if (card.primary) {
            // API 36+ only inside NotificationCompat; a no-op below, where there is no chip to fill.
            builder.setRequestPromotedOngoing(true)
            card.shortText?.let(builder::setShortCriticalText)
        }

        // MetricStyle is Android 16+; below it the card is the standard template, whose text line
        // already carries the same departures. Nothing is lost but the layout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && card.metrics.isNotEmpty()) {
            builder.setStyle(metricStyle(card))
        }
        return builder
    }

    private fun metricStyle(card: TrackedRouteCard): NotificationCompat.MetricStyle = NotificationCompat.MetricStyle()
        .setMetrics(
            card.metrics.map { metric ->
                NotificationCompat.Metric(
                    if (metric.unit == null) {
                        NotificationCompat.Metric.FixedText(metric.value)
                    } else {
                        NotificationCompat.Metric.FixedText(metric.value, metric.unit)
                    },
                    metric.label,
                    metric.semanticStyle
                )
            }
        )
        // The next departure is the one the status-bar chip is cut from — the same choice
        // [TrackedRouteCard.shortText] makes, told to the template instead of formatted by hand.
        .setCriticalMetric(0)

    /**
     * Tapping the card frames the watched row on the map: this route, scoped to this stop and this
     * direction. The arrivals *list* was the first target, but it answers a question the card has
     * already answered — the next departures are printed on it. What the card cannot show is where
     * the vehicles actually are, which is the reason to open the app at all.
     */
    private fun openRouteOnMap(card: TrackedRouteCard): PendingIntent {
        val intent = Intent(context, HomeActivity::class.java)
            .putRouteReveal(
                ShowRouteRequest(
                    routeId = card.routeId,
                    directionStopId = card.stopId,
                    directionHeadsign = card.headsign.takeIf(String::isNotBlank)
                )
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, card.notificationId, intent, pendingIntentFlags())
    }

    private fun untrack(card: TrackedRouteCard): PendingIntent {
        val intent = Intent(context, TripTrackingReceiver::class.java)
            .setAction(TripTrackingReceiver.ACTION_UNTRACK)
            .putExtra(TripTrackingReceiver.EXTRA_ROW_ID, card.rowId)
        return PendingIntent.getBroadcast(
            context,
            card.notificationId,
            intent,
            pendingIntentFlags()
        )
    }

    private fun title(route: TrackedRoute): String = if (route.key.headsign.isBlank()) {
        route.routeName
    } else {
        context.getString(R.string.trip_tracking_title, route.routeName, route.key.headsign)
    }

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    /**
     * Renders one tracked row's card. Kept here rather than in [TripTrackingService] because every
     * line of it is a string or colour lookup against the app's resources; the decisions it renders
     * are all made in [TrackingPolicy].
     */
    fun card(
        route: TrackedRoute,
        outcome: TrackingOutcome,
        rank: Int
    ): TrackedRouteCard {
        val departures = (outcome as? TrackingOutcome.Live)?.departures.orEmpty()
        val next = departures.firstOrNull()
        return TrackedRouteCard(
            rowId = route.id,
            notificationId = trackingNotificationId(route.id),
            stopId = route.key.stopId,
            stopName = route.stopName,
            routeId = route.key.routeId,
            headsign = route.key.headsign,
            title = title(route),
            text = if (departures.isEmpty()) {
                context.getString(R.string.trip_tracking_pending)
            } else {
                departures.joinToString(DEPARTURE_SEPARATOR) { label(it) }
            },
            // The chip gets the next departure alone — it is a handful of characters in the status
            // bar, so the rest of the strip has nowhere to go.
            shortText = next?.let(::label),
            // Tinted by the *next* departure's lateness, matching the pill the rider tapped from.
            colorRes = next?.status?.displayColorRes ?: R.color.theme_primary,
            metrics = departures.map(::metric),
            primary = rank == 0,
            sortKey = sortKey(rank)
        )
    }

    /**
     * One departure as it appears in the strip. A bus still seconds away floors to zero minutes,
     * which the arrivals pill renders as "NOW", so the card says the same rather than "0 min".
     */
    private fun label(departure: TrackedDeparture): String = when {
        departure.canceled -> context.getString(R.string.trip_tracking_canceled)
        departure.etaMinutes <= 0 -> context.getString(R.string.trip_tracking_short_now)
        else -> context.getString(R.string.trip_tracking_short_eta, departure.etaMinutes.toInt())
    }

    /** One departure as a metric tile: the countdown, its unit, and the clock time it is due. */
    private fun metric(departure: TrackedDeparture): TrackedMetric = TrackedMetric(
        value = when {
            departure.canceled -> context.getString(R.string.trip_tracking_canceled)
            departure.etaMinutes <= 0 -> context.getString(R.string.trip_tracking_short_now)
            else -> departure.etaMinutes.toString()
        },
        // Only a bare number takes a unit; "Now" and "Canceled" are already whole phrases.
        unit = context.getString(R.string.trip_tracking_unit_minutes)
            .takeIf { !departure.canceled && departure.etaMinutes > 0 },
        label = DisplayFormat.formatTime(context, departure.displayTime.epochMs),
        semanticStyle = semanticStyle(departure)
    )

    /**
     * The platform tone for a departure. Early counts as a warning rather than praise, matching how
     * the app words it elsewhere: a bus running ahead is one the rider can miss. A scheduled time is
     * left unspecified — it is a timetable entry, not a measurement, so it has no news either way.
     */
    private fun semanticStyle(departure: TrackedDeparture): Int = when {
        departure.canceled -> NotificationCompat.SEMANTIC_STYLE_DANGER
        !departure.predicted -> NotificationCompat.SEMANTIC_STYLE_UNSPECIFIED
        departure.status == ScheduleDeviation.Status.ON_TIME -> NotificationCompat.SEMANTIC_STYLE_SAFE
        else -> NotificationCompat.SEMANTIC_STYLE_CAUTION
    }

    /** Ranks cards most-recently-tracked first — sort keys order lexicographically, ascending. */
    private fun sortKey(rank: Int): String = rank.toString().padStart(2, '0')
}
