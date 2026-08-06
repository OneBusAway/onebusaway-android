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
package org.onebusaway.android.ui.home.directions

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.directions.util.OtpTarget
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.map.pickRideDirection
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.EtaStripMarker
import org.onebusaway.android.ui.arrivals.rememberArrivalRowCallbacks
import org.onebusaway.android.ui.compose.components.CenteredLongPressMenu
import org.onebusaway.android.ui.compose.components.DRAG_HANDLE_TOUCH_TARGET_HEIGHT
import org.onebusaway.android.ui.compose.components.MenuRow
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.SheetDragHandle
import org.onebusaway.android.ui.compose.components.SwitchRow
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.ui.compose.unitsAreMetric
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.arrivals.ArrivalsSession
import org.onebusaway.android.ui.home.arrivals.rememberArrivalsSession
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.ui.tripplan.AdvancedSettings
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.StreetMode
import org.onebusaway.android.ui.tripplan.TripDateTimeDialog
import org.onebusaway.android.ui.tripplan.TripEndpointDotIcon
import org.onebusaway.android.ui.tripplan.TripEndpointSlot
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.TripPlanError
import org.onebusaway.android.ui.tripplan.TripPlanForm
import org.onebusaway.android.ui.tripplan.TripPlanFormState
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.TripPlanViewModel
import org.onebusaway.android.ui.tripplan.VehicleMode
import org.onebusaway.android.ui.tripplan.WalkPreference
import org.onebusaway.android.ui.tripresults.FocusedLeg
import org.onebusaway.android.ui.tripresults.RouteLegRef
import org.onebusaway.android.ui.tripresults.RouteStopRef
import org.onebusaway.android.ui.tripresults.TripLogEntry
import org.onebusaway.android.ui.tripresults.TripResultsSheet
import org.onebusaway.android.ui.tripresults.TripResultsUiState
import org.onebusaway.android.ui.tripresults.TripResultsViewModel
import org.onebusaway.android.ui.tripresults.focusTransit
import org.onebusaway.android.ui.tripresults.rideCoveringLegs
import org.onebusaway.android.util.BikeshareAvailability
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.PreferenceUtils

/**
 * The trip planner embedded on the home map as a directions focus. This file hosts the on-map surfaces:
 * [DirectionsFormCard] (the compact form that replaces the top-chrome search field),
 * [DirectionsResultsSheet] (the bottom directions list), and [DirectionsPickOverlay] (pick a From/To
 * point directly on the home map). The itinerary itself renders on the shared home map via the
 * [MapViewModel] directions controller — driven by [TripResultsSheet]'s selection.
 *
 * The address-book (contacts) picker was removed (#1936 tracks accepting place intents from other apps
 * instead). Map-pick is hoisted to the caller ([DirectionsFormCard]'s `onPickEndpoint`);
 * current-location, date/time, and advanced settings are wired here.
 */

/**
 * The compact trip-plan form, shown in the top chrome (in place of the search field) while planning.
 * No header/close chrome — exiting directions is a back gesture (handled in HomeScreen).
 */
