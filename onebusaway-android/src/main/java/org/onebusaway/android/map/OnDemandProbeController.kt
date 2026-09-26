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

import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.api.data.ONDEMAND_PROBE_RADIUS_METERS
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.map.render.nearestBoundaryPoint
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.ondemand.OnDemandGeometryCache
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

/** A device fix the controller can reason about without `android.location.Location`. */
data class RiderFix(val point: GeoPoint, val accuracyMeters: Float?)

/** Spec §2.1: a probe point has to move this far before the map or the rider triggers a new probe. */
const val ONDEMAND_PROBE_MOVE_METERS = 100.0

/** A fix less accurate than this is ignored for the rider trigger, so GPS drift can't flip the copy. */
const val ONDEMAND_FIX_MAX_ACCURACY_METERS = 100f

private const val CACHE_LIFETIME_MS = 10 * 60_000L
private const val RIDER_CACHE_DECIMALS = 3
private const val EXACT_CACHE_DECIMALS = 5
private const val RECOMPUTE_GRACE_MS = 1_000L

/**
 * The one probe controller of spec §2.1: decides the probe point (the rider's fix when there is an
 * authorised one, else the map centre), runs a point probe when a trigger fires, caches responses
 * per rounded point for ten minutes, and publishes [dockState], [edges] and [geometry] for the dock.
 * [probeExact] serves the address check and the planner from a separate exact-coordinate cache,
 * and (spec §2.4) does not depend on the layer toggle or on [start]/[stop]: it answers a question
 * the rider asked.
 *
 * JVM-constructible: every input is a flow or an interface, and `now` comes from [timeProvider].
 *
 * [scope] must be single-threaded (Main); call [probeExact] from it.
 */
