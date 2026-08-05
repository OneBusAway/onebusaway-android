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
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.ui.arrivals.ArrivalsListLauncher

/**
 * The rendered content of one tracked trip's notification.
 *
 * A value type on purpose: [TripTrackingService] re-renders every tick so the countdown keeps
 * advancing between polls, and comparing this to the previous render is what lets it skip the
 * re-post when nothing the rider can see has changed — the direct comparison of the meaningful
 * thing, rather than a revision counter that has to be remembered to bump.
 */
data class TrackedTripCard(
    val instanceId: String,
    val notificationId: Int,
    val stopId: String,
    val stopName: String,
    val title: String,
    val text: String,
    /** The status-bar chip text on Android 16+ ("4 min"); null while there is no number to show. */
    val shortText: String?,
    @param:ColorRes val colorRes: Int,
    val progress: Int,
    val progressMax: Int,
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
 * The notification id for a tracked trip. Derived from the trip instance so a card keeps its
 * identity across re-renders, service restarts, and reordering — an index-derived id would make two
 * trips swap cards when the rider tracks a third.
 *
 * Namespaced into its own high range so a hash collision cannot land on one of the app's other
 * fixed notification ids (the trip-plan monitor's, the destination reminder's), which are all small
 * constants.
 */
fun trackingNotificationId(instanceId: String): Int = TRACKING_ID_BASE or (instanceId.hashCode() and TRACKING_ID_MASK)

private const val TRACKING_ID_BASE = 0x7B000000
private const val TRACKING_ID_MASK = 0x00FFFFFF

/**
 * Builds the Live Update notification for a tracked trip.
 *
 * On Android 16+ this is a `ProgressStyle` notification requesting the promoted-ongoing treatment,
 * which is what puts the countdown in the status-bar chip and on the Lock Screen without the rider
 * unlocking anything — the closest platform analogue to the iOS Live Activity this mirrors. Below
 * that it degrades to a plain ongoing notification carrying the same countdown in its text plus a
 * legacy determinate progress bar; `NotificationCompat` is used throughout so the degradation is a
 * single explicit branch rather than two builders.
 */
class TripTrackingNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** The card shown before the first arrivals response lands, so the service can promote to the
     *  foreground immediately (the platform gives it seconds, not a network round trip). */
    fun pending(trip: TrackedTrip): Notification = builder(
        TrackedTripCard(
            instanceId = trip.instanceId,
            notificationId = trackingNotificationId(trip.instanceId),
            stopId = trip.key.stopId,
            stopName = trip.stopName,
            title = title(trip),
            text = context.getString(R.string.trip_tracking_pending),
            shortText = null,
            colorRes = R.color.theme_primary,
            progress = 0,
            progressMax = trackingProgressMax(trip),
            indeterminate = true,
            primary = true,
            sortKey = sortKey(0)
        )
    ).build()

    fun build(card: TrackedTripCard): Notification = builder(card).build()

    private fun builder(card: TrackedTripCard): NotificationCompat.Builder {
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
            // The rider explicitly asked to watch this bus, so the promoted session outranks the
            // ones it superseded; both stay below anything that alerts (the channel is LOW).
            .setPriority(
                if (card.primary) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW
            )
            .setContentIntent(openArrivals(card))
            // Swiping the card away is the same intent as the action: stop watching this bus. Without
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
            // The pre-16 fallback: the classic determinate bar. The countdown itself is in the text,
            // so a device that shows neither still reads correctly.
            builder.setProgress(card.progressMax, card.progress, card.indeterminate)
        }
        return builder
    }

    private fun progressStyle(card: TrackedTripCard, color: Int): NotificationCompat.ProgressStyle = NotificationCompat.ProgressStyle()
        // One segment spanning the rider's whole wait; the tracker icon rides along it. Splitting it
        // into per-stop segments would need the trip's remaining stop list, which this feature does
        // not fetch — a deliberate follow-up, not an omission to paper over with a guess.
        .setProgressSegments(
            listOf(NotificationCompat.ProgressStyle.Segment(card.progressMax).setColor(color))
        )
        .setProgress(card.progress)
        .setProgressIndeterminate(card.indeterminate)
        .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_bus))

    /** Tapping the card opens the arrivals list for the stop being watched. */
    private fun openArrivals(card: TrackedTripCard): PendingIntent {
        val intent = ArrivalsListLauncher.Builder(context, card.stopId)
            .setStopName(card.stopName)
            .intent
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, card.notificationId, intent, pendingIntentFlags())
    }

    private fun untrack(card: TrackedTripCard): PendingIntent {
        val intent = Intent(context, TripTrackingReceiver::class.java)
            .setAction(TripTrackingReceiver.ACTION_UNTRACK)
            .putExtra(TripTrackingReceiver.EXTRA_INSTANCE_ID, card.instanceId)
        return PendingIntent.getBroadcast(
            context,
            card.notificationId,
            intent,
            pendingIntentFlags()
        )
    }

    private fun title(trip: TrackedTrip): String = if (trip.key.headsign.isBlank()) {
        trip.routeName
    } else {
        context.getString(R.string.trip_tracking_title, trip.routeName, trip.key.headsign)
    }

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    /**
     * Renders one tracked trip's card. Kept here rather than in [TripTrackingService] because every
     * line of it is a string lookup against the app's resources; the decisions it renders are all
     * made in [TrackingPolicy].
     */
    fun card(
        trip: TrackedTrip,
        outcome: TrackingOutcome,
        match: TrackedMatch?,
        rank: Int
    ): TrackedTripCard {
        val waiting = outcome as? TrackingOutcome.Waiting
        // Null unless there is a number worth printing. A trip still seconds away floors to zero
        // minutes — the arrivals pill calls that "NOW", so the card says "arriving now" too rather
        // than counting down to "0 min".
        val minutes = waiting?.etaMinutes?.toInt()?.takeIf { it > 0 }
        val arriving = outcome is TrackingOutcome.Arriving || (waiting != null && minutes == null)
        return TrackedTripCard(
            instanceId = trip.instanceId,
            notificationId = trackingNotificationId(trip.instanceId),
            stopId = trip.key.stopId,
            stopName = trip.stopName,
            title = title(trip),
            text = when {
                outcome is TrackingOutcome.Canceled -> context.getString(R.string.trip_tracking_canceled)
                arriving -> context.getString(R.string.trip_tracking_arriving_now)
                minutes == null -> context.getString(R.string.trip_tracking_pending)
                waiting.predicted ->
                    context.resources
                        .getQuantityString(R.plurals.trip_tracking_arrives_in, minutes, minutes)
                else ->
                    context.resources
                        .getQuantityString(R.plurals.trip_tracking_scheduled_in, minutes, minutes)
            },
            shortText = when {
                outcome is TrackingOutcome.Canceled -> null
                arriving -> context.getString(R.string.trip_tracking_short_now)
                minutes == null -> null
                else -> context.getString(R.string.trip_tracking_short_eta, minutes)
            },
            colorRes = match?.fillColorRes ?: R.color.theme_primary,
            progress = trackingProgress(trip, outcome),
            progressMax = trackingProgressMax(trip),
            // Indeterminate only while nothing is known. Once the bus is here or the trip is off, a
            // full bar reads as done, where a spinner would read as "still working on it".
            indeterminate = outcome is TrackingOutcome.Pending,
            primary = rank == 0,
            sortKey = sortKey(rank)
        )
    }

    /** Ranks cards most-recently-tracked first — sort keys order lexicographically, ascending. */
    private fun sortKey(rank: Int): String = rank.toString().padStart(2, '0')
}