@Composable
fun DirectionsFormCard(
    viewModel: TripPlanViewModel,
    state: TripPlanFormState,
    onPickEndpoint: (TripEndpointSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAdvanced by remember { mutableStateOf(false) }
    // The instant the picker opens on, captured when it is opened rather than read from the form on
    // each recomposition: under the "now" anchor the form's own dateTimeMillis is the moment the
    // ViewModel was built, which a form left open has long outrun (see [TripPlanViewModel.pickerStartMillis]).
    // Saved, not merely remembered, so a rotation mid-pick doesn't close the dialog on the rider — the
    // fragment-based pickers this replaced survived one, and the dialog's own state is saveable too.
    var pickerStartMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val usesOtp2 = remember { OtpTarget.resolve(context).usesOtp2 }
    val bikeshare = remember { BikeshareAvailability.isTripPlanningEnabled(context) }
    val availableStreetModes = StreetMode.entries.filter { it.isAvailableIn(bikeshare, usesOtp2) }
    val vehicleModePreference = stringResource(R.string.preference_key_trip_plan_vehicle_mode)
    val streetModePreference = stringResource(R.string.preference_key_trip_plan_street_mode)

    // containerSize (px) reflects the actual available window; Configuration.screenHeightDp is lint-flagged
    // as unreliable across insets/multi-window. Match the HomeScreen peek-cap pattern.
    val maxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * 0.6f).toDp()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            TripPlanForm(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onSelect = viewModel::setEndpoint,
                onCurrentLocation = { slot -> setCurrentLocation(context, viewModel, slot) },
                onPickOnMap = onPickEndpoint,
                onSetArriving = viewModel::setArriving,
                onDepartNow = viewModel::setDepartNow,
                onPickDateTime = { pickerStartMillis = viewModel.pickerStartMillis() },
                availableStreetModes = availableStreetModes,
                onVehicleModeSelected = { vehicle ->
                    viewModel.setModeSelection(state.modes.copy(vehicle = vehicle))
                    PreferenceUtils.saveString(vehicleModePreference, vehicle.name)
                },
                onStreetModeSelected = { street ->
                    viewModel.setModeSelection(state.modes.copy(street = street))
                    PreferenceUtils.saveString(streetModePreference, street.name)
                },
                onReverse = viewModel::reverseTrip,
                onRefresh = viewModel::refreshPlan,
                onAdvancedSettings = { showAdvanced = true }
            )
        }
    }
    if (showAdvanced) {
        DirectionsAdvancedSettingsDialog(viewModel = viewModel, onDismiss = { showAdvanced = false })
    }
    pickerStartMillis?.let { start ->
        TripDateTimeDialog(
            initialMillis = start,
            onDismiss = { pickerStartMillis = null },
            onConfirm = { millis ->
                viewModel.setDateTime(millis)
                pickerStartMillis = null
            }
        )
    }
}

/** Set [slot] to the device's last-known location, or toast if none is available. */
private fun setCurrentLocation(context: Context, viewModel: TripPlanViewModel, slot: TripEndpointSlot) {
    if (viewModel.setEndpointToCurrentLocation(slot)) return
    // A null fix means "no permission" only when permission is actually denied; with permission
    // granted it just means we don't have a fix yet, which is a different (recoverable) message.
    val messageRes = if (PermissionUtils.hasGrantedAtLeastOnePermission(
            context,
            PermissionUtils.LOCATION_PERMISSIONS
        )
    ) {
        R.string.main_waiting_for_location
    } else {
        R.string.no_location_permission
    }
    Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
}

/** The expanded directions sheet's share of the window height (the collapsed peek is handle-only). */
private const val DIRECTIONS_SHEET_HEIGHT_FRACTION = 0.4f