class OnDemandProbeController(
    private val settledCamera: Flow<CameraSnapshot>,
    riderFixes: Flow<RiderFix?>,
    layerEnabled: Flow<Boolean>,
    private val deployment: Flow<String?>,
    private val dataSource: OnDemandDataSource,
    private val support: OnDemandSupport,
    private val geometryCache: OnDemandGeometryCache,
    private val timeProvider: TimeProvider,
    private val scope: CoroutineScope
) {
    private val _result = MutableStateFlow<OnDemandProbeResult?>(null)
    val result: StateFlow<OnDemandProbeResult?> = _result.asStateFlow()

    private val _zoomLevel = MutableStateFlow(OnDemandZoomLevel.HIDDEN)
    val zoomLevel: StateFlow<OnDemandZoomLevel> = _zoomLevel.asStateFlow()

    private val _edges = MutableStateFlow<Map<String, ZoneEdge>>(emptyMap())

    /** The nearest boundary per matched service id, from full geometry; absent until it has loaded. */
    val edges: StateFlow<Map<String, ZoneEdge>> = _edges.asStateFlow()

    private val _geometry = MutableStateFlow<Map<String, List<ServiceArea>>>(emptyMap())

    /** Full-geometry areas per matched service id (the thumbnail's rings); absent until loaded. */
    val geometry: StateFlow<Map<String, List<ServiceArea>>> = _geometry.asStateFlow()

    private val suppressed = MutableStateFlow(false)
    private val enabled = layerEnabled.stateIn(scope, SharingStarted.Eagerly, true)

    // The deployment [probeExact] asks, tracked apart from the dock pipeline's [currentDeployment]
    // so the layer toggle and [stop] never take the address check and the planner down with the dock.
    private val latestDeployment = deployment.stateIn(scope, SharingStarted.Eagerly, null)

    val dockState: StateFlow<OnDemandDockState> = combine(_result, _zoomLevel, suppressed, enabled) { result, level, hidden, on ->
        dockStateFor(result, level, visible = !hidden && on)
    }.stateIn(scope, SharingStarted.Eagerly, OnDemandDockState.Hidden)

    // The last usable rider point: an inaccurate fix keeps the previous one, a null fix (permission
    // gone) clears it so the probe source falls back to the map centre.
    private val riderPoint: Flow<GeoPoint?> = riderFixes
        .scan(null as GeoPoint?) { last, fix ->
            when {
                fix == null -> null
                fix.accuracyMeters != null && fix.accuracyMeters > ONDEMAND_FIX_MAX_ACCURACY_METERS -> last
                else -> fix.point
            }
        }
        .distinctUntilChanged()

    private val riderCache = ProbeCache(RIDER_CACHE_DECIMALS)
    private val exactCache = ProbeCache(EXACT_CACHE_DECIMALS)

    private var inputJob: Job? = null
    private var probeJob: Job? = null
    private var recomputeJob: Job? = null
    private var geometryJob: Job? = null

    // Confined to the input collector and the probe job, which never run concurrently for one point.
    private var currentDeployment: String? = null

    /** The point moves are measured from and trigger 3 re-probes; kept across a failed probe. */
    private var lastProbe: ProbePoint? = null

    /** The last probe failed, so the next trigger probes whatever the distance (§2.1 "retries"). */
    private var retryPending = false

    private data class Inputs(val camera: CameraSnapshot, val rider: GeoPoint?, val deployment: String?, val enabled: Boolean)

    fun start() {
        inputJob?.cancel()
        inputJob = scope.launch {
            combine(settledCamera, riderPoint, deployment, enabled) { camera, rider, deployment, on -> Inputs(camera, rider, deployment, on) }
                .collect { onInputs(it) }
        }
    }

    /** Stop probing and forget the dock's state; the next [start] probes afresh. [probeExact] keeps working. */
    fun stop() {
        inputJob?.cancel()
        inputJob = null
        clearState()
        riderCache.clear()
        exactCache.clear()
        currentDeployment = null
    }

    /** Spec §2.4: the host hides the dock while a focus, the survey card or a tall sheet has the screen. */
    fun setSuppressed(value: Boolean) {
        suppressed.value = value
    }

    /** Spec §2.1 trigger 3: re-probe the last point on return to the foreground (through the cache). */
    fun onForeground() {
        val probe = lastProbe ?: _result.value?.probe ?: return
        val deployment = currentDeployment ?: return
        launchProbe(probe, deployment)
    }

    /**
     * Spec §2.1's exact-point probe for the address check and the planner: its own cache, keyed on
     * five decimals, never the rider/centre one. Null when the deployment is unknown or unsupported,
     * or the probe fails. Available with the layer off and before [start] or after [stop] (§2.4).
     */
    suspend fun probeExact(point: GeoPoint): List<OnDemandMatch>? {
        val deployment = latestDeployment.value ?: return null
        if (support.isKnownUnsupported(deployment)) return null
        return when (val outcome = fetch(exactCache, deployment, point)) {
            is Outcome.Services -> outcome.services.map { matchFor(it, now()) }
            Outcome.Unsupported -> {
                support.recordAbsent(deployment)
                clearState()
                null
            }
            Outcome.Failed -> null
        }
    }

    private fun onInputs(inputs: Inputs) {
        _zoomLevel.value = onDemandZoomLevel(inputs.camera.latSpan)
        val deployment = inputs.deployment
        if (deployment == null || !inputs.enabled || support.isKnownUnsupported(deployment)) {
            clearState()
            return
        }
        if (deployment != currentDeployment) {
            // Cancel in-flight work and drop the old server's state before the new one is asked.
            currentDeployment = deployment
            clearState()
        }
        val probe = ProbePoint(inputs.rider ?: inputs.camera.center, if (inputs.rider != null) ProbeSource.Rider else ProbeSource.MapCenter)
        val last = lastProbe
        val due = retryPending ||
            last == null ||
            last.source != probe.source ||
            haversineMeters(last.point, probe.point) >= ONDEMAND_PROBE_MOVE_METERS
        if (due) launchProbe(probe, deployment)
    }

    private fun launchProbe(probe: ProbePoint, deployment: String) {
        probeJob?.cancel()
        lastProbe = probe
        retryPending = false
        probeJob = scope.launch {
            when (val outcome = fetch(riderCache, deployment, probe.point)) {
                is Outcome.Services -> publish(deployment, probe, outcome.services)
                Outcome.Unsupported -> {
                    support.recordAbsent(deployment)
                    clearState()
                }
                Outcome.Failed -> {
                    // Keep the last state only while it still describes roughly where the rider is.
                    val current = _result.value
                    val nearLastState = current != null &&
                        current.deployment == deployment &&
                        haversineMeters(current.probe.point, probe.point) < ONDEMAND_PROBE_MOVE_METERS
                    if (!nearLastState) hideState()
                    retryPending = true
                }
            }
        }
    }

    private fun publish(deployment: String, probe: ProbePoint, services: List<OnDemandService>) {
        val now = now()
        val matches = services.map { matchFor(it, now) }
        _result.value = OnDemandProbeResult(deployment, probe, matches)
        refreshEdges(deployment, probe, matches)
        scheduleRecompute(deployment, probe, services, matches, now)
        loadGeometry(deployment, probe, matches)
    }

    /** Spec §2.1 trigger 4: re-evaluate availability at the earliest change, plus a second, without a request. */
    private fun scheduleRecompute(deployment: String, probe: ProbePoint, services: List<OnDemandService>, matches: List<OnDemandMatch>, now: Instant) {
        recomputeJob?.cancel()
        val next = matches.mapNotNull { it.availability.nextChangeInstant }.minOrNull() ?: return
        val delayMs = (next.toEpochMilli() + RECOMPUTE_GRACE_MS - now.toEpochMilli()).coerceAtLeast(0L)
        recomputeJob = scope.launch {
            delay(delayMs)
            if (isShowing(deployment, probe)) publish(deployment, probe, services)
        }
    }

    /** Edges for the services whose full geometry is already cached; the rest arrive from [loadGeometry]. */
    private fun refreshEdges(deployment: String, probe: ProbePoint, matches: List<OnDemandMatch>) {
        val cached = matches.mapNotNull { match -> geometryCache.peek(deployment, match.service.id)?.let { match.service.id to it } }.toMap()
        _geometry.value = cached
        _edges.value = cached.mapNotNull { (id, areas) -> nearestBoundaryPoint(probe.point, areas)?.let { id to it } }.toMap()
    }

    private fun loadGeometry(deployment: String, probe: ProbePoint, matches: List<OnDemandMatch>) {
        geometryJob?.cancel()
        val wanted = matches.filter { (it.isInside || it.isNearby) && geometryCache.peek(deployment, it.service.id) == null }
        if (wanted.isEmpty()) return
        geometryJob = scope.launch {
            for (match in wanted) {
                val areas = geometryCache.areas(deployment, match.service.id) ?: continue
                // A fetch that outlived a deployment change or a newer state answers for one we no longer show.
                if (!isShowing(deployment, probe)) return@launch
                _geometry.update { it + (match.service.id to areas) }
                nearestBoundaryPoint(probe.point, areas)?.let { edge -> _edges.update { it + (match.service.id to edge) } }
            }
        }
    }

    /** Whether the state on screen is the one [probe] produced on [deployment]. */
    private fun isShowing(deployment: String, probe: ProbePoint): Boolean {
        val current = _result.value
        return current != null && current.deployment == deployment && current.probe == probe
    }

    /** Hide the dock's state and its follow-up work, keeping the probe point and any probe in flight. */
    private fun hideState() {
        recomputeJob?.cancel()
        geometryJob?.cancel()
        _result.value = null
        _edges.value = emptyMap()
        _geometry.value = emptyMap()
    }

    /** Forget everything, so the next input probes afresh. */
    private fun clearState() {
        probeJob?.cancel()
        hideState()
        lastProbe = null
        retryPending = false
    }

    private sealed interface Outcome {
        data class Services(val services: List<OnDemandService>) : Outcome
        data object Unsupported : Outcome
        data object Failed : Outcome
    }

    private suspend fun fetch(cache: ProbeCache, deployment: String, point: GeoPoint): Outcome {
        cache.get(deployment, point, timeProvider.now())?.let { return Outcome.Services(it) }
        return when (val result = dataSource.servicesNear(point, ONDEMAND_PROBE_RADIUS_METERS)) {
            is OnDemandResult.Loaded -> {
                cache.put(deployment, point, result.value, timeProvider.now())
                Outcome.Services(result.value)
            }
            OnDemandResult.Unsupported -> Outcome.Unsupported
            is OnDemandResult.Failed -> Outcome.Failed
        }
    }

    private fun now(): Instant = Instant.ofEpochMilli(timeProvider.now())

    /** Responses per `(deployment, point rounded to [decimals])`, alive for [CACHE_LIFETIME_MS]. */
    private class ProbeCache(private val decimals: Int) {
        private class Entry(val services: List<OnDemandService>, val atMs: Long)

        private val entries = HashMap<Triple<String, Long, Long>, Entry>()

        private fun key(deployment: String, point: GeoPoint): Triple<String, Long, Long> {
            val scale = 10.0.pow(decimals)
            return Triple(deployment, (point.latitude * scale).roundToLong(), (point.longitude * scale).roundToLong())
        }

        fun get(deployment: String, point: GeoPoint, nowMs: Long): List<OnDemandService>? = entries[key(deployment, point)]?.takeIf { nowMs - it.atMs < CACHE_LIFETIME_MS }?.services

        fun put(deployment: String, point: GeoPoint, services: List<OnDemandService>, nowMs: Long) {
            entries[key(deployment, point)] = Entry(services, nowMs)
        }

        fun clear() = entries.clear()
    }
}
