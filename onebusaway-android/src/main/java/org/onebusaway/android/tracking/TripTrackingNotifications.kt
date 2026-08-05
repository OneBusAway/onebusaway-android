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
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.ui.arrivals.ArrivalsListLauncher

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
    val title: String,
    /** The row's strip, as one line: "4 min · 12 min · 24 min". */
    val text: String,
    /** The status-bar chip text on Android 16+ ("4 min"); null while there is no number to show. */
    val shortText: String?,
    @param:ColorRes val colorRes: Int,
    val progress: Int,
    val progressMax: Int,
    /** The departures after the next one, as points further back along the road. */
    val progressPoints: List<Int>,
    val indeterminate: Boolean,
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
            title = title(route),
            text = context.getString(R.string.trip_tracking_pending),
            shortText = null,
            colorRes = R.color.theme_primary,
            progress = 0,
            progressMax = trackingBarSpan(),
            progressPoints = emptyList(),
            indeterminate = true,
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
            .setContentIntent(openArrivals(card))
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setStyle(progressStyle(card, color))
        } else {
            // The pre-16 fallback: the classic determinate bar, which has no notion of points, so it
            // shows the wait for the next departure alone. The full strip is in the text, so a device
            // that shows neither bar still reads correctly.
            builder.setProgress(card.progressMax, card.progress, card.indeterminate)
        }
        return builder
    }

    private fun progressStyle(card: TrackedRouteCard, color: Int): NotificationCompat.ProgressStyle = NotificationCompat.ProgressStyle()
        // One segment spanning the road to the stop; the tracker is the next bus driving along it and
        // the points are the ones further back. Per-*stop* segments would be the richer rendering, but
        // they would need the trip's remaining stop list, which this feature does not fetch — a
        // deliberate follow-up, not an omission to paper over with a guess.
        .setProgressSegments(
            listOf(NotificationCompat.ProgressStyle.Segment(card.progressMax).setColor(color))
        )
        .setProgressPoints(
            card.progressPoints.map { NotificationCompat.ProgressStyle.Point(it).setColor(color) }
        )
        .setProgress(card.progress)
        .setProgressIndeterminate(card.indeterminate)
        // The bus, at the head of the approach; it marches right as the arrival closes in.
        .setProgressTrackerIcon(tinted(R.drawable.ic_bus, color))
        // The rider's own stop, anchoring the right-hand end: what every bus on the bar is driving
        // toward, and where the tracker lands as it arrives.
        .setProgressEndIcon(tinted(R.drawable.stop_flag, color))

    /**
     * A drawable as a notification icon in [color]. The tint is not decoration: both of these vectors
     * are authored as flat `#000000` glyphs for use over the app's own surfaces, and a notification
     * draws them over the *system's* background — which is near-black in dark mode, so untinted they
     * disappear entirely. Tinting also keeps them on the same deviation colour as the bar they ride.
     */
    private fun tinted(@DrawableRes drawable: Int, color: Int): IconCompat = IconCompat.createWithResource(context, drawable).setTint(color)

    /** Tapping the card opens the arrivals list for the stop being watched. */
    private fun openArrivals(card: TrackedRouteCard): PendingIntent {
        val intent = ArrivalsListLauncher.Builder(context, card.stopId)
            .setStopName(card.stopName)
            .intent
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
    fun card(route: TrackedRoute, outcome: TrackingOutcome, rank: Int): TrackedRouteCard {
        val departures = (outcome as? TrackingOutcome.Live)?.departures.orEmpty()
        val next = departures.firstOrNull()
        return TrackedRouteCard(
            rowId = route.id,
            notificationId = trackingNotificationId(route.id),
            stopId = route.key.stopId,
            stopName = route.stopName,
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
            colorRes = next?.displayColorRes ?: R.color.theme_primary,
            progress = next?.let { trackingBarPosition(it.eta) } ?: 0,
            progressMax = trackingBarSpan(),
            progressPoints = departures.drop(1).map { trackingBarPosition(it.eta) },
            // Indeterminate only while nothing is known — a spinner otherwise reads as "still
            // working on it" when the answer is already on screen.
            indeterminate = departures.isEmpty(),
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

    /** Ranks cards most-recently-tracked first — sort keys order lexicographically, ascending. */
    private fun sortKey(rank: Int): String = rank.toString().padStart(2, '0')
}
