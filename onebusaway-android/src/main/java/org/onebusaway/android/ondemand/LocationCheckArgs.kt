package org.onebusaway.android.ondemand

import org.onebusaway.android.util.geoPointOrNull

private const val ROUTE_RIDER = "rider"
private const val ROUTE_CENTER = "center"
private const val ROUTE_POINT = "point"

/** The probe source as a navigation argument; the point's label is not carried (the page never shows it). */
fun ProbeSource.toRouteValue(): String = when (this) {
    ProbeSource.Rider -> ROUTE_RIDER
    ProbeSource.MapCenter -> ROUTE_CENTER
    is ProbeSource.Point -> ROUTE_POINT
}

fun probeSourceFromRoute(value: String?): ProbeSource? = when (value) {
    ROUTE_RIDER -> ProbeSource.Rider
    ROUTE_CENTER -> ProbeSource.MapCenter
    ROUTE_POINT -> ProbeSource.Point(null)
    else -> null
}

/** The detail page's location check from its optional route arguments; null unless both facts arrived. */
fun locationCheckFromArgs(inside: String?, source: String?, locality: String?, lat: String?, lon: String?): LocationCheck? {
    val isInside = inside?.toBooleanStrictOrNull() ?: return null
    val probeSource = probeSourceFromRoute(source) ?: return null
    val point = geoPointOrNull(lat?.toDoubleOrNull(), lon?.toDoubleOrNull())
    return LocationCheck(probeSource, isInside, locality?.takeIf { it.isNotBlank() }, point)
}
