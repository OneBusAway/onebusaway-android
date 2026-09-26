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

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.ondemand.LocationCheck
import org.onebusaway.android.ondemand.resolveServiceColors
import org.onebusaway.android.ui.home.directions.OnDemandPlannerFallbackSheet
import org.onebusaway.android.util.ExternalIntents

/** Spec §2.4: 16 dp side gutters, 12 dp above the sheet edge, 360 dp maximum in landscape. */
private val DOCK_GUTTER = 16.dp
private val DOCK_SHEET_GAP = 12.dp
private val DOCK_LANDSCAPE_MAX_WIDTH = 360.dp

/**
 * The dock slot on the home map: the zone card or the docked bar from [MapViewModel.onDemandDock],
 * bottom-centre above the sheet edge (bottom-start, 360 dp wide, in landscape), reporting its
 * measured height through [onHeight] so the FAB stack lifts clear of it. Also hosts the overlap
 * picker the card footer and the bar long-press open.
 */
@Composable
fun BoxScope.OnDemandDockOverlay(
    mapViewModel: MapViewModel,
    sheetsViewModel: OnDemandSheetsViewModel,
    bottomInset: Dp,
    onOpenDetail: (serviceId: String, check: LocationCheck) -> Unit,
    onHeight: (Int) -> Unit
) {
    val context = LocalContext.current
    val state by mapViewModel.onDemandDock.collectAsStateWithLifecycle()
    val probeResult by mapViewModel.onDemandProbeResult.collectAsStateWithLifecycle()
    val edges by mapViewModel.onDemandEdges.collectAsStateWithLifecycle()
    val geometry by mapViewModel.onDemandGeometry.collectAsStateWithLifecycle()
    val colors by mapViewModel.onDemandColors.collectAsStateWithLifecycle()
    val picker by sheetsViewModel.picker.collectAsStateWithLifecycle()
    val planner by sheetsViewModel.planner.collectAsStateWithLifecycle()
    val windowSize = LocalWindowInfo.current.containerSize
    val landscape = windowSize.width > windowSize.height
    // Spec §2.3: the highlight a picker row or a bar page set clears when the detail page closes — on
    // Android that is this screen resuming; the next swipe re-highlights its page. Clearing it when the
    // dock leaves the bar state (hidden, card, level change) is MapViewModel's job (its dockState
    // collector already does it for the life of the ViewModel) — not this composable's.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { mapViewModel.highlightOnDemandService(null) }
    val actions = remember(mapViewModel, sheetsViewModel, context) {
        OnDemandDockActions(
            openDetail = { match, probe -> onOpenDetail(match.service.id, LocationCheck(probe.source, match.isInside, locality = null, point = probe.point)) },
            openPicker = sheetsViewModel::openPicker,
            // ACTION_DIAL never places the call itself; the rider confirms in the dialer.
            call = { phone -> ExternalIntents.goToPhoneDialer(context, phone) },
            openUrl = { url -> ExternalIntents.goToUrl(context, url) },
            zoomOut = mapViewModel::zoomOutToOnDemandZones,
            panTo = mapViewModel::panToOnDemandEdge,
            highlight = mapViewModel::highlightOnDemandService
        )
    }
    // Measured through a wrapper that is always here: a hidden dock composes nothing, and a modifier
    // on nothing never reports the shrink.
    OnDemandDockFeature(
        state = state,
        allMatches = probeResult?.matches ?: emptyList(),
        edges = edges,
        geometry = geometry,
        colors = colors,
        now = sheetsViewModel.now(),
        actions = actions,
        modifier = Modifier
            .align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter)
            .then(if (landscape) Modifier.widthIn(max = DOCK_LANDSCAPE_MAX_WIDTH) else Modifier.fillMaxWidth())
            .padding(start = DOCK_GUTTER, end = DOCK_GUTTER, bottom = bottomInset + DOCK_SHEET_GAP)
            .onSizeChanged { onHeight(if (state == OnDemandDockState.Hidden) 0 else it.height) }
    )
    picker?.let { request ->
        OnDemandPickerSheet(
            request = request,
            colors = colors,
            now = sheetsViewModel.now(),
            onSelect = { match ->
                mapViewModel.highlightOnDemandService(match.service.id)
                sheetsViewModel.closePicker()
                onOpenDetail(match.service.id, LocationCheck(request.probe.source, match.isInside, request.locality, request.probe.point))
            },
            onDismiss = {
                mapViewModel.highlightOnDemandService(null)
                sheetsViewModel.closePicker()
            }
        )
    }
    planner?.let { state ->
        val brand = colorResource(R.color.brand_color).toArgb()
        val services = (state as? PlannerFallbackState.Ready)?.qualification?.qualifying?.map { it.service } ?: emptyList()
        OnDemandPlannerFallbackSheet(
            state = state,
            colors = remember(services, brand) { resolveServiceColors(services, brand) },
            now = sheetsViewModel.now(),
            onOpenDetail = { match, probe ->
                sheetsViewModel.closePlanner()
                onOpenDetail(match.service.id, LocationCheck(probe.source, match.isInside, locality = null, point = probe.point))
            },
            onCall = actions.call,
            onOpenUrl = actions.openUrl,
            onShowAll = { matches, probe ->
                sheetsViewModel.closePlanner()
                sheetsViewModel.openPicker(matches, probe, nearby = false)
            },
            onDismiss = sheetsViewModel::closePlanner
        )
    }
}