/**
 * The bottom directions sheet: option cards + the step-by-step list, over the map. A standard Material 3
 * persistent bottom sheet — [BottomSheetScaffold]'s sheet slot — so it gets the real sheet gesture:
 * the sheet tracks the finger, settles on velocity, and drags from anywhere on it (including a
 * nested-scroll handoff, so pulling down at the top of the step list collapses the sheet). Collapsed it
 * rests at a handle-only peek revealing the map; expanded it fills [DIRECTIONS_SHEET_HEIGHT_FRACTION]
 * of the window. Tapping the handle still toggles, and screen readers get M3's expand/collapse actions.
 *
 * The scaffold hosts *only* the sheet: its body is empty and its container transparent, so the map that
 * the caller drew underneath shows through and keeps receiving its own gestures (M3 builds the scaffold
 * root from a plain `Box`, not a touch-intercepting `Surface`). [onSheetHeightPx] reports the sheet's
 * settled on-screen height for the map's bottom inset.
 *
 * It also answers the map's own tap on a ride ([rideBadgeTaps], #2101) — the sheet is where a ride's
 * resolved identity lives, so this is the one place that can spend a map tap the same way the drawer's
 * own row tap is spent. Where a label covers the same route ridden twice, it resolves to the first of
 * those rides in travel order (see
 * [rideCoveringLegs][org.onebusaway.android.ui.tripresults.rideCoveringLegs]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsResultsSheet(
    resultsViewModel: TripResultsViewModel,
    itineraries: List<TripItinerary>,
    params: TripPlanParams?,
    showItinerary: (TripItinerary) -> Unit,
    onFocusRouteLeg: (RouteLegRef, FocusedLeg) -> Unit,
    onFocusLeg: (FocusedLeg) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (TripLogEntry.Transit, RouteStopRef) -> Unit,
    onSheetHeightPx: (Int) -> Unit,
    // The itinerary leg indices of a route label tapped on the map, as they arrive. Required rather than
    // defaulted to an empty flow: omitting it leaves the map's labels dead, which is a wiring bug that
    // would otherwise type-check.
    rideBadgeTaps: Flow<Set<Int>>,
    modifier: Modifier = Modifier
) {
    // The system nav-bar inset: the sheet reaches the bottom edge (continuous background), but its
    // content is padded above the nav chrome so the collapsed handle (and the last list row) aren't
    // stranded under the gesture pill / 3-button bar. The peek includes it so the handle clears it.
    val navBottom = navigationBarBottomPadding()
    val peekHeight = DRAG_HANDLE_TOUCH_TARGET_HEIGHT + navBottom
    // containerSize (px), not Configuration.screenHeightDp (lint-flagged as unreliable across insets).
    //
    // Floored at the peek, because a sheet shorter than its own peek inverts the two states: M3 anchors
    // PartiallyExpanded at (layoutHeight - peek) and Expanded at (layoutHeight - sheetHeight), so the
    // collapsed sheet would sit *above* the expanded one. Reachable in a freeform window near Android's
    // 220dp resizable minimum alongside a 48dp 3-button bar, where the fraction lands under the peek. At
    // the floor M3 drops the Expanded anchor outright (it skips it when sheet height == peek) and the
    // sheet just rests at the handle — the honest outcome for a window with no room to open into.
    val fullHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * DIRECTIONS_SHEET_HEIGHT_FRACTION).toDp()
    }.coerceAtLeast(peekHeight)
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // The map's bottom inset follows the *settled* sheet, not the live drag — a per-frame inset would
    // re-frame the map on every gesture event. Both resting heights are known up front, so this needs no
    // measurement: expanded is the sheet's own height, collapsed is the peek. Keyed on the heights too,
    // so a late nav-bar inset (or a rotation) re-emits rather than sticking at the stale value.
    val density = LocalDensity.current
    LaunchedEffect(sheetState, fullHeight, peekHeight) {
        snapshotFlow { sheetState.currentValue }.collect { value ->
            val resting = if (value == SheetValue.Expanded) fullHeight else peekHeight
            onSheetHeightPx(with(density) { resting.roundToPx() })
        }
    }

    // A route label tapped on the drawn itinerary focuses that ride exactly as tapping its row in the
    // list below does — the same handler, so one ride can't mean two things depending on where the rider
    // reached for it. The label names legs of the itinerary it is drawn on, and the drawn itinerary is
    // this VM's own selection, so the indices are read against the plan that produced them; a set naming
    // no ride here focuses nothing rather than guessing, and one naming several (the same route ridden
    // twice under a single label) focuses the first in travel order.
    //
    // The handlers are read through rememberUpdatedState so this long-lived collector spends the latest
    // ones rather than those it first captured.
    val focusRide by rememberUpdatedState({ ride: TripLogEntry.Transit ->
        focusTransit(ride, onFocusRouteLeg, onFocusLeg, onFocusPoint)
    })
    LaunchedEffect(resultsViewModel, rideBadgeTaps) {
        rideBadgeTaps.collect { legIndices ->
            val directions = (resultsViewModel.state.value as? TripResultsUiState.Success)?.directions.orEmpty()
            directions.rideCoveringLegs(legIndices)?.let(focusRide)
        }
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetTonalElevation = 2.dp,
        sheetShadowElevation = 8.dp,
        // The scaffold supplies the drag gesture and the tap/accessibility actions around whatever handle
        // it's given, so this is the app's own bar — the same one the arrivals sheet shows — in the 48dp
        // band [DRAG_HANDLE_TOUCH_TARGET_HEIGHT] measures for the peek above.
        sheetDragHandle = { SheetDragHandle() },
        sheetContent = {
            // Sized so handle + content == fullHeight, which is what sets the sheet's expanded anchor.
            // Non-negative by construction: fullHeight is floored at the peek, which is this same
            // handle band plus navBottom, so the subtraction leaves at least the nav padding below.
            TripResultsSheet(
                itineraries = itineraries,
                params = params,
                resultsViewModel = resultsViewModel,
                showItinerary = showItinerary,
                onFocusRouteLeg = onFocusRouteLeg,
                onFocusLeg = onFocusLeg,
                onFocusPoint = onFocusPoint,
                stopEtaStrip = stopEtaStrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fullHeight - DRAG_HANDLE_TOUCH_TARGET_HEIGHT)
                    .navigationBarsPadding()
            )
        },
        // Sheet-only host: no snackbar (the caller owns one), no body, and a transparent container so
        // the caller's map stays visible and interactive behind it.
        snackbarHost = {},
        containerColor = Color.Transparent,
        content = {}
    )
}

/**
 * The inline ETA strip shown under a transit leg's Board / Alight row: the live arrivals for that leg's
 * route at [stop], rendered as the same horizontally-scrollable pill strip the arrivals drawer uses (tap
 * a pill to focus its vehicle, long-press for the trip menu — wired through [rememberArrivalRowCallbacks]).
 * Spins up a per-stop arrivals session keyed to [stop] — so it polls only while shown — and picks the
 * route group matching the leg (by route id, then headsign). Ids on [routeLeg]/[stop] are OBA-format.
 *
 * A leg with interchangeable routes ([RouteLegRef.alternatives], #2010) combines every matching route
 * into this one strip in arrival order. Each ETA pill carries its route's small badge, so the rider can
 * scan one timeline to see which interchangeable route comes first without losing the route identity.
 * All routes read the one arrivals poll this stop already runs, so alternatives cost no extra request.
 * An alternative with no OBA id, or with nothing upcoming at this stop, is simply left out — its name
 * still appears on the card's "or …" line.
 *
 * The strip is live arrivals *as of now*, but the rider is somewhere up the plan, so [reachStopTime] —
 * when the plan has them reach this stop — is ruled across it (#2125): departures before it are ones
 * they can't be here for, and the first pill after the rule is the soonest one they can actually board.
 * Null when the plan puts nothing before this ride (the rider is at the stop from the start, so every
 * departure is theirs to take) — the strip then draws no rule rather than one placed at a guess.
 */
