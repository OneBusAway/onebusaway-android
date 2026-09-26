package org.onebusaway.android.ondemand

import org.onebusaway.android.util.GeoPoint

/** Where a probe's point came from (spec §2.1, §2.8): it decides the copy every surface reads. */
sealed interface ProbeSource {
    /** The rider's own location. */
    data object Rider : ProbeSource

    /** The map centre, used when there is no authorised fix. */
    data object MapCenter : ProbeSource

    /** A place the rider chose — a dropped pin or a planner endpoint; [label] names it when known. */
    data class Point(val label: String?) : ProbeSource
}

data class ProbePoint(val point: GeoPoint, val source: ProbeSource)

/**
 * What the zone detail page says about the probe that opened it (spec §3.6 item 3). [point] is the
 * probe coordinate, drawn on the page's thumbnail when it lies inside the zone's box; null from a
 * caller that has no coordinate to offer.
 */
data class LocationCheck(val source: ProbeSource, val isInside: Boolean, val locality: String?, val point: GeoPoint? = null)
