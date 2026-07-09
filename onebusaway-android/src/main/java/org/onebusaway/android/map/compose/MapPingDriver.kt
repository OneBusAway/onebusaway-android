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
package org.onebusaway.android.map.compose

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapPing
import org.onebusaway.android.map.render.PingTarget
import org.onebusaway.android.time.WallTime

/**
 * Drive one-shot vehicle pings onto [target] (#1764) — the flavor-neutral orchestration both compose
 * adapters share (they differ only in the SDK draw, which lives behind [PingTarget]). For each point from
 * [pings]: wait for the framing pan to settle (the next [camera] idle, bounded by [MapPing.SETTLE_TIMEOUT_MS]
 * in case the fit didn't move the camera) so the ripple plays on the settled map, then animate it at the
 * full display rate via [withFrameNanos] — off the vehicle loop's throttle, so it's smooth. `collectLatest`
 * so a newer ping supersedes one still waiting/animating. Call from each adapter's `LaunchedEffect`.
 */
suspend fun drivePings(
    pings: SharedFlow<GeoPoint>,
    camera: StateFlow<CameraSnapshot?>,
    target: PingTarget,
) {
    pings.collectLatest { point ->
        withTimeoutOrNull(MapPing.SETTLE_TIMEOUT_MS) { camera.drop(1).first() }
        target.startPing(point)
        while (target.tickPing(WallTime.now().epochMs)) {
            withFrameNanos { }
        }
    }
}