@Composable
internal fun DirectionStopEtaStrip(
    routeLeg: RouteLegRef,
    stop: RouteStopRef,
    reachStopTime: ServerTime?,
    arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    onShowTrip: (tripId: String, stopId: String) -> Unit,
    onEditReminder: (ReminderEditorArgs) -> Unit,
    onFocusVehicle: (ShowRouteRequest) -> Unit,
    modifier: Modifier = Modifier,
    hoistedSession: ArrivalsSession? = null
) {
    val stopId = stop.stopId
    val point = stop.point
    // Left-justified to the content column; the old start indent was a holdover from the indented-sub-row design.
    val rowPadding = Modifier.fillMaxWidth().padding(end = 12.dp, top = 2.dp, bottom = 8.dp)
    // Without an OBA id + location there is no stop to query, so draw nothing at all. "No upcoming
    // arrivals" is reserved for a stop we *did* look up (below) — saying it here would report an
    // unidentifiable stop as one with no service.
    if (stopId == null || point == null) return
    // The row's own session is requested unconditionally, with a null stop when HOME already hoisted one
    // for this stop — [rememberArrivalsSession] then builds nothing and returns null. Asking for it
    // behind an `if` instead would put the remembers on one branch of a condition that flips when focus
    // moves, which is exactly what Compose's slot table cannot follow.
    val ownSession = rememberArrivalsSession(
        focusedStop = if (hoistedSession == null) {
            FocusedStop(id = stopId, name = stop.name, code = stop.stopCode, point = point)
        } else {
            null
        },
        sheetVisible = true,
        arrivalsViewModelFactory = arrivalsViewModelFactory,
        tutorialState = null,
        onArrivalsLoaded = {},
        // A pill tap (via the shared handler's onFocusVehicleOnMap) lands here with a focusTripId
        // request — hand it to the host to focus/animate/ping that vehicle, reusing the arrivals path.
        revealRoute = { _, request -> onFocusVehicle(request) },
        onShowTrip = onShowTrip,
        onEditReminder = onEditReminder,
        showUndoSnackbar = { _, _, _ -> }
    )
    // The focused leg's row reads HOME's hoisted session, so a focused ride polls its boarding stop
    // exactly once and the map and this strip see the same arrivals.
    val session = hoistedSession ?: ownSession ?: return
    DirectionStopEtaStripContent(
        routeLeg = routeLeg,
        reachStopTime = reachStopTime,
        session = session,
        rowPadding = rowPadding,
        modifier = modifier
    )
}

