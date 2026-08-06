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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.time.isEtaNow
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.nav.RouteRevealExtras
import org.onebusaway.android.ui.nav.putStopRouteReveal
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.GeoPoint

/**
 * The rendered content of one tracked row's notification.
 *
 * A value type on purpose: [TripTrackingService] re-renders every tick so the countdown keeps
 * advancing between polls, and comparing this to the previous render is what lets it skip the
 * re-post when nothing the rider can see has changed — the direct comparison of the meaningful
 * thing, rather than a revision counter that has to be remembered to bump.
 */
data class TrackedRouteCard(
    /** The row this renders. Held whole rather than copied field by field: everything the card needs
     *  about *what* is tracked already lives here, and two names for one headsign can drift. */
    val route: TrackedRoute,
    val title: String,
    /** The row's strip, as one line: "4 min · 12 min · 24 min". The card's text on every platform,
     *  and the whole of it below Android 16, where there are no metrics to lay out. */
    val text: String,
    /** The status-bar chip text on Android 16+ ("4 min"); null while there is no number to show. */
    val shortText: String?,
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
) {
    val notificationId: Int get() = trackingNotificationId(route.id)
}

/**
 * One departure as a metric tile: the clock time it is due, captioning the countdown to it.
 *
 * Note the order, which is the template's and not ours: a metric tile is a small caption *above* a
 * large value, so [dueAt] sits on top and the countdown below — the inverse of the arrivals drawer's
 * ETA pill, which leads with the number. The countdown stays the value rather than the caption
 * because that is the half the template emphasises and cuts the status-bar chip from, and the
 * countdown is what the rider is watching.
 *
 * Deliberately carries no colour. `Metric` takes a semantic style that tints the value — safe,
 * caution, danger — and the card used to map lateness onto it. The tiles then read as three
 * differently-coloured numbers in a shade full of other apps' notifications, which is a lot of
 * emphasis for a distinction the rider can already make from the times themselves. Lateness is still
 * on the row they tracked it from, in the palette that app-side surface owns.
 *
 * [value] and [unit] are kept apart here for the text line and the chip to join, *not* to be handed
 * to `FixedText(value, unit)`: the template renders that unit against the caption, so splitting them
 * there reads as "4:27 PM (min)" over a bare "4".
 */
