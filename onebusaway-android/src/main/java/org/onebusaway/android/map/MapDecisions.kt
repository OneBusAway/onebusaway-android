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

import android.location.Location
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.PreferenceUtils
import java.util.concurrent.TimeUnit

/** Debounce before reacting to a camera move, matching the old MapWatcher poll cadence (stops + bikes). */
internal const val STOP_LOAD_DEBOUNCE_MS = 250L

/** A flavor-neutral [GeoPoint] as an Android [Location], for the repositories' lat/lon span queries. */
internal fun GeoPoint.toLocation(): Location = LocationUtils.makeLocation(latitude, longitude)

/** An Android [Location] (shape/interpolation point) as a flavor-neutral [GeoPoint] (inverse of [toLocation]). */
internal fun Location.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

/** The map's initial camera (lat/lon/zoom) before the loaders / region centering take over. */
data class MapCameraSeed(val lat: Double, val lon: Double, val zoom: Float)

/** The seed-camera default zoom (was HomeActivity.MAP_DEFAULT_ZOOM; NOT [MapParams.DEFAULT_ZOOM] = 18). */
internal const val SEED_DEFAULT_ZOOM = 16f

/**
 * Resolves the cold-launch camera seed from the map view model's [SavedStateHandle] (replacing the
 * former HomeActivity.readMapSeed). The [primary] seed is the SSH center/zoom — the launching intent's
 * extras as default-args on a fresh launch, the VM's own persisted values on process death, and the
 * live camera once [MapViewModel.onCameraIdle] has written one — falling back to the [persisted]
 * last-viewed camera (prefs) when the primary carries no explicit center. See [resolveMapSeed].
 */
internal fun resolveCameraSeed(handle: SavedStateHandle): MapCameraSeed {
    val primary = MapCameraSeed(
        lat = handle[MapParams.CENTER_LAT] ?: 0.0,
        lon = handle[MapParams.CENTER_LON] ?: 0.0,
        zoom = handle[MapParams.ZOOM] ?: SEED_DEFAULT_ZOOM,
    )
    val restored = Bundle().also { PreferenceUtils.maybeRestoreMapViewToBundle(it) }
    val persisted = MapCameraSeed(
        lat = restored.getDouble(MapParams.CENTER_LAT, 0.0),
        lon = restored.getDouble(MapParams.CENTER_LON, 0.0),
        // The persisted zoom defaults to the primary zoom (so an empty persisted view keeps it).
        zoom = restored.getFloat(MapParams.ZOOM, primary.zoom),
    )
    return resolveMapSeed(primary, persisted)
}

/**
 * Resolves the initial map camera from the launch sources. The [primary] seed (saved instance state,
 * else the launching intent) wins, unless it carries no explicit center — lat *and* lon both 0 — in
 * which case we fall back to the [persisted] last-viewed camera. The caller applies the read defaults
 * (e.g. the persisted zoom defaults to the primary zoom), so this is just the precedence decision.
 */
internal fun resolveMapSeed(primary: MapCameraSeed, persisted: MapCameraSeed): MapCameraSeed =
    if (primary.lat == 0.0 && primary.lon == 0.0) persisted else primary

/** How often the real-time vehicle positions are refreshed while a route is shown. */
internal val VEHICLE_REFRESH_PERIOD_MS = TimeUnit.SECONDS.toMillis(10)

/**
 * The delay before the next vehicle refresh when (re)starting the poll — e.g. on resume. So resuming
 * mid-period waits only the remainder. Pure and unit-tested; [lastUpdated]/[now] are nanosecond
 * timestamps (`SystemClock.elapsedRealtimeNanos()`).
 *
 *  - never loaded ([lastUpdated] == 0) → a full period
 *  - already overdue → a near-immediate refresh (100 ms)
 *  - otherwise → the remaining time in the current period
 */
internal fun nextVehicleDelay(lastUpdated: Long, now: Long): Long {
    if (lastUpdated == 0L) {
        return VEHICLE_REFRESH_PERIOD_MS
    }
    val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(now - lastUpdated)
    return if (elapsedMillis > VEHICLE_REFRESH_PERIOD_MS) {
        100L
    } else {
        VEHICLE_REFRESH_PERIOD_MS - elapsedMillis
    }
}

/**
 * Clears [accum] down to just the focused stop, in place: keeps the entry for [focusedId] (if present)
 * and drops the rest. The focus-retention behavior shared by the stop-accumulation cap and
 * `clearStops(false)`. Pure (operates on a plain map of [StopMarker]) so it's unit-testable.
 */
internal fun retainOnlyFocusedStop(accum: LinkedHashMap<String, StopMarker>, focusedId: String?) {
    val focused = focusedId?.let { accum[it] }
    accum.clear()
    if (focused != null) {
        accum[focused.id] = focused
    }
}

/**
 * The stop-accumulation cap: once [accum] reaches [cap] stops, clear it (keeping only the focused
 * stop) so the next batch starts fresh — bounding the marker count as the user pans. No-op below the
 * cap. Pure, so the (easily-broken) keep-the-focused-stop-on-cap behavior is unit-testable.
 */
internal fun capStopAccumulation(
    accum: LinkedHashMap<String, StopMarker>,
    focusedId: String?,
    cap: Int,
) {
    if (accum.size >= cap) {
        retainOnlyFocusedStop(accum, focusedId)
    }
}

