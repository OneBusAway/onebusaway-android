package org.onebusaway.android.ondemand

import java.text.Collator
import java.time.Instant
import java.util.Locale
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.util.GeoPoint

/** Beyond this the docked bar and address line say nothing rather than nag (spec §2.4, §3.7). */
const val ONDEMAND_NEARBY_MAX_METERS = 5_000.0

/**
 * One service in a probe result (spec §2.1): the server's reason, the minimum distance over the
 * service's areas with that area's boundary point, and the availability computed at probe time.
 */
data class OnDemandMatch(
    val service: OnDemandService,
    val matchReason: OnDemandMatchReason?,
    val distanceToAreaMeters: Double?,
    val nearestPointOnBoundary: GeoPoint?,
    val availability: OnDemandAvailability
) {
    val isInside: Boolean get() = matchReason == OnDemandMatchReason.AREA_CONTAINS_POINT

    /** Outside, with a finite distance the dock is allowed to show. A pure stop group (null) never is. */
    val isNearby: Boolean get() = !isInside && distanceToAreaMeters != null && distanceToAreaMeters <= ONDEMAND_NEARBY_MAX_METERS
}

fun matchFor(service: OnDemandService, now: Instant): OnDemandMatch {
    val nearest = service.areas
        .mapNotNull { area -> area.distanceToAreaMeters?.let { it to area } }
        .minByOrNull { it.first }
    return OnDemandMatch(
        service = service,
        matchReason = service.matchReason,
        distanceToAreaMeters = nearest?.first,
        nearestPointOnBoundary = nearest?.second?.nearestPointOnBoundary,
        availability = computeAvailability(service, now)
    )
}

/** Spec §2.6: tier, then distance (inside first, null last), then the name in natural order. */
fun List<OnDemandMatch>.sortedSoonestUsable(locale: Locale = Locale.getDefault()): List<OnDemandMatch> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return sortedWith(
        compareBy<OnDemandMatch> { it.availability.usabilityTier }
            .thenBy(nullsLast()) { it.distanceToAreaMeters }
            .thenComparator { a, b -> naturalCompare(a.service.name, b.service.name, collator) }
    )
}

/**
 * Locale-aware comparison that orders digit runs by value ("Route 9" before "Route 10"), which a
 * plain [Collator] does not. Public because the picker and the layers sheet sort names too.
 */
fun naturalCompare(a: String, b: String, collator: Collator): Int {
    val left = chunk(a)
    val right = chunk(b)
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val cmp = if (x.first().isDigit() && y.first().isDigit()) {
            x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
        } else {
            collator.compare(x, y)
        }
        if (cmp != 0) return cmp
    }
    return left.size.compareTo(right.size)
}

private fun chunk(text: String): List<String> {
    val chunks = mutableListOf<String>()
    var current = StringBuilder()
    for (ch in text) {
        if (current.isNotEmpty() && current.last().isDigit() != ch.isDigit()) {
            chunks += current.toString()
            current = StringBuilder()
        }
        current.append(ch)
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}