data class TrackedMetric(
    val value: String,
    /** Null for a value that is already a whole phrase ("Now", "Canceled"). */
    val unit: String?,
    /** The clock time the departure is due ("4:27 PM") — the tile's caption. */
    val dueAt: String
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
 * Two platform thresholds, and they are not the same one:
 *
 *  - **Android 16+** takes the promoted-ongoing request, which is what puts the countdown in the
 *    status-bar chip and on the Lock Screen without the rider unlocking anything — the closest
 *    platform analogue to the iOS Live Activity this mirrors.
 *  - **Android 17+** takes `MetricStyle`, the tile layout (androidx marks it `@RequiresApi(37)`).
 *
 * So Android 16 gets the chip with the plain standard template behind it, and everything below gets
 * an ordinary ongoing notification. All three carry the same departures on the text line, which is
 * why the Track action is offered on every version down to `minSdk`: what degrades is the
 * glanceability, not the tracking.
 *
 * The card is deliberately **not** a rendering of the Compose `EtaStrip`: a custom `RemoteViews`
 * layout disqualifies a notification from the Live Update treatment entirely, so the row is
 * expressed inside the standard template — one tile per departure, each the countdown to it under
 * the clock time it is due. The tile's own order, caption over value, is the template's to choose;
 * see [TrackedMetric].
 */
class TripTrackingNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * The card shown before the first arrivals response lands, so the service can promote to the
     * foreground immediately (the platform gives it seconds, not a network round trip). Rendered
     * through [card] rather than assembled by hand, so the first card of every session cannot drift
     * from the ones that follow it.
     */
    fun pending(route: TrackedRoute): Notification = build(card(route, TrackingOutcome.Pending, rank = 0))

    fun build(card: TrackedRouteCard): Notification = builder(card).build()

    private fun builder(card: TrackedRouteCard): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, NotificationChannels.TRIP_TRACKING_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(card.title)
            .setContentText(card.text)
            .setSubText(card.route.stopName)
            // The brand colour, and deliberately not the next departure's lateness: one accent for a
            // card listing three departures could only ever describe one of them, and the shade is
            // not where this app's lateness palette is legible anyway (see [TrackedMetric], which
            // gives up its per-tile tint for the same reason). Theme-aware through theme_primary
            // (brand_color / brand_color_dark), and per-brand for the white-label flavours, which
            // each define both.
            .setColor(ContextCompat.getColor(context, R.color.theme_primary))
            // Deliberately NOT setColorized(true): a colorized notification is disqualified from the
            // Android 16 Live Update treatment, and this one would have qualified as colorized on
            // every device (isColorized() is true for a colorized *foreground-service* notification
            // even without the colorized-notification permission).
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
            .setContentIntent(openStopRouteOnMap(card))
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

        // MetricStyle is Android 17+, not 16: androidx marks both it and Metric @RequiresApi(37).
        // This gate said BAKLAVA (36), so an Android 16 device built the tiles, posted them, and had
        // the platform drop them on the floor — the card fell back to the standard template by
        // accident rather than on purpose, and the tiles had never once rendered before an OS
        // upgrade proved it. Below 17 that text line already carries the same departures, so nothing
        // is lost but the layout; the promoted chip and the Lock Screen are a separate, lower bar
        // (36, just above).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && card.metrics.isNotEmpty()) {
            builder.setStyle(metricStyle(card))
        }
        return builder
    }

    private fun metricStyle(card: TrackedRouteCard): NotificationCompat.MetricStyle = NotificationCompat.MetricStyle()
        .setMetrics(
            card.metrics.map { metric ->
                NotificationCompat.Metric(
                    // The countdown whole ("4 min"), not split into `FixedText(value, unit)`. A
                    // metric tile is a caption over a number, and the template renders the unit
                    // against the *caption*, not the number — so the split came out as
                    // "4:27 PM (min)" over a bare "4". Flattened through [countdown], the same way
                    // the text line and the chip are, so all three read alike.
                    NotificationCompat.Metric.FixedText(countdown(metric)),
                    metric.dueAt
                )
            }
        )
        // The next departure is the one the status-bar chip is cut from — the same choice
        // [TrackedRouteCard.shortText] makes, told to the template instead of formatted by hand.
        .setCriticalMetric(0)

    /**
     * Tapping the card opens the map on the watched stop with this route selected inside it — the
     * drawer's own "stop, then route" view, not standalone route focus. Framing the route alone would
     * drop the rider onto the whole line with no drawer and no sense of where on it they are standing;
     * what a tracked row means is "this route, at my stop".
     *
     * The arrivals *list* was the first target, but it answers a question the card has already
     * answered — the next departures are printed on it. What the card cannot show is where the
     * vehicles actually are.
     */
    private fun openStopRouteOnMap(card: TrackedRouteCard): PendingIntent {
        val intent = Intent(context, HomeActivity::class.java)
            .putStopRouteReveal(
                stop = FocusedStop(
                    id = card.route.key.stopId,
                    name = card.route.stopName,
                    code = null,
                    point = GeoPoint(card.route.stopLat, card.route.stopLon)
                ),
                route = RouteRevealExtras(
                    routeId = card.route.key.routeId,
                    routeShortName = card.route.routeName,
                    headsign = card.route.key.headsign.takeIf(String::isNotBlank)
                )
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, card.notificationId, intent, pendingIntentFlags())
    }

    private fun untrack(card: TrackedRouteCard): PendingIntent {
        val intent = Intent(context, TripTrackingReceiver::class.java)
            .setAction(TripTrackingReceiver.ACTION_UNTRACK)
            .putExtra(TripTrackingReceiver.EXTRA_ROW_ID, card.route.id)
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
        val metrics = departures.map(::metric)
        return TrackedRouteCard(
            route = route,
            title = title(route),
            text = if (metrics.isEmpty()) {
                context.getString(R.string.trip_tracking_pending)
            } else {
                metrics.joinToString(DEPARTURE_SEPARATOR, transform = ::countdown)
            },
            // The chip gets the next departure alone — it is a handful of characters in the status
            // bar, so the rest of the strip has nowhere to go.
            shortText = metrics.firstOrNull()?.let(::countdown),
            metrics = metrics,
            primary = rank == 0,
            sortKey = sortKey(rank)
        )
    }

    /**
     * One departure as a metric tile: the countdown with its unit, or a whole phrase where a number
     * would be wrong, over the clock time it is due.
     *
     * The single place a departure is put into words. The strip line and the status-bar chip are both
     * derived from these tiles ([countdown]) rather than formatted again — split, moving the "0 minutes
     * means NOW" cutoff in one would leave the chip saying "Now" while the tile beside it said "0 min".
     */
    private fun metric(departure: TrackedDeparture): TrackedMetric {
        // The app's one ETA formatter, not a hand-built "N min": the tracking window runs to 65
        // minutes, so past the hour this is the difference between the card saying "63 min" and every
        // other surface saying "1hr 3min" (#1777). Its parts alternate number/unit, so the trailing
        // unit is the tile's small text and everything before it the value.
        val parts = DisplayFormat.formatEtaParts(context, departure.etaMinutes)
        val now = isEtaNow(departure.etaMinutes)
        return TrackedMetric(
            value = when {
                departure.canceled -> context.getString(R.string.trip_tracking_canceled)
                // A bus still seconds away floors to zero minutes, and one that has just pulled out is
                // still being boarded — [isEtaNow] is the same cutoff the arrivals pill renders "NOW" at,
                // shared rather than restated so the shade and the row it was copied from cannot disagree
                // (#2177). Every departure the card can hold is inside it anyway (see TRACKING_LINGER),
                // so this is the whole of the card's non-canceled past.
                now -> context.getString(R.string.trip_tracking_short_now)
                else -> parts.dropLast(1).joinToString("") { it.text }
            },
            // Only a bare countdown takes a unit; "Now" and "Canceled" are already whole phrases.
            unit = parts.last().text.takeIf { !departure.canceled && !now },
            dueAt = DisplayFormat.formatTime(context, departure.displayTime.epochMs)
        )
    }

    /** A tile's countdown as one string ("4 min", "Now") — the tile's own number, and what the
     *  card's text line and the status-bar chip are cut from. */
    private fun countdown(metric: TrackedMetric): String = listOfNotNull(metric.value, metric.unit).joinToString(" ")

    /** Ranks cards most-recently-tracked first — sort keys order lexicographically, ascending. */
    private fun sortKey(rank: Int): String = rank.toString().padStart(2, '0')
}
