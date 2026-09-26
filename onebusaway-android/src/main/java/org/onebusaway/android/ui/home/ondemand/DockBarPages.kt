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
package org.onebusaway.android.ui.home.ondemand

import java.time.Instant
import java.util.Locale
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.TextSpec
import org.onebusaway.android.ondemand.barTitle
import org.onebusaway.android.ondemand.readableTextColor
import org.onebusaway.android.util.GeoPoint

/** What the bar's trailing button does on a page (spec §3.4 states). */
enum class TrailingAction { PHONE, URL, CHEVRON_EDGE, CHEVRON_DETAIL, NONE }

/** The first rule's pickup booking rule's contact, which the card and bar act on (spec §3.3). */
data class ServiceContact(val phone: String?, val url: String?)

fun contactOf(service: OnDemandService): ServiceContact {
    val rule = service.rules.firstOrNull()?.let(service::pickupBookingRule)
    return ServiceContact(rule?.phoneNumber, rule?.bookingUrl)
}

/** One page of the docked bar, fully decided so the composable only lays it out. */
data class DockBarPage(
    val match: OnDemandMatch,
    val color: Int,
    val textColor: Int,
    /**
     * The thumbnail's ring colour: the service's own resolved colour always, even outside where
     * [color] (the bar's background) turns gray (spec §3.4's "the same drawing" for the outside state).
     */
    val thumbnailColor: Int,
    /** Null when there is nothing to say: the service name then takes the title position. */
    val title: TextSpec?,
    val trailing: TrailingAction,
    val contact: ServiceContact,
    /** Where the outside chevron pans: the server's boundary point, else the client edge. */
    val edgePoint: GeoPoint?
)

private const val OPAQUE_ALPHA = 0xFF000000.toInt()

/** One page per stack entry, in the stack's order (the dock decision already filtered and sorted it). */
fun dockBarPages(state: OnDemandDockState.Bar, edges: Map<String, ZoneEdge>, colors: Map<String, Int>, now: Instant, locale: Locale, metric: Boolean): List<DockBarPage> = state.matches.map { match ->
    val edge = edges[match.service.id]
    val contact = contactOf(match.service)
    val edgePoint = match.nearestPointOnBoundary ?: edge?.point
    val resolved = colors[match.service.id] ?: ONDEMAND_OUTSIDE_GRAY
    val color = if (match.isInside) resolved else ONDEMAND_OUTSIDE_GRAY
    // A collision can swap the route colour for a palette one, and the route's text colour was chosen for
    // the route colour only.
    val onRouteColor = match.isInside && match.service.routeColor?.let { (it or OPAQUE_ALPHA) == resolved } == true
    DockBarPage(
        match = match,
        color = color,
        // The outside gray always reads white; a route's own text colour applies only over its own colour.
        textColor = readableTextColor(color, preferred = match.service.routeTextColor.takeIf { onRouteColor }),
        thumbnailColor = resolved,
        title = barTitle(match, state.probe.point, edge, now, locale, metric),
        trailing = trailingAction(match, contact, edgePoint),
        contact = contact,
        edgePoint = edgePoint
    )
}

fun trailingAction(match: OnDemandMatch, contact: ServiceContact, edgePoint: GeoPoint?): TrailingAction = when {
    !match.isInside -> if (edgePoint != null) TrailingAction.CHEVRON_EDGE else TrailingAction.NONE
    contact.phone != null -> TrailingAction.PHONE
    contact.url != null -> TrailingAction.URL
    else -> TrailingAction.CHEVRON_DETAIL
}

/** The card's primary pill: "Call to Book" with a phone, else "Book online" with a URL, else none. */
sealed interface CardPrimary {
    data class Call(val phone: String) : CardPrimary
    data class Online(val url: String) : CardPrimary
}

fun cardPrimary(service: OnDemandService): CardPrimary? {
    val contact = contactOf(service)
    return contact.phone?.let { CardPrimary.Call(it) } ?: contact.url?.let { CardPrimary.Online(it) }
}

fun showsBadge(pageCount: Int): Boolean = pageCount > 1
