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
package org.onebusaway.android.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * A live "now" in the **server** clock domain, anchored on [serverTime] (a poll's server-clock
 * reading) and advanced by the device's elapsed time since that reading was captured — the same
 * skew-free extrapolation `VehicleInfoWindow`'s "…ago" age text uses (#1612), generalized to the
 * typed [ServerTime]/[ElapsedTime] domains so a live countdown (e.g. an ETA pill, issue #1781) never
 * measures against a bare device clock.
 *
 * Re-anchors via [remember] whenever [serverTime] changes value — i.e. the instant a fresh poll
 * republishes it — so the ticking value is always pre-empted by real data rather than drifting from
 * interpolation indefinitely.
 */
@Composable
fun rememberLiveServerTime(serverTime: ServerTime): ServerTime {
    val anchorElapsed = remember(serverTime) { ElapsedTime.now() }
    val nowElapsed = rememberTickingElapsedTime()
    return liveServerTime(serverTime, anchorElapsed, nowElapsed)
}

/**
 * A monotonic elapsed-time reading that updates once per second, so a live countdown/age ticks even
 * though the underlying data (e.g. the poll response) is unchanged between polls.
 */
@Composable
private fun rememberTickingElapsedTime(): ElapsedTime {
    val elapsed by produceState(ElapsedTime.now()) {
        while (true) {
            value = ElapsedTime.now()
            delay(1000)
        }
    }
    return elapsed
}

/**
 * [serverTime] advanced by the elapsed device time between [anchorElapsed] (captured when
 * [serverTime] was observed) and [nowElapsed]. Clamped at zero so a fresh anchor racing the
 * 1s-lagged ticker never yields a "now" before the anchor. Extracted from [rememberLiveServerTime]
 * as a pure function so it's JVM-unit-testable without Compose.
 */
internal fun liveServerTime(serverTime: ServerTime, anchorElapsed: ElapsedTime, nowElapsed: ElapsedTime): ServerTime =
    serverTime + (nowElapsed - anchorElapsed).coerceAtLeast(Duration.ZERO)