/** The strip proper, over whichever session it was given. */
@Composable
private fun DirectionStopEtaStripContent(
    routeLeg: RouteLegRef,
    reachStopTime: ServerTime?,
    session: ArrivalsSession,
    rowPadding: Modifier,
    modifier: Modifier
) {
    val state by session.viewModel.state.collectAsStateWithLifecycle()
    val callbacks = rememberArrivalRowCallbacks(session.handler, session.viewModel)

    val content = state as? ArrivalsUiState.Content ?: return // nothing until the first load lands
    val plannedGroup = content.routeGroups.pickRoute(routeLeg.routeId, routeLeg.headsign)
    val alternativeGroups = routeLeg.alternatives.mapNotNull { alternative ->
        content.routeGroups.pickRoute(alternative.routeId, alternative.headsign)
            ?.let { alternative to it }
    }
    val routeTrips = buildList {
        plannedGroup?.let { add(routeLeg.etaPlannedBadge(it.representative.lineName) to it.trips) }
        alternativeGroups.forEach { (alternative, group) ->
            add(RouteBadge(alternative.shortName, alternative.routeColor) to group.trips)
        }
    }
    val interleaved = interleaveRouteItems(routeTrips) { it.displayTime.epochMs }
    if (interleaved.isEmpty()) {
        NoEtasText(rowPadding)
        return
    }
    val badgesByTrip = interleaved.associate { (trip, badge) -> trip to badge }
    EtaStrip(
        trips = interleaved.map { it.first },
        actionsFor = { content.actions[it.tripId] },
        callbacks = callbacks,
        modifier = modifier.then(rowPadding),
        routeBadgeFor = { badgesByTrip[it] },
        marker = reachStopTime?.let { rememberReachStopMarker(it) }
    )
}

/** The strip's "you get here at …" rule for [reachStopTime]. The clock string is memoized because the
 *  format call is locale work that only changes with the plan, not with each arrivals poll. */
@Composable
private fun rememberReachStopMarker(reachStopTime: ServerTime): EtaStripMarker {
    val context = LocalContext.current
    val clock = remember(reachStopTime, context) { DisplayFormat.formatTime(context, reachStopTime.epochMs) }
    return EtaStripMarker(
        at = reachStopTime,
        contentDescription = stringResource(R.string.directions_stop_eta_reach_stop, clock),
        passedStateDescription = stringResource(R.string.directions_stop_eta_departure_missed)
    )
}

/**
 * Flattens route-specific, already-ordered arrival lists into one chronological strip. Kotlin's sort
 * is stable, so equal-time arrivals retain route order (planned first, then alternatives), avoiding
 * visual jitter when two interchangeable routes share an ETA.
 */
internal fun <T> interleaveRouteItems(
    routes: List<Pair<RouteBadge, List<T>>>,
    timeOf: (T) -> Long
): List<Pair<T, RouteBadge>> = routes
    .flatMap { (badge, items) -> items.map { it to badge } }
    .sortedBy { (item, _) -> timeOf(item) }

/** The planned route's own badge, falling back to the arrivals display name only for legacy fixtures.
 * It is carried explicitly rather than recovered from the joined badge by name: distinct routes may
 * publish the same name, and the joined badge deliberately deduplicates those names. */
internal fun RouteLegRef.etaPlannedBadge(fallbackLineName: String): RouteBadge = plannedBadge ?: RouteBadge(fallbackLineName, null)

/** The group for [routeId] at this stop: matched by OBA route id, preferring [headsign]'s direction.
 *  Null when the route has no OBA id (unresolved) or nothing upcoming at the stop.
 *
 *  Delegates to [pickRideDirection] rather than repeating the rule, so the strip and the map's ride
 *  selection resolve a leg to the same direction group by construction. */
private fun List<RouteRowGroup>.pickRoute(routeId: String?, headsign: String?): RouteRowGroup? = pickRideDirection(routeId, headsign, routeIdOf = { it.routeId }, headsignOf = { it.headsign })

