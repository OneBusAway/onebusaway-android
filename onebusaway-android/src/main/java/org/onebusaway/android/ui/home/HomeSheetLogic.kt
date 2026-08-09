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
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.map.render.showsNearbyArrivals

/**
 * Pure decision logic for the arrivals bottom sheet, lifted out of [HomeScreen]'s `LaunchedEffect`
 * /`BackHandler` so the parity-sensitive behavior (the part that can't be exercised in a JVM test
 * from inside a `@Composable`) is unit-testable. [HomeScreen] does the Compose plumbing — keying the
 * effect, reading the live `SheetState`, animating — and defers every *decision* to these functions.
 *
 * The model: **visibility is business state** ([homeSheetContent]); **expansion is ephemeral UI**
 * toggled by [toggleSheetTarget] and unwound by [sheetBackAction].
 */

/** The arrivals sheet's resting position, reported from the screen back to the activity and the state
 *  the toggle/back decisions below operate on. */
enum class ArrivalsSheetState { Hidden, Collapsed, Expanded }

/**
 * What the bottom sheet is showing. Three states, not two, since #2107: at transit-centre zoom the
 * sheet also engages with **no** stop focused, listing every route leaving every bay in view.
 *
 * Derived, never stored — deliberately not a [CurrentFocus] variant. `CurrentFocus` is the map's
 * mutually-exclusive *subject*: it is persisted across process death and it drives the undo stack. The
 * nearby list has no subject — it is precisely what shows when there is none — so a variant for it
 * would push a state onto the back stack that Back has no meaning for, and force every `when (focus)`
 * in the view model to grow a branch the map never renders.
 */
sealed interface HomeSheetContent {

    /** Nothing shown; the peek is retracted. */
    data object None : HomeSheetContent

    /** One focused stop's arrivals — the original drawer. */
    data class Stop(val stopId: String) : HomeSheetContent

    /** Every route leaving every bay in view, at transit-centre zoom (#2107). */
    data object NearbyRoutes : HomeSheetContent
}

/**
 * The sheet's content for the current [focus], zoom [band], and whether the nearby query has rows to
 * show ([nearbyRowsReady]).
 *
 * A focused stop always wins: it is a deliberate choice about one bay, and the map is already showing
 * it selected. Otherwise the transit-centre band engages the nearby list, through the same
 * [showsNearbyArrivals] predicate `NearbyArrivalsViewModel` gates its query on — one definition, so
 * the sheet cannot decide to show a band the query never asked for.
 *
 * Gating on rows rather than on the query's state is what keeps the drawer honest: it never opens
 * empty while loading, never opens over a viewport with no departures, and never opens at all on a
 * region whose server can't answer the query.
 */
internal fun homeSheetContent(
    focus: CurrentFocus,
    band: StopBand,
    nearbyRowsReady: Boolean
): HomeSheetContent = when {
    focus is CurrentFocus.Stop -> HomeSheetContent.Stop(focus.stop.id)
    focus is CurrentFocus.None && band.showsNearbyArrivals && nearbyRowsReady ->
        HomeSheetContent.NearbyRoutes
    else -> HomeSheetContent.None
}

/**
 * The reveal effect's key: the identity of what is shown, or null when nothing is. Stable across a pan
 * or zoom *within* the nearby mode, so re-querying a new viewport updates the list in place instead of
 * re-running the reveal or fighting a drag the rider is in the middle of.
 */
internal val HomeSheetContent.sheetKey: String?
    get() = when (this) {
        HomeSheetContent.None -> null
        is HomeSheetContent.Stop -> "stop:$stopId"
        HomeSheetContent.NearbyRoutes -> "nearby"
    }

/**
 * How much of the window the collapsed peek may cover. A property of what the sheet holds, because the
 * two drawers are *asked for* differently:
 *
 *  - A tapped stop is a deliberate request to read that stop, so its peek is worth real screen — it
 *    opens showing actual arrival rows.
 *  - The transit-centre list (#2107) is ambient: it appears on zoom without anyone asking, over a map
 *    the rider is still reading. Its peek is only wide enough to advertise that a list is there (a row
 *    or so under the handle); dragging up is how you read it.
 *
 * [HomeSheetContent.None] is retracting to zero anyway, so its value is never seen — it takes the stop
 * fraction so the number is stable if a hide is interrupted by a re-show.
 */
internal val HomeSheetContent.peekHeightFraction: Float
    get() = when (this) {
        HomeSheetContent.NearbyRoutes -> NEARBY_PEEK_HEIGHT_FRACTION
        HomeSheetContent.None, is HomeSheetContent.Stop -> STOP_PEEK_HEIGHT_FRACTION
    }

private const val STOP_PEEK_HEIGHT_FRACTION = 0.30f

private const val NEARBY_PEEK_HEIGHT_FRACTION = 0.15f

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
 * The two sheets are mutually exclusive today — directions focus is neither a stop focus nor the
 * no-focus state the nearby list needs, so [homeSheetContent] resolves to [HomeSheetContent.None] for
 * the whole of directions mode — but taking the larger clears either one without depending on that.
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

/**
 * Back's effect given the sheet's resting position and what it is showing.
 *
 * An expanded sheet always collapses to peek first, whatever it holds. From peek it depends: a focused
 * stop is a focus to step out of, but the **nearby list is not** — it is ambient, the thing that shows
 * when nothing is focused, and there is nothing behind it to go back to. Swallowing Back there would
 * strand the rider on a screen they can't leave, so it passes to the system.
 */
internal fun sheetBackAction(
    current: ArrivalsSheetState,
    content: HomeSheetContent = HomeSheetContent.None
): SheetBackAction = when (current) {
    ArrivalsSheetState.Expanded -> SheetBackAction.COLLAPSE // full -> peek
    ArrivalsSheetState.Collapsed ->
        if (content == HomeSheetContent.NearbyRoutes) {
            SheetBackAction.NONE
        } else {
            SheetBackAction.NAVIGATE_BACK
        }
    ArrivalsSheetState.Hidden -> SheetBackAction.NONE // let the system handle back
}
