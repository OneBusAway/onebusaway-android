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
package org.onebusaway.android.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure decision logic for the arrivals bottom sheet, lifted out of [HomeScreen]'s `LaunchedEffect`
 * /`BackHandler` so the parity-sensitive behavior (the part that can't be exercised in a JVM test
 * from inside a `@Composable`) is unit-testable. [HomeScreen] does the Compose plumbing — keying the
 * effect, reading the live `SheetState`, animating — and defers every *decision* to these functions.
 *
 * The model: **visibility is business state** ([shouldShowSheet]); **expansion is ephemeral UI**
 * toggled by [toggleSheetTarget] and unwound by [sheetBackAction].
 */

/** The arrivals sheet's resting position, reported from the screen back to the activity and the state
 *  the toggle/back decisions below operate on. */
enum class ArrivalsSheetState { Hidden, Collapsed, Expanded }

/** The sheet is shown (peeking or full) iff a stop is focused. (HOME is always the map now — the
 *  list screens are their own destinations — so a focused stop is the only condition.) [HomeScreen]
 *  translates this into an animated peek height (the sheet has no `Hidden` drag anchor). */
internal fun shouldShowSheet(focus: CurrentFocus): Boolean = focus is CurrentFocus.Stop

/**
 * Bottom edge used to keep map content below the active top chrome: the stop/route focus banner, or —
 * in directions — the trip-plan form card ([directionsFormBottomPx]), so the map's top content padding
 * reflects the form/FAB and a focused itinerary step centers in the band below it.
 */
internal fun focusBannerTopEdge(
    focus: CurrentFocus,
    focusBannerBottomPx: Int,
    directionsFormBottomPx: Int = 0
): Int = when (focus) {
    is CurrentFocus.Route, is CurrentFocus.Stop -> focusBannerBottomPx
    is CurrentFocus.Directions -> directionsFormBottomPx
    CurrentFocus.None, is CurrentFocus.BikeStation -> 0
}

/**
 * How far to lift the map's floating controls (my-location, zoom, layers) off the bottom edge so they
 * stay in the visible map band above whichever bottom sheet is resting over it.
 *
 * [arrivalsPeek] is the collapsed arrivals peek, counted only while the arrivals sheet rests *at* that
 * peek ([arrivalsAtPeek]) — an expanded arrivals sheet covers the controls outright, so lifting to it
 * would only park them behind a full-height panel. [directionsSheet] is the directions results drawer's
 * settled height, or zero when that drawer isn't shown; it always counts, because the drawer rests at a
 * fraction of the window and the controls belong above it in either of its two positions (#2155).
 *
 * The two sheets are mutually exclusive today — directions focus is not stop focus, so [shouldShowSheet]
 * keeps the arrivals sheet hidden for the whole of directions mode — but taking the larger clears either
 * one without depending on that.
 */
internal fun mapControlsBottomInset(
    arrivalsPeek: Dp,
    arrivalsAtPeek: Boolean,
    directionsSheet: Dp
): Dp = maxOf(if (arrivalsAtPeek) arrivalsPeek else 0.dp, directionsSheet)

/** The drag-handle toggle target: a full sheet collapses to peek; anything else expands to full. */
internal fun toggleSheetTarget(current: ArrivalsSheetState): ArrivalsSheetState = if (current == ArrivalsSheetState.Expanded) ArrivalsSheetState.Collapsed else ArrivalsSheetState.Expanded

/** Whether the sheet consumes back by collapsing before focus navigation proceeds. */
enum class SheetBackAction { COLLAPSE, NAVIGATE_BACK, NONE }

internal fun sheetBackAction(current: ArrivalsSheetState): SheetBackAction = when (current) {
    ArrivalsSheetState.Expanded -> SheetBackAction.COLLAPSE // full -> peek
    ArrivalsSheetState.Collapsed -> SheetBackAction.NAVIGATE_BACK
    ArrivalsSheetState.Hidden -> SheetBackAction.NONE // let the system handle back
}