@Composable
private fun NoEtasText(modifier: Modifier) {
    Text(
        text = stringResource(R.string.directions_stop_no_arrivals),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * Stable handles for [DirectionsLongPressMenu]'s endpoint dots, so a render can sample the dot
 * itself rather than guessing at Material's menu-row padding.
 */
object DirectionsLongPressMenuTestTags {
    const val FROM_DOT = "directionsFromHereDot"
    const val TO_DOT = "directionsToHereDot"
}

/**
 * The menu shown when the user long-presses the map: "directions from here" / "directions to here",
 * each of which enters directions focus and fills that endpoint with the pressed point.
 *
 * The same centered dialog every other long-press menu in the app uses (#2112), rather than the
 * bottom sheet it was: a long press means the same thing wherever the rider does it, and the map is
 * the one surface where a sheet rising from the bottom also covers what was just pressed. The rows
 * are marked with the trip-plan rail's own endpoint dots, so the row names the end of the trip it
 * fills by the same green/red the form will show once it is filled.
 *
 * Always expanded — the host renders this only while there is a pressed point, and dismissal clears
 * it (see HomeScreen).
 */
@Composable
fun DirectionsLongPressMenu(
    onChooseSlot: (TripEndpointSlot) -> Unit,
    onDismiss: () -> Unit
) {
    CenteredLongPressMenu(expanded = true, onDismissRequest = onDismiss) {
        MenuRow(
            textRes = R.string.directions_from_here,
            leadingIcon = {
                TripEndpointDotIcon(
                    TripEndpointSlot.FROM,
                    Modifier.testTag(DirectionsLongPressMenuTestTags.FROM_DOT)
                )
            },
            onClick = { onChooseSlot(TripEndpointSlot.FROM) }
        )
        MenuRow(
            textRes = R.string.directions_to_here,
            leadingIcon = {
                TripEndpointDotIcon(
                    TripEndpointSlot.TO,
                    Modifier.testTag(DirectionsLongPressMenuTestTags.TO_DOT)
                )
            },
            onClick = { onChooseSlot(TripEndpointSlot.TO) }
        )
    }
}

/**
 * The trip-plan error surface: a Material 3 snackbar (the `inverseSurface` container, seated at the
 * bottom of the map where the results sheet would be). It shows a stable, severity-coloured header
 * naming the category (every no-route failure reads "Cannot find route") over a single reason line —
 * the exact wire message — with an explicit dismiss. Unlike a toast or an auto-dismissing snackbar it
 * persists until the user dismisses it (or the plan state changes), so a deterministic failure —
 * outside coverage, no route, no service at this time — isn't lost if the user glances away. Header =
 * the "richer ontology" ([TripPlanError.category]); reason = the specifics ([TripPlanError.detailRes]).
 */
@Composable
fun DirectionsErrorSnackbar(
    error: TripPlanError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        // A polite live region so a screen reader announces a newly surfaced planning failure.
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(error.category.headerRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorResource(error.category.severity.colorRes)
                )
                Text(
                    text = stringResource(error.detailRes),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.9f)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

/**
 * "Leave these directions?" — the modal shown when a gesture would close directions on a trip that is
 * drawn on the map (#2140). A planned trip is several deliberate acts (two endpoints, a time, a mode,
 * the option picked from the results), and the two gestures that discard it — Back, and a tap on the
 * map background — are the two cheapest gestures on the screen, so it was far too easy to spend the
 * whole plan by accident. Only a drawn trip is worth the interruption: an unplanned form still leaves
 * on the first gesture (see [org.onebusaway.android.ui.home.HomeViewModel.pendingDirectionsExit]).
 *
 * The confirm button is the destructive one, so it names what it does ("Discard") rather than "OK";
 * dismissing — the cancel button, an outside tap, or Back — keeps the trip.
 */
@Composable
fun DirectionsExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.directions_exit_confirm_title)) },
        text = { Text(stringResource(R.string.directions_exit_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.directions_exit_confirm_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Pick a From/To point directly on the home map: a fixed center crosshair the map pans under, and a
 * bottom confirm. [onConfirm] captures the map's current center; the caller resolves it to a
 * [org.onebusaway.android.ui.tripplan.TripEndpoint.MapPoint].
 *
 * Nothing is drawn at the top. A labelled From/To card used to sit there carrying a ✕, but the map is
 * the thing being read during a pick and the card only covered it; back cancels, as it does everywhere
 * else in directions.
 */
@Composable
fun BoxScope.DirectionsPickOverlay(onConfirm: () -> Unit) {
    // Fixed, non-interactive center crosshair: the map pans under it and its center is the chosen point.
    Icon(
        painter = painterResource(R.drawable.ic_my_location),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.align(Alignment.Center).size(48.dp)
    )
    Button(
        onClick = onConfirm,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Text(stringResource(R.string.trip_plan_use_this_location))
    }
}

/** Advanced trip options (travel-by mode, max walk, minimize transfers, wheelchair). */
@Composable
private fun DirectionsAdvancedSettingsDialog(
    viewModel: TripPlanViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imperial = !unitsAreMetric()
    // Which options this region can actually serve. OTP2 deleted `maxWalkDistance` from its routing
    // API, so the distance field would be silently ignored there (#1780 wired the OTP2 path without
    // it); OTP1 in turn has no cycling-optimization knob that doesn't collide with the `optimize`
    // parameter "Minimize transfers" already uses, and no verified way to carry the rider's own bike.
    // Rather than show a control the server will drop, each protocol gets the ones it honours.
    val usesOtp2 = remember { OtpTarget.resolve(context).usesOtp2 }
    val bikeshare = remember { BikeshareAvailability.isTripPlanningEnabled(context) }

    val current = remember { viewModel.formState.value }
    val offeredStreetMode = current.modes.street
        .takeIf { it.isAvailableIn(bikeshare, usesOtp2) } ?: StreetMode.WALK
    var minimizeTransfers by remember { mutableStateOf(current.optimizeTransfers) }
    var wheelchair by remember { mutableStateOf(current.wheelchair) }
    // Rounded, not truncated: the field's value is converted back to metres on confirm, so
    // truncating here would shave a little off the stored distance every time the dialog is opened
    // and confirmed (1600 m -> 5249 ft -> 1599.8 m -> 5248 ft -> …).
    var maxWalk by remember {
        mutableStateOf(
            current.maxWalkMeters?.let {
                (if (imperial) ConversionUtils.metersToFeet(it) else it).roundToLong().toString()
            }.orEmpty()
        )
    }
    var walkPreference by remember { mutableStateOf(current.walkPreference) }
    var cyclingPreference by remember { mutableStateOf(current.cyclingPreference) }
    var bikePreference by remember { mutableStateOf(current.bikePreference) }

    // One shared five-stop label set, ordered least -> most. Generated from the enums rather than
    // written out, so the slider's index->label mapping can't drift from the declaration order it
    // depends on, and the `when`s below are exhaustive, so adding a stop to either enum is a compile
    // error here rather than a silently mislabelled slider.
    val walkStopLabels = WalkPreference.entries.map { it.label() }
    val bikeStopLabels = BikePreference.entries.map { it.label() }
    val cyclingOptions = listOf(
        stringResource(R.string.cycling_preference_default) to CyclingPreference.DEFAULT,
        stringResource(R.string.cycling_preference_fastest) to CyclingPreference.FASTEST,
        stringResource(R.string.cycling_preference_safest) to CyclingPreference.SAFEST,
        stringResource(R.string.cycling_preference_flattest) to CyclingPreference.FLATTEST
    )

    // Preference keys resolved in composition (stringResource) so the confirm handler doesn't read
    // resource values off LocalContext.current (lint: LocalContextGetResourceValueCall).
    val prefKeyMaxWalk = stringResource(R.string.preference_key_trip_plan_maximum_walking_distance)
    val prefKeyWalkPreference = stringResource(R.string.preference_key_trip_plan_walk_preference)
    val prefKeyCyclingPreference = stringResource(R.string.preference_key_trip_plan_cycling_preference)
    val prefKeyBikePreference = stringResource(R.string.preference_key_trip_plan_bike_preference)
    val prefKeyMinimizeTransfers = stringResource(R.string.preference_key_trip_plan_minimize_transfers)
    val prefKeyAvoidStairs = stringResource(R.string.preference_key_trip_plan_avoid_stairs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_plan_advanced_settings)) },
        text = {
            // Scrollable: the OTP2 branch adds two sliders and a dropdown on top of the existing
            // rows, and AlertDialog content doesn't scroll on its own — at large font scales the
            // switches at the bottom would otherwise be unreachable.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (usesOtp2) {
                    PreferenceSliderRow(
                        label = stringResource(R.string.walk_preference_label),
                        stopLabels = walkStopLabels,
                        selectedIndex = walkPreference.ordinal,
                        onSelectedIndex = { walkPreference = WalkPreference.entries[it] },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // Only meaningful when the plan can contain a bike leg at all — the offered mode,
                    // since a stored one this region can't serve won't produce one.
                    if (offeredStreetMode.usesBike) {
                        PreferenceSliderRow(
                            label = stringResource(R.string.bike_preference_label),
                            stopLabels = bikeStopLabels,
                            selectedIndex = bikePreference.ordinal,
                            onSelectedIndex = { bikePreference = BikePreference.entries[it] },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        PreferenceDropdownRow(
                            label = stringResource(R.string.cycling_preference_label),
                            options = cyclingOptions,
                            selected = cyclingPreference,
                            onSelected = { cyclingPreference = it },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.maximum_walk_distance), modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = maxWalk,
                            onValueChange = { new -> maxWalk = new.filter { it.isDigit() } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(
                                if (imperial) R.string.feet_abbreviation else R.string.meters_abbreviation
                            )
                        )
                    }
                }
                SwitchRow(
                    label = stringResource(R.string.minimize_transfers),
                    checked = minimizeTransfers,
                    onCheckedChange = { minimizeTransfers = it }
                )
                SwitchRow(
                    label = stringResource(R.string.wheelchair_accessible),
                    checked = wheelchair,
                    onCheckedChange = { wheelchair = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // On an OTP2 region the metres field was never shown, so there is no edited value to
                // read: carry the stored one through untouched rather than round-tripping it through
                // an unshown field, which would erode a setting the rider can't see.
                val maxWalkMeters: Double? = if (usesOtp2) {
                    current.maxWalkMeters
                } else {
                    maxWalk.takeIf { it.isNotEmpty() }?.toDouble()?.let {
                        if (imperial) ConversionUtils.feetToMeters(it) else it
                    }
                }
                viewModel.applyAdvancedSettings(
                    AdvancedSettings(
                        modes = current.modes,
                        maxWalkMeters = maxWalkMeters,
                        optimizeTransfers = minimizeTransfers,
                        wheelchair = wheelchair,
                        walkPreference = walkPreference,
                        cyclingPreference = cyclingPreference,
                        bikePreference = bikePreference
                    )
                )
                // Every option is saved whether or not this region's protocol showed its control, so
                // the value survives a move to a region that can use it (see [AdvancedSettings]).
                PreferenceUtils.saveDouble(prefKeyMaxWalk, maxWalkMeters ?: Double.MAX_VALUE)
                PreferenceUtils.saveString(prefKeyWalkPreference, walkPreference.name)
                PreferenceUtils.saveString(prefKeyCyclingPreference, cyclingPreference.name)
                PreferenceUtils.saveString(prefKeyBikePreference, bikePreference.name)
                PreferenceUtils.saveBoolean(prefKeyMinimizeTransfers, minimizeTransfers)
                PreferenceUtils.saveBoolean(prefKeyAvoidStairs, wheelchair)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        }
    )
}

/** The rider-facing name of this stop. Exhaustive so a new stop can't be added without a label. */
@Composable
private fun WalkPreference.label(): String = when (this) {
    WalkPreference.MINIMUM -> stringResource(R.string.street_preference_minimum)
    WalkPreference.LOW -> stringResource(R.string.street_preference_low)
    WalkPreference.MEDIUM -> stringResource(R.string.street_preference_medium)
    WalkPreference.HIGH -> stringResource(R.string.street_preference_high)
    WalkPreference.MAXIMUM -> stringResource(R.string.street_preference_maximum)
}

/** As [WalkPreference.label]; the two scales share one set of stop names. */
@Composable
private fun BikePreference.label(): String = when (this) {
    BikePreference.MINIMUM -> stringResource(R.string.street_preference_minimum)
    BikePreference.LOW -> stringResource(R.string.street_preference_low)
    BikePreference.MEDIUM -> stringResource(R.string.street_preference_medium)
    BikePreference.HIGH -> stringResource(R.string.street_preference_high)
    BikePreference.MAXIMUM -> stringResource(R.string.street_preference_maximum)
}

/**
 * A labelled discrete slider for an ordinal preference: the label with the current stop's name
 * right-aligned above the track, and the two extremes named beneath it.
 *
 * [stopLabels] is ordered least → most and its indices are the enum's `ordinal`s, so the slider
 * position *is* the enum value — no separate value table to keep in step. [Slider.steps] counts the
 * stops *between* the ends, hence `size - 2`.
 */
@Composable
private fun PreferenceSliderRow(
    label: String,
    stopLabels: List<String>,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(
                stopLabels.getOrElse(selectedIndex) { "" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { onSelectedIndex(it.roundToInt().coerceIn(stopLabels.indices)) },
            valueRange = 0f..(stopLabels.size - 1).toFloat(),
            steps = stopLabels.size - 2,
            // The stop name lives in a sibling Text, so without these a screen reader announces the
            // raw position ("2 of 4") and nothing about what the control is.
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = stopLabels.getOrElse(selectedIndex) { "" }
            }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stopLabels.first(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                stopLabels.last(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A labelled dropdown row in the advanced-settings dialog: the label, then a button showing the
 * selected option's label that opens the menu. [options] pairs each display label with the value it
 * selects; the button falls back to blank if [selected] isn't among them.
 */
@Composable
private fun <T> PreferenceDropdownRow(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(options.firstOrNull { it.second == selected }?.first.orEmpty())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (optionLabel, value) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