/**
 * The pure decision branches behind [MapViewModel]'s location/region behavior, split out from the
 * `Application`/`LocationUtils`/`PermissionUtils` reads (which stay at the call site) so the branch
 * logic — the historically error-prone permission/location flow ported from the flavor hosts — can be
 * unit-tested on the JVM. Same pattern as [zoomFulfills] / [stopRequestFulfilled] /
 * [org.onebusaway.android.map.bike.bikeAction] / [nextVehicleDelay].
 */

/** What the my-location request should do, given the current location/permission state. */
enum class MyLocationAction {
    /** Move the camera to the last-known location. */
    MoveToLocation,

    /** Location services are off — show the enable-location dialog. */
    ShowNoLocationDialog,

    /** No permission yet — show the rationale before asking. */
    ShowPermissionRationale,

    /** Permission + services but no fix yet — show the "waiting for location" toast. */
    ShowWaitingToast,

    /** Nothing to do (e.g. services off but the user opted out of the dialog, or permission denied). */
    None,
}

/**
 * The my-location decision, ported verbatim from the flavor hosts' `setMyLocation`:
 *  - services off → the no-location dialog, unless the user opted out of it
 *  - no fix yet + no permission → the rationale, unless the user already declined permission
 *  - no fix yet + permission → the "waiting" toast
 *  - otherwise → move to the last-known location
 */
fun myLocationAction(
    locationEnabled: Boolean,
    neverShowLocationDialog: Boolean,
    hasLastKnownLocation: Boolean,
    hasPermission: Boolean,
    userDeniedPermission: Boolean,
): MyLocationAction {
    if (!locationEnabled) {
        return if (neverShowLocationDialog) MyLocationAction.None else MyLocationAction.ShowNoLocationDialog
    }
    if (!hasLastKnownLocation) {
        if (!hasPermission) {
            return if (userDeniedPermission) MyLocationAction.None else MyLocationAction.ShowPermissionRationale
        }
        return MyLocationAction.ShowWaitingToast
    }
    return MyLocationAction.MoveToLocation
}

/** How a resolved region should (re)frame the camera. */
enum class RegionRezoom {
    /** Frame the user's location. */
    FrameMyLocation,

    /** Frame the region's bounds. */
    FrameRegion,

    /** Leave the camera where it is. */
    None,
}

/**
 * The region-resolved re-zoom decision, ported from the hosts' `onRegionTaskFinished`: only re-frame
 * when the region actually changed *and* we have no location or the camera is still at the (0,0) seed;
 * prefer framing the user's location when we have one, else the region.
 */
fun regionRezoom(changed: Boolean, hasLocation: Boolean, cameraAtSeed: Boolean): RegionRezoom {
    if (!changed) {
        return RegionRezoom.None
    }
    // Has a location but the camera has already moved off the seed: don't yank it back.
    if (hasLocation && !cameraAtSeed) {
        return RegionRezoom.None
    }
    return if (hasLocation) RegionRezoom.FrameMyLocation else RegionRezoom.FrameRegion
}

/**
 * The zoom/limit-exceeded half of the "is the last response still good for this viewport?" decision
 * (the legacy `StopsResponse.fulfills`), split out from the Android [Location] center comparison so
 * it can be unit-tested on the JVM. Assumes the caller already confirmed the centers match.
 *
 * @param hasResponse whether the last request produced a (non-null) response
 * @param lastLimitExceeded the last response's `limitExceeded` flag
 * @param lastZoom the zoom the last response was loaded at
 * @param newZoom the zoom of the new viewport
 * @return true if the last response still satisfies the new viewport (no reload needed)
 */
internal fun zoomFulfills(
    hasResponse: Boolean,
    lastLimitExceeded: Boolean,
    lastZoom: Double,
    newZoom: Double,
): Boolean {
    if (!hasResponse) {
        return true
    }
    // Zooming in past a capped response, or zooming out, both need a fresh load.
    if (newZoom > lastZoom && lastLimitExceeded) {
        return false
    }
    if (newZoom < lastZoom) {
        return false
    }
    return true
}

/**
 * The whole-request replacement for the legacy `StopsResponse.fulfills`: is the new viewport [next]
 * already satisfied by the last completed stop load, so no reload is needed? The reactive stop loader
 * in [MapViewModel] uses this in place of the controller's `lastResponse?.fulfills(request)` check.
 *
 * [CameraSnapshot.center] is a value type, so the center comparison is honest value-equality (the
 * legacy version compared the request center as an Android [Location] — reference equality — so a
 * fresh instance almost never matched and the gate rarely short-circuited). The zoom/limit-exceeded
 * half delegates to [zoomFulfills], unchanged.
 *
 * @param last the camera the last completed load was made at, or null if nothing has loaded yet
 * @param lastHadResponse whether that load produced a non-null response (a null response — e.g. no
 * API endpoint — fulfilled future same-center viewports, matching the legacy null-response no-op)
 * @param lastLimitExceeded that response's `limitExceeded` flag
 * @param next the new viewport
 */
internal fun stopRequestFulfilled(
    last: CameraSnapshot?,
    lastHadResponse: Boolean,
    lastLimitExceeded: Boolean,
    next: CameraSnapshot,
): Boolean {
    if (last == null) {
        return false
    }
    if (last.center != next.center) {
        return false
    }
    return zoomFulfills(lastHadResponse, lastLimitExceeded, last.zoom, next.zoom)
}
