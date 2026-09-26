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
package org.onebusaway.android.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.api.net.obaEndpoint
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ZonePolygon
import org.onebusaway.android.map.render.ZoneStyle
import org.onebusaway.android.map.render.largestPolygon
import org.onebusaway.android.map.render.pinPointFor
import org.onebusaway.android.map.rental.visibleHeightMeters
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.resolveServiceColors
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/**
 * The on-demand zone overlay (GTFS-Flex): a cold driver that loads the flex service areas covering
 * the settled viewport whenever the layer preference is on and the viewport is inside the zoom gate
 * ([isWithinOnDemandZoomGate]), and publishes them as
 * [org.onebusaway.android.map.render.MapRenderSnapshot.onDemandZones]. Mirrors [RentalLayerController]:
 * [start] launches the loader for a view, [stop] cancels it, [hide] additionally clears the map.
 *
 * Takes the settled-camera flow and the render state rather than the whole [MapHost] so it is
 * JVM-constructible; [MapViewModel] hands it `mapHost.settledCamera()` and `mapHost.renderState`.
 *
 * Whether the deployment serves the namespace at all is discovered here: the viewport query is the
 * probe, and an [OnDemandResult.Unsupported] answer is recorded in [OnDemandSupport] (keyed by the
 * endpoint the requests go to — a custom API URL ahead of the region's) so this process never asks
 * that server again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandLayerController(
    private val settledCamera: Flow<CameraSnapshot>,
    private val renderState: MapRenderState,
    private val dataSource: OnDemandDataSource,
    private val support: OnDemandSupport,
    private val prefsRepository: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val demoMode: DemoModeState,
    private val brandColor: Int,
    private val scope: CoroutineScope
) {

    private var loadJob: Job? = null

    private val _zoomLevel = MutableStateFlow(OnDemandZoomLevel.HIDDEN)

    /** Spec §2.2's level for the settled camera, published so the layer, the dock and tests read one answer. */
    val zoomLevel: StateFlow<OnDemandZoomLevel> = _zoomLevel.asStateFlow()

    /** The one service drawn highlighted (spec §2.3), set by the picker row and the bar page; null for none. */
    val highlightedServiceId = MutableStateFlow<String?>(null)

    // The last response and the viewport + deployment that produced it; confined to the loader
    // coroutine, so no synchronization. Services, not polygons: a level or highlight change restyles
    // without asking again.
    private var cachedViewport: CameraSnapshot? = null
    private var cachedDeployment: String? = null
    private var cachedServices: List<OnDemandService>? = null

    /** (Re)start the loader for the current view. */
    fun start() {
        loadJob?.cancel()
        loadJob = scope.launch {
            combine(
                settledCamera,
                prefsRepository.observeBoolean(R.string.preference_key_show_ondemand_zones, true),
                onDemandDeployment(regionRepository, prefsRepository, demoMode),
                highlightedServiceId
            ) { camera, enabled, deployment, highlighted -> Inputs(camera, enabled, deployment, highlighted) }
                // A newer viewport cancels an in-flight load.
                .collectLatest { (camera, enabled, deployment, highlighted) ->
                    val level = onDemandZoomLevel(camera.latSpan)
                    _zoomLevel.value = level
                    if (!enabled || deployment == null || support.isKnownUnsupported(deployment)) {
                        clearZones()
                        return@collectLatest
                    }
                    // The gate comes before the request, as the rental layer's does: a state-wide
                    // view costs no round trip and shows no zones, and the cache survives it so
                    // zooming back in to the same view redraws without asking again.
                    if (level == OnDemandZoomLevel.HIDDEN) {
                        clearZones()
                        return@collectLatest
                    }
                    servicesFor(camera, deployment)?.let { renderState.setOnDemandZones(zonePolygons(it, level, highlighted, brandColor)) }
                }
        }
    }

    private data class Inputs(val camera: CameraSnapshot, val enabled: Boolean, val deployment: String?, val highlighted: String?)

    /** Stop the loader, dropping the cache so the next [start] can't redraw another server's zones. */
    fun stop() {
        loadJob?.cancel()
        loadJob = null
        dropCache()
    }

    /** Leave the map with no zones on it and the loader off. */
    fun hide() {
        stop()
        clearZones()
    }

    private fun dropCache() {
        cachedViewport = null
        cachedDeployment = null
        cachedServices = null
    }

    /**
     * The services for [camera] on [deployment]: from cache when unchanged, else fetched. Null when
     * there is nothing new to draw — a transient failure keeps whatever is on screen (a pan must not
     * blank the layer), and an unsupported answer has already cleared it. The one exception is a
     * failure right after the deployment changed: what is on screen is then another server's zones,
     * so they come off before that server is asked rather than outliving a fetch of it that fails.
     */
    private suspend fun servicesFor(camera: CameraSnapshot, deployment: String): List<OnDemandService>? {
        cachedServices?.takeIf { cachedViewport == camera && cachedDeployment == deployment }?.let { return it }
        if (cachedDeployment != null && cachedDeployment != deployment) {
            dropCache()
            clearZones()
        }
        return when (val result = dataSource.servicesForViewport(camera)) {
            is OnDemandResult.Loaded -> result.value.also {
                cachedViewport = camera
                cachedDeployment = deployment
                cachedServices = it
            }
            OnDemandResult.Unsupported -> {
                support.recordAbsent(deployment)
                clearZones()
                null
            }
            is OnDemandResult.Failed -> null
        }
    }

    private fun clearZones() = renderState.clearOnDemandZones()
}

