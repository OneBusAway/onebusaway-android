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
package org.onebusaway.android.ui.home.nearby

import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.arrivals.groupByRouteDirectionAndStop
import org.onebusaway.android.ui.arrivals.routeRowKey
import org.onebusaway.android.util.GeoPoint

/**
 * The bay a [NearbyRouteRow] departs from. Resolved from the response's stop references, and carried
 * on the row rather than looked up at draw time so a row can always name where to stand.
 *
 * [code] is what is painted on the pole and [direction] the compass bearing the stop serves; at a real
 * transit centre those two, not a "Bay C", are what tell one side of an intersection from the other
 * ("Pine St & 4th Ave" southbound vs northbound). [point] is what a row tap recentres on.
 */
data class NearbyBay(
    val id: String,
    val name: String,
    val code: String?,
    val direction: String?,
    val point: GeoPoint
) {
    /**
     * The deterministic tiebreak between two bays serving the same route and direction. The stop code
     * when the feed publishes one — it is both the rider-facing identifier and the one that sorts
     * meaningfully between adjacent bays — else the name.
     */
    val sortKey: String get() = code?.takeIf(String::isNotBlank) ?: name
}

/**
 * One row of the transit-centre drawer (#2107): every upcoming trip for a single (route, direction) at
 * a single bay, ETA-sorted, plus the bay it leaves from.
 *
 * The stop-scoped drawer's row is the [group] alone, because there the stop is the screen's subject.
 * Here the list spans a whole viewport, so the bay is half the answer — which is why it is part of the
 * row's identity as well as its display (see [groupByRouteDirectionAndStop]).
 */
data class NearbyRouteRow(val group: RouteRowGroup, val bay: NearbyBay) {

    /** A stable LazyColumn key: the stop-scoped sibling of [RouteRowGroup.key]. */
    val key: String get() = "${bay.id}\u0000${routeRowKey(group.routeId, group.directionId, group.headsign)}"
}

/**
 * Builds the drawer's rows from one viewport's [arrivals].
 *
 * [bayOf] resolves an arrival's stop from the response references; an arrival whose bay the references
 * don't carry is **dropped rather than shown bay-less** — a row in this list that can't say where to
 * stand answers nothing, and the per-stop drawer is the surface for "arrivals at a stop you already
 * chose". [agencyNameOf] feeds the shared (agency, line, headsign) ordering.
 *
 * Pure: [ArrivalInfo]s are built by the caller (they need a `Context`), so the grouping stays
 * JVM-testable — the same split [groupByRouteDirection] uses.
 */
fun nearbyRouteRows(
    arrivals: List<ArrivalInfo>,
    bayOf: (stopId: String) -> NearbyBay?,
    agencyNameOf: (ArrivalInfo) -> String?
): List<NearbyRouteRow> {
    val bays = HashMap<String, NearbyBay?>()
    fun bay(stopId: String) = bays.getOrPut(stopId) { bayOf(stopId) }
    return groupByRouteDirectionAndStop(
        items = arrivals.filter { bay(it.stopId) != null },
        agencyNameOf = agencyNameOf,
        stopIdOf = { it.stopId },
        stopSortKeyOf = { bay(it.stopId)?.sortKey }
    ).mapNotNull { trips ->
        val resolved = bay(trips.first().stopId) ?: return@mapNotNull null
        NearbyRouteRow(RouteRowGroup(trips), resolved)
    }
}
