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
package org.onebusaway.android.map.render

/**
 * The zoom at or above which stops show their full directional icon; below it they collapse to a
 * small dot to reduce clutter when a lot of stops are on screen. Tunable — mirrors the spirit of the
 * bike zoom bands ([bikeZoomBand]).
 */
const val STOP_DOT_ZOOM_THRESHOLD = 15f

/** Whether a stop renders as a small dot (distant zoom) or its full directional icon (close zoom). */
enum class StopBand { DOT, FULL }

/** The [StopBand] a stop falls in at [zoom]: a dot below [STOP_DOT_ZOOM_THRESHOLD], else the full icon. */
fun stopZoomBand(zoom: Float): StopBand =
    if (zoom < STOP_DOT_ZOOM_THRESHOLD) StopBand.DOT else StopBand.FULL

/** The icon variants a stop marker can show: full icon (normal/focused) or dot (normal/focused). */
enum class StopIconKind { FULL, FULL_FOCUSED, DOT, DOT_FOCUSED }

/**
 * The icon a stop marker should show given whether it's the [focused] stop and the current zoom
 * [band]. The focused stop always gets a distinct (accent) icon — the enlarged full icon up close,
 * an accent-colored dot far out — so a selection stays visible at every zoom. Pure, so the renderers'
 * "did this marker's icon change?" decision is unit-testable and identical across both map flavors.
 */
fun stopIconKind(focused: Boolean, band: StopBand): StopIconKind = when {
    band == StopBand.DOT -> if (focused) StopIconKind.DOT_FOCUSED else StopIconKind.DOT
    focused -> StopIconKind.FULL_FOCUSED
    else -> StopIconKind.FULL
}