/**
 * Who answers the on-demand queries, as an input rather than a fact read once: a region switch or a
 * custom API URL changes it. Keyed exactly as the requests are routed ([obaEndpoint]), so a custom URL
 * with no region still loads zones and a 404 is recorded against the server that sent it. The demo
 * transit system has no flex data, so demo mode alone reads as "no deployment". Shared by the layer
 * and the probe controller so both key [OnDemandSupport] the same way.
 */
internal fun onDemandDeployment(regionRepository: RegionRepository, prefsRepository: PreferencesRepository, demoMode: DemoModeState): Flow<String?> = combine(
    regionRepository.region,
    prefsRepository.observeString(R.string.preference_key_oba_api_url, null),
    demoMode.active
) { region, customApiUrl, demo -> if (demo) null else obaEndpoint(customApiUrl, region) }
    .distinctUntilChanged()

/**
 * Whether [deployment] may serve `/api/ondemand`: unknown counts as supported until [absent] names it
 * (spec §2.10). Combined on [OnDemandSupport.absent] rather than a one-shot
 * [OnDemandSupport.isKnownUnsupported] check so a 404 recorded after this flow already emitted still
 * turns it false — a fallback surface built on this (e.g. the trip planner) hides on the very probe
 * that finds the deployment unsupported, not just on the next collector restart. Factored out of
 * [MapViewModel] so it is JVM-testable without constructing one.
 */
internal fun onDemandSupportedFlow(deployment: Flow<String?>, absent: Flow<Set<String>>): Flow<Boolean> = combine(deployment, absent) { url, absentDeployments -> url != null && url !in absentDeployments }

/**
 * The tallest viewport the zone layer draws for, in metres of north-south extent.
 *
 * The sibling iOS app hides this layer above a visible-rect height of 600,000 Mercator map points,
 * which spans about 60 to 70 km of latitude across the mid-latitudes the app serves: a county-sized
 * zone stays drawn with the whole county on screen, and a state-wide view draws nothing.
 */
const val ONDEMAND_MAX_VISIBLE_HEIGHT_METERS = 65_000.0

/** Whether the viewport is tight enough to fetch and draw zones for. */
fun isWithinOnDemandZoomGate(latSpan: Double): Boolean = visibleHeightMeters(latSpan) <= ONDEMAND_MAX_VISIBLE_HEIGHT_METERS

/**
 * One [ZonePolygon] per polygon of every area of every service, styled for [level] and the
 * highlight, in the service's resolved colour; at region level the service's largest polygon carries
 * the pin's label point (spec §3.1).
 */
internal fun zonePolygons(services: List<OnDemandService>, level: OnDemandZoomLevel, highlightedServiceId: String?, brandColor: Int): List<ZonePolygon> {
    val colors = resolveServiceColors(services, brandColor)
    return services.flatMap { service ->
        val highlighted = service.id == highlightedServiceId
        val style = when (level) {
            OnDemandZoomLevel.HIDDEN, OnDemandZoomLevel.REGION -> if (highlighted) ZoneStyle.REGION_HIGHLIGHTED else ZoneStyle.REGION
            OnDemandZoomLevel.STREET -> when {
                highlighted -> ZoneStyle.STREET_HIGHLIGHTED
                highlightedServiceId != null -> ZoneStyle.STREET_DIMMED
                else -> ZoneStyle.STREET
            }
        }
        // The pin sits on the largest polygon only; identity is enough because `pinned` is one of these
        // rings. Its coordinate comes from Task 4's pinPointFor, which picks the same polygon internally.
        val pinned = if (level == OnDemandZoomLevel.REGION) largestPolygon(service.areas) else null
        val pinPoint = if (pinned != null) pinPointFor(service.areas) else null
        service.areas.flatMap { area ->
            area.polygons.map { rings ->
                val labelPoint = if (rings === pinned) pinPoint else null
                ZonePolygon(service.id, service.name, rings, colors[service.id], labelPoint, style)
            }
        }
    }
}
