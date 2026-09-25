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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ZonePolygon
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/**
 * The on-demand zone overlay (GTFS-Flex): a cold driver that loads the flex service areas covering
 * the settled viewport whenever the layer preference is on, and publishes them as
 * [org.onebusaway.android.map.render.MapRenderSnapshot.onDemandZones]. Mirrors [RentalLayerController]:
 * [start] launches the loader for a view, [stop] cancels it, [hide] additionally clears the map.
 *
 * Takes the settled-camera flow and the render state rather than the whole [MapHost] so it is
 * JVM-constructible; [MapViewModel] hands it `mapHost.settledCamera()` and `mapHost.renderState`.
 *
 * Whether the deployment serves the namespace at all is discovered here: the viewport query is the
 * probe, and an [OnDemandResult.Unsupported] answer is recorded in [OnDemandSupport] (keyed by the OBA
 * base URL, like the transit-centre drawer) so this process never asks that server again.
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
    private val scope: CoroutineScope
) {

    private var loadJob: Job? = null

    // The last response and the viewport + deployment that produced it; confined to the loader
    // coroutine, so no synchronization.
    private var cachedViewport: CameraSnapshot? = null
    private var cachedDeployment: String? = null
    private var cachedZones: List<ZonePolygon>? = null

    /** (Re)start the loader for the current view. */
    fun start() {
        loadJob?.cancel()
        loadJob = scope.launch {
            combine(
                settledCamera,
                prefsRepository.observeBoolean(R.string.preference_key_show_ondemand_zones, true),
                // The deployment is an input, not a fact read once: a region switch changes who answers.
                // The demo transit system has no flex data, so demo mode reads as "no deployment".
                combine(regionRepository.region.map { it?.obaBaseUrl }.distinctUntilChanged(), demoMode.active) { url, demo -> if (demo) null else url }
            ) { camera, enabled, deployment -> Triple(camera, enabled, deployment) }
                // A newer viewport cancels an in-flight load.
                .collectLatest { (camera, enabled, deployment) ->
                    if (!enabled || deployment == null || support.isKnownUnsupported(deployment)) {
                        clearZones()
                        return@collectLatest
                    }
                    zonesFor(camera, deployment)?.let(renderState::setOnDemandZones)
                }
        }
    }

    /** Stop the loader, dropping the cache so the next [start] can't redraw another server's zones. */
    fun stop() {
        loadJob?.cancel()
        loadJob = null
        cachedViewport = null
        cachedDeployment = null
        cachedZones = null
    }

    /** Leave the map with no zones on it and the loader off. */
    fun hide() {
        stop()
        clearZones()
    }

    /**
     * The zones for [camera] on [deployment]: from cache when unchanged, else fetched. Null when there
     * is nothing new to draw — a transient failure keeps whatever is on screen (a pan must not blank
     * the layer), and an unsupported answer has already cleared it.
     */
    private suspend fun zonesFor(camera: CameraSnapshot, deployment: String): List<ZonePolygon>? {
        cachedZones?.takeIf { cachedViewport == camera && cachedDeployment == deployment }?.let { return it }
        return when (val result = dataSource.servicesForViewport(camera)) {
            is OnDemandResult.Loaded -> zonePolygons(result.value).also {
                cachedViewport = camera
                cachedDeployment = deployment
                cachedZones = it
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

/** One [ZonePolygon] per polygon of every area of every service, in the route's colour. */
internal fun zonePolygons(services: List<OnDemandService>): List<ZonePolygon> = services.flatMap { service ->
    service.areas.flatMap { area ->
        area.polygons.map { rings -> ZonePolygon(service.id, service.name, rings, service.routeColor) }
    }
}
