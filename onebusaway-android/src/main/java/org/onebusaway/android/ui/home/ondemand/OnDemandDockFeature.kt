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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import java.time.Instant
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ui.compose.unitsAreMetric
import org.onebusaway.android.util.GeoPoint

/** What the dock's surfaces can do; the host binds these to the map view model, the dialer and navigation. */
data class OnDemandDockActions(
    val openDetail: (OnDemandMatch, ProbePoint) -> Unit,
    /** [nearby] is true for the bar's long-press list (every match), false for the card's inside-only list. */
    val openPicker: (matches: List<OnDemandMatch>, probe: ProbePoint, nearby: Boolean) -> Unit,
    val call: (String) -> Unit,
    val openUrl: (String) -> Unit,
    val zoomOut: (List<OnDemandMatch>, GeoPoint) -> Unit,
    val panTo: (GeoPoint) -> Unit,
    val highlight: (String?) -> Unit
)

private const val CROSSFADE_MS = 200

/**
 * The dock slot (spec §2.4): exactly one of the zone card, the docked bar or nothing, cross-fading in
 * 200 ms. [allMatches] is the whole last probe, for the bar's long-press picker.
 */
// Transition.Crossfade is the only Crossfade overload with a contentKey.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnDemandDockFeature(
    state: OnDemandDockState,
    allMatches: List<OnDemandMatch>,
    edges: Map<String, ZoneEdge>,
    geometry: Map<String, List<ServiceArea>>,
    colors: Map<String, Int>,
    now: Instant,
    actions: OnDemandDockActions,
    modifier: Modifier = Modifier
) {
    val locale = LocalLocale.current.platformLocale
    val metric = unitsAreMetric()
    // Fade between card, bar and nothing only: a reprobe's new Bar recomposes in place and keeps the pager page.
    updateTransition(targetState = state, label = "onDemandDock").Crossfade(
        modifier = modifier,
        animationSpec = tween(CROSSFADE_MS),
        contentKey = { it::class }
    ) { shown ->
        when (shown) {
            OnDemandDockState.Hidden -> Unit
            is OnDemandDockState.Card -> {
                val first = shown.matches.first()
                OnDemandZoneCard(
                    match = first,
                    moreCount = shown.matches.size - 1,
                    color = colors[first.service.id] ?: ONDEMAND_OUTSIDE_GRAY,
                    now = now,
                    onOpenDetail = { actions.openDetail(first, shown.probe) },
                    onOpenPicker = { actions.openPicker(shown.matches, shown.probe, false) },
                    onCall = actions.call,
                    onOpenUrl = actions.openUrl
                )
            }
            is OnDemandDockState.Bar -> OnDemandDockBar(
                pages = dockBarPages(shown, edges, colors, now, locale, metric),
                probe = shown.probe,
                geometry = geometry,
                onPageShown = { actions.highlight(it.match.service.id) },
                onOpenDetail = { actions.openDetail(it.match, shown.probe) },
                onOpenPicker = { actions.openPicker(allMatches, shown.probe, true) },
                onCall = actions.call,
                onOpenUrl = actions.openUrl,
                onZoomOut = { actions.zoomOut(shown.matches, shown.probe.point) },
                onPanTo = actions.panTo
            )
        }
    }
}
