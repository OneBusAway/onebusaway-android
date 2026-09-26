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

import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.sortedSoonestUsable

/** What the dock slot shows (spec §2.4); the planner fallback is a sheet on Android, so it has no case here. */
sealed interface OnDemandDockState {
    data object Hidden : OnDemandDockState

    /** Region level, probe point inside ≥ 1 service: [matches] are the inside matches in §2.6 order. */
    data class Card(val matches: List<OnDemandMatch>, val probe: ProbePoint) : OnDemandDockState

    /** Street level: the stack — inside matches when any, else the nearby ones — in §2.6 order. */
    data class Bar(val matches: List<OnDemandMatch>, val probe: ProbePoint) : OnDemandDockState
}

/** One probe's outcome, kept whole so the picker's "all services here" can read the full list. */
data class OnDemandProbeResult(val deployment: String, val probe: ProbePoint, val matches: List<OnDemandMatch>)

/**
 * The dock content for a probe result at a zoom level. [visible] folds in every host-side reason to
 * show nothing (a focused stop, the survey card, a sheet over half the map, the layer off).
 */
internal fun dockStateFor(result: OnDemandProbeResult?, level: OnDemandZoomLevel, visible: Boolean): OnDemandDockState {
    if (result == null || !visible) return OnDemandDockState.Hidden
    val inside = result.matches.filter { it.isInside }.sortedSoonestUsable()
    return when (level) {
        OnDemandZoomLevel.HIDDEN -> OnDemandDockState.Hidden
        OnDemandZoomLevel.REGION -> if (inside.isEmpty()) OnDemandDockState.Hidden else OnDemandDockState.Card(inside, result.probe)
        OnDemandZoomLevel.STREET -> {
            val stack = inside.ifEmpty { result.matches.filter { it.isNearby }.sortedSoonestUsable() }
            if (stack.isEmpty()) OnDemandDockState.Hidden else OnDemandDockState.Bar(stack, result.probe)
        }
    }
}
