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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.RideRouteGroup
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.models.WheelchairBoarding
import org.onebusaway.android.ui.arrivals.ArrivalsLoaded
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.ListUiState
import org.onebusaway.android.ui.compose.components.DRAG_HANDLE_HEIGHT
import org.onebusaway.android.ui.compose.components.DRAG_HANDLE_VERTICAL_PADDING
import org.onebusaway.android.ui.compose.components.DragHandleBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.home.arrivals.ArrivalsSheetHost
import org.onebusaway.android.ui.home.arrivals.ServiceAlertsDialog
import org.onebusaway.android.ui.home.arrivals.rememberArrivalsSession
import org.onebusaway.android.ui.home.chrome.MAP_TOP_CHROME_CLEARANCE
import org.onebusaway.android.ui.home.chrome.MapTopChrome
import org.onebusaway.android.ui.home.chrome.mapTopChromeOverlayInset
import org.onebusaway.android.ui.home.directions.DirectionStopEtaStrip
import org.onebusaway.android.ui.home.directions.DirectionsErrorSnackbar
import org.onebusaway.android.ui.home.directions.DirectionsExitConfirmDialog
import org.onebusaway.android.ui.home.directions.DirectionsFormCard
import org.onebusaway.android.ui.home.directions.DirectionsLongPressMenu
import org.onebusaway.android.ui.home.directions.DirectionsPickOverlay
import org.onebusaway.android.ui.home.directions.DirectionsResultsSheet
import org.onebusaway.android.ui.home.directions.itineraryPins
import org.onebusaway.android.ui.home.directions.pinPoint
import org.onebusaway.android.ui.home.donation.DonationFeature
import org.onebusaway.android.ui.home.donation.DonationViewModel
import org.onebusaway.android.ui.home.drawer.HomeNavDrawerSheet
import org.onebusaway.android.ui.home.drawer.NavDrawerViewModel
import org.onebusaway.android.ui.home.help.HelpAction
import org.onebusaway.android.ui.home.help.HelpFeature
import org.onebusaway.android.ui.home.help.HelpViewModel
import org.onebusaway.android.ui.home.map.FocusBanner
import org.onebusaway.android.ui.home.map.FocusBannerState
import org.onebusaway.android.ui.home.map.FocusBannerViewModel
import org.onebusaway.android.ui.home.map.MapChrome
import org.onebusaway.android.ui.home.map.MapFeature
import org.onebusaway.android.ui.home.nearby.NearbyArrivalsSheetHost
import org.onebusaway.android.ui.home.nearby.NearbyArrivalsViewModel
import org.onebusaway.android.ui.home.nearby.limitExceeded
import org.onebusaway.android.ui.home.nearby.rememberNearbyRouteRows
import org.onebusaway.android.ui.home.nearby.rememberNearbyRowActions
import org.onebusaway.android.ui.home.nearby.rememberNearbyRowCallbacks
import org.onebusaway.android.ui.home.weather.WeatherFeature
import org.onebusaway.android.ui.home.weather.WeatherViewModel
import org.onebusaway.android.ui.home.widealert.WideAlertDialog
import org.onebusaway.android.ui.home.widealert.WideAlertViewModel
import org.onebusaway.android.ui.mylists.RecentItem
import org.onebusaway.android.ui.mylists.SearchRecentsRepository
import org.onebusaway.android.ui.mylists.rememberListVm
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.ui.survey.SurveyFeature
import org.onebusaway.android.ui.survey.SurveyViewModel
import org.onebusaway.android.ui.tripplan.PlanResult
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripEndpointSlot
import org.onebusaway.android.ui.tripplan.TripPlanViewModel
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripViewModel
import org.onebusaway.android.ui.tripplan.pinned.describesSameTripAs
import org.onebusaway.android.ui.tripresults.TripResultsUiState
import org.onebusaway.android.ui.tripresults.TripResultsViewModel
import org.onebusaway.android.ui.tutorial.ArrivalTutorial
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.TutorialOverlay
import org.onebusaway.android.ui.tutorial.WelcomeTutorial
import org.onebusaway.android.ui.tutorial.rememberTutorialState
import org.onebusaway.android.ui.tutorial.tutorialAnchor
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.GeoPoint

/**
 * The home screen's tap/UI callbacks, bundled into one holder (mirrors [org.onebusaway.android.ui.survey.SurveyCallbacks]) so
 * [HomeScreen]'s signature stays a handful of parameters — state + the map/survey plumbing + this —
 * instead of ~30 individual lambdas. Each is dispatched up to HomeActivity or a view model.
 *
 * Composed in two halves at the HOME composable: the Activity-bound actions arrive whole as
 * [activityActions] (held, not re-flattened, so adding one there can't be silently dropped here), and
 * the navigation lambdas below are built where the NavController is in scope. [HomeScreen] brings both
 * into scope via nested `with`, so the body references every callback unqualified.
 */
class HomeCallbacks(
    val activityActions: HomeActivityActions,
    // One onClick per drawer row — the content rows (starred/reminders) navigate to their
    // destinations, the action rows navigate/launch. None are "selections": the NavHost's current
    // destination is the source of truth for what's shown.
    val onStarredStops: () -> Unit,
    val onStarredRoutes: () -> Unit,
    val onReminders: () -> Unit,
    val onPlanTrip: () -> Unit,
    val onSettings: () -> Unit,
    val onSearch: (String) -> Unit,
    val onRecentStopsRoutes: () -> Unit,
    // Search-box recents dropdown: tapping a stop or a route reveals it on the map.
    val onRecentStop: (id: String, lat: Double, lon: Double) -> Unit,
    val onRecentRoute: (routeId: String) -> Unit,
    // Wraps [HomeActivityActions.onHelpActionExternal] with the one branch that's a navigation (AGENCIES).
    val onHelpAction: (HelpAction) -> Unit,
    val onShowTrip: (tripId: String, stopId: String) -> Unit,
    val onEditReminder: (args: ReminderEditorArgs) -> Unit,
    val onLearnMore: () -> Unit,
    val onOpenSurvey: (url: String) -> Unit
)

/**
 * The home callbacks that are genuinely Activity operations — the ones that need `ExternalIntents` /
 * `ReportLauncher` / `startActivity` or are thin forwards to an Activity-owned ViewModel. Built once by
 * [org.onebusaway.android.ui.HomeActivity] and combined, in the HOME composable, with the navigation
 * lambdas (which need the NavController) to form the full [HomeCallbacks]. [onHelpActionExternal] handles
 * every [HelpAction] branch except `AGENCIES` (a navigation, supplied by the composable).
 */
class HomeActivityActions(
    val onPayFare: () -> Unit,
    val onHelp: () -> Unit,
    val onSendFeedback: () -> Unit,
    val onOpenSource: () -> Unit,
    val onHelpActionExternal: (HelpAction) -> Unit,
    val onShowWelcomeTutorial: () -> Unit,
    val onSheetSettled: (ArrivalsSheetState, Int) -> Unit,
    val onArrivalsLoaded: (ArrivalsLoaded) -> Unit
)

/**
 * The declarative home screen: a Compose `ModalNavigationDrawer` + an edge-to-edge Material3
 * `BottomSheetScaffold` (the map) with the floating [MapTopChrome] (menu + search FABs) over its top,
 * rendered from [CurrentFocus] (state down) with taps dispatched through plain lambda callbacks +
 * [HomeViewModel] events (up). Replaces the imperative `HomeShellHost` bridge.
 *
 * The arrivals sheet inverts to declarative: **visibility is business state** — the sheet peeks iff
 * a stop is focused on NEARBY — driven by a [LaunchedEffect] keyed on that derived flag, so it never
 * fights a user drag. The sheet has no `Hidden` drag anchor (`skipHiddenState = true`), so peek is the
 * hard floor of the drag; show/hide is instead an animated peek height (0 <-> real peek) that slides
 * the whole sheet in and out. **Expansion (peek<->full)** is the live `SheetState`, toggled by the drag
 * handle and collapsed as a declarative reaction to a route being selected inside stop focus;
 * the screen alone knows the live state), plus [BackHandler]. The arrivals panel is hosted directly per focused stop (see [ArrivalsSheetHost]);
 * the map ([MapFeature]), shared focus banner ([FocusBanner]), and survey ([org.onebusaway.android.ui.survey.SurveyOverlay])
 * are all composables now — no map-related `AndroidView` / View seam remains.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentFocus: CurrentFocus,
    // The map is a self-wiring [MapFeature]; it composes only while HOME is the current destination, so
    // SDK init is already lazy. The route-mode header and survey are Compose overlays over it.
    homeViewModel: HomeViewModel,
    mapViewModel: MapViewModel,
    routeHeader: RouteHeader?,
    surveyViewModel: SurveyViewModel,
    donationViewModel: DonationViewModel,
    weatherViewModel: WeatherViewModel,
    helpViewModel: HelpViewModel,
    // The trip planner, hosted on HOME (directions focus): the compact form replaces the search field in
    // the top chrome and the results sheet + itinerary render over the map.
    tripPlanViewModel: TripPlanViewModel,
    tripResultsViewModel: TripResultsViewModel,
    // The parked trip plan (#2053): read from the results sheet's pin controls and from the resume card
    // over the map, so it is one activity-scoped instance rather than a per-destination one.
    pinnedTripViewModel: PinnedTripViewModel,
    // Builds the per-focused-stop ArrivalsViewModel for the bottom-sheet host (assisted-injected;
    // the sheet's stop id is runtime-dynamic, so it can't be a plain hiltViewModel). Injected into
    // HomeActivity and threaded down.
    arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    // All the screen's tap/UI lambdas, bundled (see [HomeCallbacks]); brought into scope below via
    // `with` so the body references them unqualified.
    callbacks: HomeCallbacks
) {
    with(callbacks) {
        with(activityActions) {
            ObaTheme {
                val stopFocus = currentFocus as? CurrentFocus.Stop
                val canUndoMapAction by homeViewModel.canUndoMapAction.collectAsStateWithLifecycle()
                val mapRouteColors by mapViewModel.focusedRouteColors.collectAsStateWithLifecycle()
                val selectedTripBandColor by mapViewModel.selectedTripBandColor.collectAsStateWithLifecycle()
                val focusBannerViewModel = hiltViewModel<FocusBannerViewModel>()
                // The transit-centre drawer's query (#2107). Created here — the sheet is this screen's
                // — and fed the settled viewport + zoom band by MapFeature, which holds the map VM.
                val nearbyArrivalsViewModel = hiltViewModel<NearbyArrivalsViewModel>()
                val nearbyArrivalsState by nearbyArrivalsViewModel.state.collectAsStateWithLifecycle()
                // The map's zoom band, read back off the nearby query rather than derived a second time
                // here: MapFeature holds the map VM and is the single producer, pushing the band into
                // the query — so the sheet decision and the query it gates can't drift.
                val stopBand by nearbyArrivalsViewModel.stopBand.collectAsStateWithLifecycle()
                val favoriteRouteIds by focusBannerViewModel.favoriteRouteIds.collectAsStateWithLifecycle()
                val favoriteStopIds by focusBannerViewModel.favoriteStopIds.collectAsStateWithLifecycle()
                val stopFavoritesReady by focusBannerViewModel.stopFavoritesReady.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                val density = LocalDensity.current
                val resources = LocalResources.current
                // Compute before entering mapTopChromeOverlayInset(), whose statusBarsPadding consumes this inset
                // for descendants. This is the route card's absolute top edge in map coordinates.
                val focusBannerTopPx = WindowInsets.statusBars.getTop(density) +
                    with(density) { MAP_TOP_CHROME_CLEARANCE.roundToPx() }
                // Unkeyed: FocusBanner only reports height via onSizeChanged (fires on size *change*), so keying
                // on currentFocus would reset this to 0 when switching between two equal-height banners, framing
                // the map as if no banner showed. The disappearance case resets it via the banner's else branch.
                var focusBannerBottomPx by remember { mutableIntStateOf(0) }
                // The directions trip-plan form card's absolute bottom edge (window px), so the map's top inset
                // covers the form/FAB during directions (the itinerary-step focus centers in the band below it).
                var directionsFormBottomPx by remember { mutableIntStateOf(0) }
                // Which endpoint (if any) is being picked directly on the map (crosshair + confirm). Lives
                // up here because the map's padding depends on it, just below.
                var pickTarget by rememberSaveable { mutableStateOf<TripEndpointSlot?>(null) }
                val focusTopEdgePx = focusBannerTopEdge(
                    currentFocus,
                    focusBannerBottomPx,
                    directionsFormBottomPx
                )
                LaunchedEffect(focusTopEdgePx) {
                    mapViewModel.setFocusBannerBottomEdge(focusTopEdgePx)
                }
                // Aiming the centre crosshair suspends map padding entirely: the point a pick captures is
                // the camera target, and padding moves that off the crosshair the rider is aiming.
                LaunchedEffect(pickTarget) {
                    mapViewModel.setCenterPickActive(pickTarget != null)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                // The unified recent stops+routes list for the search field's dropdown. Hosted here (like the
                // My-tab lists, via rememberListVm) so MapTopChrome stays a pure, VM-free chrome composable;
                // empty until it resolves.
                val app = LocalContext.current.findActivity().applicationContext
                val searchRecents = rememberListVm("home.searchRecents") { SearchRecentsRepository(app) }
                val recents: List<RecentItem> =
                    (searchRecents.state.collectAsStateWithLifecycle().value as? ListUiState.Success)?.items
                        .orEmpty()
                // Drives the arrivals-panel onboarding spotlight; provided to the sheet content (so the panel's
                // anchors can register) and read by [TutorialOverlay] below, which draws over the whole screen.
                val tutorialState = rememberTutorialState()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                // The sheet has NO reachable `Hidden` anchor (`skipHiddenState = true`), so peek is the hard
                // floor of the drag: the user can expand from peek or collapse back to it, but can never drag it
                // below peek (which used to let the pinned peek content slide off-screen and snap back). Show /
                // hide is therefore not a drag state — it's driven by animating the peek height between 0 and the
                // real peek (see `visiblePeekDp` / `sheetShown` below), which slides the whole sheet in and out.
                val sheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded,
                    skipHiddenState = true
                )
                val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

                // Tapping the drag handle toggles the live sheet between peek and full (a full sheet collapses
                // to peek, anything else expands). This lives next to the SheetState now — the header used to
                // trigger it via a VM round-trip, needed only because the header was in a different composable
                // tree; the handle is right here, so it toggles directly.
                val toggleSheet: () -> Unit = remember {
                    {
                        scope.launch {
                            runCatching {
                                when (toggleSheetTarget(sheetState.currentValue.toArrivalsSheetState())) {
                                    ArrivalsSheetState.Expanded -> sheetState.expand()
                                    else -> sheetState.partialExpand()
                                }
                            }
                        }
                    }
                }

                // Drag the sheet down to peek. Unlike the declarative routeModeActive effect below (which only
                // fires on the off->on route-mode transition), this is a per-tap action, so it also drags an
                // already-expanded sheet down when the user taps a "show vehicles on map" row while route mode
                // is already active for another route.
                val collapseSheet: () -> Unit = remember {
                    { scope.launch { runCatching { sheetState.partialExpand() } } }
                }

                // Opening the nav drawer from the menu FAB — remembered so the frequently-recomposing screen body
                // (it reads the animated sheet peek) doesn't hand MapTopChrome a fresh lambda each frame.
                val openDrawer: () -> Unit = remember {
                    { scope.launch { drawerState.open() } }
                }

                // The system navigation-bar inset (height varies by handset) grows the peek so the collapsed
                // sheet's content clears the bottom chrome; the panel matches this with its own content inset.
                val peekBottomPadding = navigationBarBottomPadding()

                // The expanded sheet's ceiling, so its top edge stops below the status bar / notch instead
                // of sliding under it. Material3 derives the Expanded anchor from the sheet's *measured*
                // height (anchor y = containerHeight - sheetHeight), so a content list that measures the
                // full window pins the top edge at y=0 — capping the content is what stops it short; there
                // is no sheet-max-height parameter to set. safeDrawing rather than statusBars because the
                // cutout is what we're clearing, and it can exceed the status bar. The drag handle sits
                // above this slot inside the same sheet Surface, so it comes out of the same budget.
                val topSystemInsetPx = WindowInsets.safeDrawing.getTop(density)
                val maxSheetContentDp = with(density) {
                    val availablePx = LocalWindowInfo.current.containerSize.height - topSystemInsetPx
                    availablePx.toDp() - DRAG_HANDLE_HEIGHT
                }

                // The panel's fully-laid-out list height in px, reported once
                // measured (0 until then). Used only to shrink the peek below the cap for short stops. Not reset
                // on focus change — the next stop's panel overwrites it once laid out, avoiding a cap-bounce.
                var contentPx by remember { mutableIntStateOf(0) }
                // That content height as the on-screen peek it implies: the measured content plus the drag handle
                // above it and the nav-bar inset below (matching what the collapsed sheet actually shows).
                val contentPeekDp = with(density) { contentPx.toDp() } + DRAG_HANDLE_HEIGHT + peekBottomPadding

                // The transit-centre drawer's rows, built from the query's last response (#2107). Needed
                // before the sheet decision below, which gates on there being rows to show.
                val nearbyRows = rememberNearbyRouteRows(nearbyArrivalsState)
                val nearbyActionsFor = rememberNearbyRowActions(nearbyArrivalsState, nearbyRows)
                val nearbyLimitExceeded = nearbyArrivalsState.limitExceeded
                val nearbyRowCallbacks = rememberNearbyRowCallbacks(
                    homeViewModel = homeViewModel,
                    rows = nearbyRows,
                    undoViewport = { mapViewModel.viewport },
                    onShowTrip = onShowTrip
                )

                // Visibility is business state: the sheet is shown (its peek slid up) iff it has something
                // to show — a focused stop, or (since #2107) the routes leaving every bay in view at
                // transit-centre zoom. Because there's no `Hidden` drag anchor, "shown" is a plain flag that
                // drives the animated peek height rather than a sheet drag state. The key is the *identity*
                // of what's shown, so the effect reacts to focus/mode changes but NOT to a user drag, and
                // not to a pan that re-queries the same nearby list.
                var sheetShown by remember { mutableStateOf(false) }
                val sheetContent = homeSheetContent(currentFocus, stopBand, nearbyRows.isNotEmpty())
                val sheetKey = sheetContent.sheetKey
                LaunchedEffect(sheetKey) {
                    if (sheetKey == null) {
                        // Hide: an expanded sheet is first collapsed to peek (so it then slides straight down as
                        // the peek retracts, rather than staying stuck at the top with no `Hidden` anchor to fall
                        // to); peek == the current value otherwise, so this is a no-op there.
                        runCatching {
                            if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand()
                        }
                        sheetShown = false
                    } else {
                        // Show immediately — a fixed-fraction peek can't strand the drag, so there's no need to
                        // wait for arrivals; the peek shows a loading spinner until they land. Collapse to peek
                        // on the way in, so switching *between* modes (a stop tapped out of the nearby list, or
                        // back again) doesn't silently hand a full-height sheet to entirely different content.
                        runCatching {
                            if (sheetShown && sheetState.currentValue == SheetValue.Expanded) {
                                sheetState.partialExpand()
                            }
                        }
                        sheetShown = true
                    }
                }

                // Tell the query whether the drawer is the sheet's subject: focusing a stop hands the sheet
                // to that stop's own arrivals session, so this stops polling a list nobody can see.
                // Keyed on the focus, not on sheetContent: the drawer's own rows feed sheetContent, so
                // keying on that would make this effect depend on its own output.
                LaunchedEffect(currentFocus, nearbyArrivalsViewModel) {
                    nearbyArrivalsViewModel.setActive(currentFocus is CurrentFocus.None)
                }

                // One keyed arrivals session feeds the focus banner, alert modal, and drawer body. Keeping it
                // above the scaffold prevents duplicate polling while preserving the per-stop ViewModelStore.
                val arrivalsSession = rememberArrivalsSession(
                    focusedStop = stopFocus?.stop,
                    sheetVisible = sheetShown,
                    arrivalsViewModelFactory = arrivalsViewModelFactory,
                    tutorialState = tutorialState,
                    onArrivalsLoaded = onArrivalsLoaded,
                    revealRoute = { arrival, request ->
                        homeViewModel.selectArrivalRoute(
                            request = request,
                            shortName = arrival.shortName.orEmpty().ifBlank { arrival.routeId },
                            headsign = arrival.headsign,
                            undoViewport = mapViewModel.viewport
                        )
                        collapseSheet()
                    },
                    onShowTrip = onShowTrip,
                    onEditReminder = onEditReminder,
                    showUndoSnackbar = { messageRes, actionRes, onAction ->
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = resources.getString(messageRes),
                                actionLabel = actionRes?.let { resources.getString(it) },
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
                        }
                    }
                )
                val arrivalsState = arrivalsSession?.viewModel?.state
                    ?.collectAsStateWithLifecycle()?.value ?: ArrivalsUiState.Loading
                val arrivalsContent = arrivalsState as? ArrivalsUiState.Content

                // The focused directions leg's boarding stop, hoisted for the same reason as the session
                // above — "prevents duplicate polling" — but with a second requirement: the map's ride
                // vehicle selection (#2124) reads these arrivals, and a session owned by the itinerary's
                // Board row would stop polling the moment that row scrolled out of the LazyColumn, which
                // would make what the map draws depend on where the sheet is scrolled. Null (and so
                // inert) outside a focused leg.
                val rideRouteFocus =
                    ((currentFocus as? CurrentFocus.Directions)?.subFocus as? DirectionsSubFocus.Route)
                val rideBoardStop = rideRouteFocus?.boardStop
                val rideArrivalsSession = rememberArrivalsSession(
                    focusedStop = rideBoardStop,
                    sheetVisible = true,
                    arrivalsViewModelFactory = arrivalsViewModelFactory,
                    tutorialState = null,
                    onArrivalsLoaded = {},
                    revealRoute = { _, request -> homeViewModel.focusDirectionsRouteVehicleInFocusedLeg(request) },
                    onShowTrip = onShowTrip,
                    onEditReminder = onEditReminder,
                    showUndoSnackbar = { _, _, _ -> }
                )
                // Reduced to the map's own shape here rather than in the view model, which stays free of
                // UI types (an ArrivalInfo needs a Context to build, which is what keeps HomeViewModel's
                // tests plain JVM ones).
                val rideArrivalGroups = (
                    rideArrivalsSession?.viewModel?.state
                        ?.collectAsStateWithLifecycle()?.value as? ArrivalsUiState.Content
                    )?.routeGroups?.map { group ->
                    RideRouteGroup(group.routeId, group.headsign, group.trips.map { it.tripId })
                }
                // The same stop session is deliberately retained when focus moves between rides that
                // board there. Key the hand-off on the ride as well as its data: entering the new route
                // resets its selection to Pending, so it needs the session's already-loaded rows even
                // when neither the stop id nor those rows changed.
                LaunchedEffect(rideRouteFocus, rideArrivalGroups) {
                    val stopId = rideBoardStop?.id ?: return@LaunchedEffect
                    homeViewModel.onRideArrivals(stopId, rideArrivalGroups ?: return@LaunchedEffect)
                }
                var serviceAlertsVisible by remember(stopFocus?.stop?.id) { mutableStateOf(false) }
                val focusBannerState: FocusBannerState? = when (currentFocus) {
                    is CurrentFocus.Stop -> FocusBannerState.Stop(
                        title = arrivalsContent?.header?.name?.takeIf { it.isNotBlank() }
                            ?: currentFocus.stop.name.orEmpty(),
                        direction = arrivalsContent?.header?.direction,
                        stopCode = arrivalsContent?.stopCode ?: currentFocus.stop.code,
                        // Star state + toggle come from the favorites store keyed by stop id, so the star
                        // works the instant a stop is focused rather than only after its arrivals load (#684).
                        // It's gated on the favorites store being ready (its one-time legacy import done),
                        // not on arrivals, so a legacy-starred stop is never shown unstarred (and thus
                        // un-unstarrable) during that window.
                        isFavorite = currentFocus.stop.id in favoriteStopIds,
                        favoriteEnabled = stopFavoritesReady,
                        hasAlerts = arrivalsContent?.hasAlerts == true,
                        // Like the stop code and direction above: the loaded arrivals are the source, with
                        // the focus as the pre-load fallback. Only a map tap mints a focus from a full
                        // ObaStop — a search result, deep link or directions stop carries just an id — so
                        // without the arrivals source the glyph would be missing on most entry points.
                        wheelchairBoarding = arrivalsContent?.header?.wheelchairBoarding
                            ?.takeIf { it != WheelchairBoarding.UNKNOWN }
                            ?: currentFocus.stop.wheelchairBoarding
                    )
                    is CurrentFocus.Route -> routeHeader?.let { header ->
                        FocusBannerState.Route(
                            header = header,
                            isFavorite = header.routeId in favoriteRouteIds
                        )
                    }
                    // Directions has no focus banner — the trip-plan form in the top chrome is its affordance.
                    CurrentFocus.None, is CurrentFocus.BikeStation, is CurrentFocus.Directions -> null
                }

                // Whether the reveal slide (peek 0 -> cap) has finished at a resting peek. The peek only shrinks
                // to fit short content once settled: retargeting mid-open would move the AnchoredDraggable anchor
                // and strand the sheet, so we slide up to the constant cap first, then shrink (flipped by the
                // animateDpAsState finished-listener below; reset when the sheet slides back to 0 on hide).
                var openSettled by remember { mutableStateOf(false) }

                // The collapsed peek is capped at a fixed fraction of the window height — a constant known up
                // front, so the open slide has a stable target that can't strand the drag (unlike a measured
                // height that grows as content loads). Short stops shrink below it to fit (see collapsedPeekDp).
                // (containerSize, not Configuration.screenHeightDp — the latter is lint-flagged as unreliable.)
                // How much of the window it may cover is a property of what the sheet holds — see
                // [HomeSheetContent.peekHeightFraction] for why the two drawers differ.
                val capPeekDp = with(density) {
                    val height = LocalWindowInfo.current.containerSize.height
                    (height * sheetContent.peekHeightFraction).toDp()
                }

                // The full collapsed peek: the fixed cap while loading or still opening, then min(content, cap)
                // once settled — fitting short stops without dead space, clipping tall ones at the cap. The
                // scaffold peek, the FAB lift, and the map's bottom inset all use this.
                val collapsedPeekDp =
                    if (contentPx > 0 && openSettled) minOf(contentPeekDp, capPeekDp) else capPeekDp

                // The full collapsed peek in px — the map's bottom inset (onSheetSettled). Must match the sheet's
                // on-screen height, or map-framed content (the ETA-tap vehicle+stop fit) lands under the handle +
                // nav-bar strip.
                val collapsedPeekPx = with(density) { collapsedPeekDp.roundToPx() }

                // The peek height actually handed to the scaffold: the real peek while shown, 0 while hidden.
                // Animating between the two slides the whole sheet up from / down past the bottom edge — the
                // slide-in/out that the removed `Hidden` anchor used to provide. The finished-listener flips
                // openSettled once the reveal lands at a non-zero peek, unlocking the fit-to-content shrink.
                val visiblePeekDp by animateDpAsState(
                    targetValue = if (sheetShown) collapsedPeekDp else 0.dp,
                    label = "sheetPeek",
                    finishedListener = { settled -> openSettled = sheetShown && settled > 0.dp }
                )

                // Report the resting position back to the activity (map padding / recenter / arrivals preview).
                // While hidden the sheet still rests at `PartiallyExpanded` (just with a 0 peek), so fold the
                // shown flag in: a hidden sheet reports `Hidden` (map padding 0), else its live expansion. Keyed
                // on collapsedPeekPx too so a late peek measurement (or nav-bar inset resolving) re-emits the
                // resting state with the corrected map inset rather than sticking at the stale height.
                LaunchedEffect(sheetState, collapsedPeekPx) {
                    snapshotFlow {
                        if (!sheetShown) ArrivalsSheetState.Hidden else sheetState.currentValue.toArrivalsSheetState()
                    }.collect { value ->
                        onSheetSettled(value, collapsedPeekPx)
                    }
                }

                // Collapse the sheet to peek when a route is selected within stop focus. This reacts to the
                // durable focus state rather than to the map's asynchronously loaded route header.
                val routeModeActive = stopFocus?.selectedRoute != null
                LaunchedEffect(routeModeActive) {
                    if (routeModeActive) runCatching { sheetState.partialExpand() }
                }

                // The "Found X region" snackbar (replaces the legacy toast): a one-shot VM event, shown once per
                // auto-select resolve. showSnackbar suspends until dismissed; Long ~ the old Toast.LENGTH_LONG.
                LaunchedEffect(Unit) {
                    homeViewModel.regionFound.collect { name ->
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.region_region_found, name),
                            duration = SnackbarDuration.Long
                        )
                    }
                }

                // Welcome onboarding: the host stages a request (help "Show tutorials" / what's-new opt-out /
                // first-run launch extra) on the VM latch; start the green welcome + map-stop spotlight sequence
                // here (replacing the legacy ShowcaseView welcome), then clear the latch.
                LaunchedEffect(Unit) {
                    homeViewModel.showWelcomeTutorial.collect { requested ->
                        if (requested) {
                            tutorialState.start(WelcomeTutorial.steps)
                            homeViewModel.onWelcomeTutorialConsumed()
                        }
                    }
                }

                // Semantic map actions have HOME-local undo history. An expanded arrivals sheet still
                // collapses first; every other back gesture restores the preceding focus and viewport.
                //
                // The expansion term is what makes this work for the nearby drawer (#2107), which shows
                // with *no* focus and so usually has no undo history behind it: without it, back from a
                // full-height nearby list would leave the app instead of collapsing it. At peek that
                // drawer consumes nothing — it's ambient, with nothing behind it to go back to — so back
                // falls through to the system (see [sheetBackAction]).
                val sheetExpanded = sheetShown && sheetState.currentValue == SheetValue.Expanded
                BackHandler(enabled = canUndoMapAction || sheetExpanded) {
                    val sheetAction = if (sheetShown) {
                        sheetBackAction(sheetState.currentValue.toArrivalsSheetState(), sheetContent)
                    } else {
                        SheetBackAction.NONE
                    }
                    when (sheetAction) {
                        SheetBackAction.COLLAPSE -> scope.launch { runCatching { sheetState.partialExpand() } }
                        SheetBackAction.NAVIGATE_BACK, SheetBackAction.NONE ->
                            homeViewModel.navigateBackFocus()
                    }
                }

                HomeDrawer(
                    drawerState = drawerState,
                    onStarredStops = onStarredStops,
                    onStarredRoutes = onStarredRoutes,
                    onRecentStopsRoutes = onRecentStopsRoutes,
                    onReminders = onReminders,
                    onPlanTrip = onPlanTrip,
                    onPayFare = onPayFare,
                    onSettings = onSettings,
                    onHelp = onHelp,
                    onSendFeedback = onSendFeedback,
                    onOpenSource = onOpenSource
                ) {
                    // Provide the tutorial state to the whole screen (top chrome, map, and sheet) so their
                    // spotlight anchors register; [TutorialOverlay] below draws from the same state.
                    CompositionLocalProvider(LocalTutorialState provides tutorialState) {
                        // The map runs edge-to-edge (under the status bar): the scaffold fills the whole screen and
                        // the menu/search controls float over its top corners (see MapTopChrome below), replacing the
                        // old solid TopAppBar. The status-bar inset is applied to the floating chrome + overlays
                        // layer, not the map itself.
                        BottomSheetScaffold(
                            modifier = Modifier.fillMaxSize(),
                            scaffoldState = scaffoldState,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            // The animated peek: real peek while shown, 0 while hidden — slides the sheet in/out.
                            sheetPeekHeight = visiblePeekDp,
                            // Paint the sheet container (incl. the strip behind the drag handle) the same color
                            // the arrivals panel body paints, so the handle reads as part of the panel rather
                            // than sitting on a separate default-colored strip.
                            sheetContainerColor = MaterialTheme.colorScheme.surface,
                            sheetDragHandle = {
                                ArrivalsDragHandle(
                                    onToggle = toggleSheet,
                                    modifier = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_PANEL)
                                )
                            },
                            sheetContent = {
                                // Bounded so a long list can't grow the sheet past maxSheetContentDp — that
                                // measured height is what sets the expanded top edge. A short list still
                                // wraps below it, keeping the fit-to-content peek.
                                Box(Modifier.heightIn(max = maxSheetContentDp)) {
                                    // The nearby list only while it *is* the sheet's subject; otherwise the
                                    // per-stop panel, which stays composed through the hide animation (it
                                    // returns early on a null session) so the sheet doesn't blank mid-slide.
                                    if (sheetContent == HomeSheetContent.NearbyRoutes) {
                                        NearbyArrivalsSheetHost(
                                            rows = nearbyRows,
                                            actionsFor = nearbyActionsFor,
                                            favoriteRouteIds = favoriteRouteIds,
                                            callbacks = nearbyRowCallbacks,
                                            limitExceeded = nearbyLimitExceeded,
                                            onContentHeight = { px -> contentPx = px }
                                        )
                                    } else {
                                        ArrivalsSheetHost(
                                            session = arrivalsSession,
                                            state = arrivalsState,
                                            selectedRoute = stopFocus?.selectedRoute,
                                            mapRouteColors = mapRouteColors,
                                            selectedTripBandColor = selectedTripBandColor,
                                            onContentHeight = { px -> contentPx = px }
                                        )
                                    }
                                }
                            }
                        ) {
                            // Trip-plan directions focus: the compact form replaces the top-chrome search field and
                            // the results sheet + itinerary render over the map. The form/plan state lives in the
                            // HOME-scoped trip-plan VMs.
                            val directionsActive = currentFocus is CurrentFocus.Directions
                            val tripPlanFormState by tripPlanViewModel.formState.collectAsStateWithLifecycle()
                            val tripPlanResult by tripPlanViewModel.planState.collectAsStateWithLifecycle()
                            val directionsResults = (tripPlanResult as? PlanResult.Success)?.takeIf {
                                it.itineraries.isNotEmpty()
                            }
                            // The classified error for a failed plan (e.g. endpoints outside the transit
                            // network), so it isn't silently swallowed; the snackbar renders its header + reason.
                            // Success is always non-empty (both planners throw NoRoute on empty), so only Error
                            // surfaces a message.
                            val directionsError = (tripPlanResult as? PlanResult.Error)?.error
                            val directionsLoading = tripPlanResult is PlanResult.Loading

                            // ---- The parked trip plan (#2053) ------------------------------------
                            val pinnedTrip by pinnedTripViewModel.pinned.collectAsStateWithLifecycle()
                            val pinnedCard by pinnedTripViewModel.card.collectAsStateWithLifecycle()
                            val pendingResumeIndex by pinnedTripViewModel.pendingResumeIndex
                                .collectAsStateWithLifecycle()
                            // Whether the plan on screen is the pinned one — so the controls read Unpin,
                            // and so a refresh of it can update the snapshot in place.
                            val pinnedTripIsOnScreen = directionsResults?.params?.let { params ->
                                pinnedTrip?.describesSameTripAs(params, tripPlanFormState.departNow)
                            } == true
                            // A pin is a whole request, so a plan with no request behind it (a monitor
                            // notification re-entry) is one there is nothing to pin. The results sheet's
                            // control is *disabled* — it holds its place in a row that is always there —
                            // while the exit dialog's button is simply absent, since a dialog offering a
                            // greyed-out answer is worse than one offering two.
                            val canPin = directionsResults?.params != null
                            val pinTripOption: (Int) -> Unit = { index ->
                                val results = directionsResults
                                val params = results?.params
                                if (params != null) {
                                    pinnedTripViewModel.pin(
                                        params = params,
                                        departNow = tripPlanFormState.departNow,
                                        itineraries = results.itineraries,
                                        selectedIndex = index
                                    )
                                }
                            }
                            // The option the rider is looking at, which is the one an exit-time pin parks.
                            val tripResultsState by tripResultsViewModel.state.collectAsStateWithLifecycle()
                            val selectedOptionIndex =
                                (tripResultsState as? TripResultsUiState.Success)?.selectedIndex ?: 0
                            // The toggle the pin control and the card's long-press menu share. The exit
                            // dialog deliberately does *not* use it: "Pin & leave" must always leave a
                            // pin behind, and toggling would make it un-pin the very trip it was pressed
                            // to keep.
                            val onTogglePinOption: (Int) -> Unit = { index ->
                                if (pinnedTripIsOnScreen && index == pinnedTrip?.selectedIndex) {
                                    pinnedTripViewModel.unpin()
                                } else {
                                    pinTripOption(index)
                                }
                            }
                            // The parked trip, traced thin under the map the rider is exploring (#2053) —
                            // withdrawn inside directions, where the real trip is already drawn and a
                            // ghost of it would only double every line.
                            LaunchedEffect(pinnedTrip, pinnedCard, directionsActive) {
                                mapViewModel.setPinnedTripOverlay(
                                    pinnedTrip?.selectedItinerary,
                                    pinnedCard,
                                    // The trace is withheld over a drawn itinerary, where it would only
                                    // double every line; the marker stands wherever the rider is.
                                    traceRoute = !directionsActive
                                )
                            }
                            // A pinned trip is one the rider can walk back to, so leaving it costs nothing
                            // and the #2140 "you'll lose this" confirmation stops being worth asking.
                            LaunchedEffect(pinnedTripIsOnScreen) {
                                homeViewModel.setDrawnTripRecoverable(pinnedTripIsOnScreen)
                            }
                            // A fresh plan for the trip the rider pinned replaces the pin's snapshot, so
                            // it stays a bookmark of that trip rather than of the first answer to it.
                            // Guarded on `fromSnapshot` because a resume *is* the stored plan — adopting
                            // it would be the pin re-pinning itself once per resume.
                            LaunchedEffect(directionsResults, pinnedTripIsOnScreen) {
                                val results = directionsResults ?: return@LaunchedEffect
                                if (!pinnedTripIsOnScreen || results.fromSnapshot) return@LaunchedEffect
                                pinnedTripViewModel.resnapshot(results.itineraries)
                            }
                            // Resume: seed the form + results *before* entering directions, so the single
                            // recomposition that turns directions on already sees a plan. Reversed, the
                            // "planning but no results yet" effect below can fire on the intermediate
                            // frame and wipe the trip off the map.
                            val onResumePinnedTrip: () -> Unit = {
                                pinnedTrip?.let { pin ->
                                    pinnedTripViewModel.beginResume(pin)
                                    tripPlanViewModel.restorePinned(pin.params, pin.departNow, pin.itineraries)
                                    homeViewModel.enterDirections(mapViewModel.viewport)
                                }
                            }

                            // A long-pressed map point awaiting the "directions from/to here" choice.
                            var longPressPoint by remember { mutableStateOf<GeoPoint?>(null) }
                            // Leaving directions ends any in-progress map pick.
                            LaunchedEffect(directionsActive) { if (!directionsActive) pickTarget = null }
                            // While planning but not yet submittable (no results), clear any stale drawn itinerary.
                            LaunchedEffect(directionsActive, directionsResults == null) {
                                if (directionsActive && directionsResults == null) {
                                    homeViewModel.clearShownItineraryOnMap()
                                }
                            }
                            // Drop a green/red pin for each resolved From/To endpoint as it's set, before any plan
                            // (so a single-endpoint state already shows the point). Only while in directions with
                            // no itinerary yet — the itinerary's own pins supersede these once it draws.
                            val showEndpointPins = directionsActive && directionsResults == null
                            val fromPoint = if (showEndpointPins) tripPlanFormState.from.pinPoint() else null
                            val toPoint = if (showEndpointPins) tripPlanFormState.to.pinPoint() else null
                            LaunchedEffect(fromPoint, toPoint) {
                                homeViewModel.setDirectionsEndpointsOnMap(fromPoint, toPoint)
                            }
                            // The results sheet's measured height, published as the map's bottom inset so a tapped
                            // itinerary step centers in the band above it (0 whenever the sheet isn't shown).
                            var directionsSheetHeightPx by remember { mutableIntStateOf(0) }
                            val showResultsSheet = directionsActive && pickTarget == null && directionsResults != null
                            LaunchedEffect(showResultsSheet, directionsSheetHeightPx) {
                                homeViewModel.setDirectionsResultsInset(
                                    if (showResultsSheet) directionsSheetHeightPx else 0
                                )
                            }
                            // Back cancels an in-progress map pick, then steps out of a drilled-into leg to
                            // the whole trip, and only from the itinerary overview exits directions focus
                            // (to nearby stops). This handler composes inside the undo one above, so it
                            // registers later and wins every back press while directions is active — the
                            // one-level walk it delegates to is what keeps that from stranding the trip.
                            BackHandler(enabled = directionsActive) {
                                if (pickTarget != null) {
                                    pickTarget = null
                                } else {
                                    homeViewModel.navigateBackInDirections()
                                }
                            }

                            // Lift the FABs above whichever sheet is resting over the map — the collapsed arrivals
                            // peek, or in directions the results drawer (whose settled height the map inset above
                            // already tracks). The target changes only on settle and MapChrome animates it. Local
                            // here since the screen holds the live SheetState. The arrivals term counts only while
                            // that sheet is shown at peek — a hidden sheet also rests at PartiallyExpanded now.
                            val fabInsetTarget = mapControlsBottomInset(
                                arrivalsPeek = collapsedPeekDp,
                                arrivalsAtPeek = sheetShown &&
                                    sheetState.currentValue == SheetValue.PartiallyExpanded,
                                directionsSheet = if (showResultsSheet) {
                                    with(density) { directionsSheetHeightPx.toDp() }
                                } else {
                                    0.dp
                                }
                            )
                            Box(Modifier.fillMaxSize()) {
                                // The map, with the chrome drawn over it: weather/donation/route-header/survey. The
                                // list "tabs" are now their own NavHost destinations, so HOME is always the map.
                                MapFeature(
                                    mapViewModel = mapViewModel,
                                    homeViewModel = homeViewModel,
                                    nearbyArrivalsViewModel = nearbyArrivalsViewModel,
                                    fabBottomInset = fabInsetTarget,
                                    onMapLongPress = { longPressPoint = it },
                                    onResumePinnedTrip = onResumePinnedTrip,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // The floating top chrome + the map overlays draw over the (now edge-to-edge) map.
                                // MapTopChrome is drawn LAST so the menu + search FABs stay on top of (and tappable
                                // above) every overlay — including the route-mode header, which now floats as a card
                                // below the FAB row rather than covering it.
                                Box(Modifier.fillMaxSize()) {
                                    // Every top-of-map overlay sits below the chrome row via one shared inset
                                    // (status bar + clearance), so no individual overlay has to know the FAB-row height.
                                    Box(Modifier.fillMaxSize().mapTopChromeOverlayInset()) {
                                        HomeMapOverlays(
                                            weatherViewModel = weatherViewModel,
                                            donationViewModel = donationViewModel,
                                            surveyViewModel = surveyViewModel,
                                            focusBannerState = focusBannerState,
                                            onCloseFocus = homeViewModel::clearMapFocus,
                                            onToggleFavorite = {
                                                when (focusBannerState) {
                                                    is FocusBannerState.Stop ->
                                                        currentFocus.focusedStop?.let {
                                                            focusBannerViewModel.toggleStopFavorite(it)
                                                        }
                                                    is FocusBannerState.Route ->
                                                        focusBannerViewModel.toggleRouteFavorite(
                                                            focusBannerState.header
                                                        )
                                                    null -> Unit
                                                }
                                            },
                                            onShowAlerts = { serviceAlertsVisible = true },
                                            onRecenterStop = {
                                                homeViewModel.recenterOnFocusedStop(mapViewModel.viewport)
                                            },
                                            // The direction menu calls straight into the map VM (which
                                            // re-filters stops/vehicles + persists the choice), like the height report below.
                                            onSelectRouteDirection = { directionId ->
                                                homeViewModel.selectStandaloneRouteDirection(directionId)
                                                mapViewModel.selectRouteDirection(directionId)
                                            },
                                            // Tapping the header body reframes the map to the route's full extent (VM
                                            // re-issues the retained route framing).
                                            onFrameRoute = {
                                                homeViewModel.reframeFocusedRoute(mapViewModel.viewport)
                                            },
                                            onLearnMore = onLearnMore,
                                            onOpenSurvey = onOpenSurvey,
                                            focusBannerTopPx = focusBannerTopPx,
                                            // This layer converts measured card height to its map-space bottom edge;
                                            // the map VM adds marker clearance and owns the resulting content padding.
                                            onFocusBannerBottom = { focusBannerBottomPx = it }
                                        )
                                    }
                                    // The FAB row itself only takes the status-bar inset (no clearance) so it sits at
                                    // the very top; the overlay layer above adds the clearance below it.
                                    if (directionsActive) {
                                        // Hidden while picking a point on the map (the pick overlay takes the screen).
                                        if (pickTarget == null) {
                                            DirectionsFormCard(
                                                viewModel = tripPlanViewModel,
                                                state = tripPlanFormState,
                                                onPickEndpoint = { pickTarget = it },
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .statusBarsPadding()
                                                    // Wider at the sides than at the top: the card
                                                    // floats over the map, and letting a little more
                                                    // of it show past either edge reads as floating
                                                    // rather than as chrome bolted to the screen.
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                                    // Report the card's bottom edge as the map's top inset (see
                                                    // directionsFormBottomPx) so a focused step clears the form.
                                                    .onGloballyPositioned {
                                                        directionsFormBottomPx = it.boundsInWindow().bottom.roundToInt()
                                                    }
                                            )
                                        }
                                    } else {
                                        MapTopChrome(
                                            onOpenDrawer = openDrawer,
                                            onSearch = onSearch,
                                            recents = recents,
                                            onRecentStop = onRecentStop,
                                            onRecentRoute = onRecentRoute,
                                            // Recent stops/routes lives in the drawer, so the onboarding spotlight points at
                                            // the menu FAB that opens it (was the retired overflow ⋮).
                                            menuModifier = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_MORE_MENU),
                                            modifier = Modifier.statusBarsPadding()
                                        )
                                    }
                                }
                                // Directions feedback over the map (not while picking a point): the results sheet
                                // when a plan produced itineraries, else an error / no-route message; a plan in
                                // flight shows a top progress line. The results selection drives the drawn itinerary.
                                if (directionsActive && pickTarget == null) {
                                    when {
                                        directionsResults != null -> DirectionsResultsSheet(
                                            resultsViewModel = tripResultsViewModel,
                                            itineraries = directionsResults.itineraries,
                                            params = directionsResults.params,
                                            showItinerary = { itinerary ->
                                                homeViewModel.showItineraryOnMap(
                                                    itinerary,
                                                    directionsResults.params.itineraryPins()
                                                )
                                            },
                                            onFocusRouteLeg = homeViewModel::focusItineraryRouteLeg,
                                            onFocusLeg = homeViewModel::focusItineraryLegOnMap,
                                            onFocusPoint = homeViewModel::focusItineraryPointOnMap,
                                            // Each transit leg's Board/Alight row shows that stop's live ETA strip inline,
                                            // ruled at the moment the plan has the rider reach the stop (#2125).
                                            stopEtaStrip = { ride, stop ->
                                                DirectionStopEtaStrip(
                                                    routeLeg = ride.routeLeg,
                                                    stop = stop,
                                                    reachStopTime = ride.reachStopTime,
                                                    arrivalsViewModelFactory = arrivalsViewModelFactory,
                                                    onShowTrip = onShowTrip,
                                                    onEditReminder = onEditReminder,
                                                    onFocusVehicle = { request ->
                                                        homeViewModel.focusDirectionsRouteVehicle(request, ride.routeLeg, ride.legPoints)
                                                    },
                                                    // The focused leg's Board row reads the hoisted session rather than
                                                    // opening a second one on the stop the map is already polling.
                                                    hoistedSession = rideArrivalsSession?.takeIf {
                                                        stop.stopId != null && stop.stopId == rideBoardStop?.id
                                                    }
                                                )
                                            },
                                            // Its own settled height (peek vs expanded), not a measured
                                            // size: the sheet now hosts a full-screen scaffold over the
                                            // map, so the composable's own bounds are the whole screen.
                                            onSheetHeightPx = { directionsSheetHeightPx = it },
                                            // A route label tapped on the drawn itinerary (#2101): the
                                            // sheet resolves it to the ride it names and focuses it —
                                            // the first in travel order where one label covers the
                                            // same route ridden twice.
                                            rideBadgeTaps = homeViewModel.itineraryRideBadgeTaps,
                                            // A resume opens on the option the rider pinned; every other
                                            // plan opens on the first, as it always has.
                                            initialOptionIndex = pendingResumeIndex ?: 0,
                                            fromSnapshot = directionsResults.fromSnapshot,
                                            pinnedOptionIndex = pinnedTrip
                                                ?.selectedIndex
                                                ?.takeIf { pinnedTripIsOnScreen },
                                            // A plan with no request behind it has nothing to pin, so
                                            // its cards carry no long press at all (#2053).
                                            onTogglePin = onTogglePinOption.takeIf { canPin },
                                            // Only while this drawer is showing the pinned trip, which
                                            // is what lets the button say "this trip".
                                            onUnpinTrip = pinnedTripViewModel::unpin
                                                .takeIf { pinnedTripIsOnScreen },
                                            onOptionsSeeded = pinnedTripViewModel::onResumeConsumed,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        directionsError != null -> DirectionsErrorSnackbar(
                                            error = directionsError,
                                            onDismiss = tripPlanViewModel::clearPlanResult,
                                            modifier = Modifier.align(Alignment.BottomCenter)
                                        )
                                    }
                                    if (directionsLoading) {
                                        LinearProgressIndicator(
                                            Modifier
                                                .align(Alignment.TopCenter)
                                                .fillMaxWidth()
                                                .statusBarsPadding()
                                        )
                                    }
                                }
                                // Pick a From/To point on the home map: crosshair + confirm reads the map center.
                                pickTarget?.let { target ->
                                    DirectionsPickOverlay(
                                        onConfirm = {
                                            // Only commit + dismiss once we actually have a map center; otherwise
                                            // keep the picker open rather than silently losing the selection.
                                            mapViewModel.camera.value?.center?.let { c ->
                                                val point = TripEndpoint.MapPoint(c.latitude, c.longitude)
                                                tripPlanViewModel.setEndpoint(target, point)
                                                pickTarget = null
                                            }
                                        }
                                    )
                                }
                                // Long-press → "directions from/to here": enters directions and fills the
                                // chosen endpoint with the pressed point (see setEndpointFromLongPress).
                                longPressPoint?.let { point ->
                                    val mapPoint = TripEndpoint.MapPoint(point.latitude, point.longitude)
                                    DirectionsLongPressMenu(
                                        onChooseSlot = { slot ->
                                            homeViewModel.enterDirections(mapViewModel.viewport)
                                            tripPlanViewModel.setEndpointFromLongPress(slot, mapPoint)
                                            longPressPoint = null
                                        },
                                        onDismiss = { longPressPoint = null }
                                    )
                                }
                                // Neither Back nor a tap on the map background leaves outright while a trip
                                // is drawn — each stages this question instead (#2140). The VM owns the latch
                                // because the two gestures reach it from different places (the BackHandler
                                // above, and the map's own click callback), and it is answered here.
                                val showExitConfirm by homeViewModel.pendingDirectionsExit
                                    .collectAsStateWithLifecycle()
                                if (showExitConfirm) {
                                    DirectionsExitConfirmDialog(
                                        // Offered only with nothing pinned, where "pin" can mean exactly
                                        // one thing. With another trip already parked it would be asking
                                        // the rider to choose between two trips they can't both see, so
                                        // the offer is withheld rather than made ambiguously.
                                        onPinAndLeave = if (canPin && pinnedTrip == null) {
                                            {
                                                pinTripOption(selectedOptionIndex)
                                                homeViewModel.confirmExitDirections()
                                            }
                                        } else {
                                            null
                                        },
                                        onConfirm = homeViewModel::confirmExitDirections,
                                        onDismiss = homeViewModel::dismissDirectionsExit
                                    )
                                }
                            }
                        }
                    }
                }

                if (serviceAlertsVisible && arrivalsContent != null && arrivalsSession != null) {
                    ServiceAlertsDialog(
                        content = arrivalsContent,
                        onShowAlert = arrivalsSession.handler::onShowAlert,
                        onHideAlert = arrivalsSession.handler::onHideAlert,
                        onShowHiddenAlerts = arrivalsSession.viewModel::showHiddenAlerts,
                        onDismiss = { serviceAlertsVisible = false }
                    )
                }

                // The region-wide GTFS alert dialog — a self-wired feature module (WideAlertViewModel streams the
                // current region's alerts), replacing the activity's GtfsAlertsHelper.showWideAlertDialog path.
                val wideAlertViewModel = hiltViewModel<WideAlertViewModel>()
                val wideAlert by wideAlertViewModel.wideAlert.collectAsStateWithLifecycle()
                wideAlert?.let { WideAlertDialog(it) { wideAlertViewModel.dismiss() } }

                // The help / what's-new / legend dialogs feature module (self-rendering from its ViewModel;
                // self-shows what's-new once a region resolves; the genuinely-Activity actions + the what's-new
                // opt-out are forwarded to the host).
                HelpFeature(
                    viewModel = helpViewModel,
                    onHelpAction = onHelpAction,
                    onShowWelcomeTutorial = onShowWelcomeTutorial
                )

                // The arrivals-panel onboarding spotlight, drawn over the whole screen (incl. the bottom sheet)
                // as the last sibling so it sits on top; renders nothing while no tutorial is active.
                TutorialOverlay(tutorialState)
            }
        }
    }
}

/**
 * The home screen's `ModalNavigationDrawer`: the nav-drawer sheet ([HomeNavDrawerSheet]) wrapping the
 * screen [content]. A tap closes the drawer and dispatches the selection up. The drawer is opened from
 * the menu FAB (via the host-owned [drawerState]), so gestures are enabled only while it's already
 * open — a left-edge drag on the map must pan the map, not peel the drawer open.
 */
@Composable
private fun HomeDrawer(
    drawerState: DrawerState,
    onStarredStops: () -> Unit,
    onStarredRoutes: () -> Unit,
    onRecentStopsRoutes: () -> Unit,
    onReminders: () -> Unit,
    onPlanTrip: () -> Unit,
    onPayFare: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenSource: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    // The drawer's region/feature gating is a self-wired feature module (NavDrawerViewModel), collected
    // here so the screen doesn't thread unrelated booleans through the focus state.
    val availability by hiltViewModel<NavDrawerViewModel>().availability.collectAsStateWithLifecycle()

    // Every row closes the drawer before dispatching, matching the legacy single onSelect path.
    fun close() {
        scope.launch { drawerState.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Material3 gates both the open-swipe and the scrim tap-to-close on this one flag, so tie it
        // to the open state (see the KDoc above).
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            HomeNavDrawerSheet(
                showReminders = availability.showReminders,
                planTripAvailable = availability.planTripAvailable,
                payFareAvailable = availability.payFareAvailable,
                onStarredStops = {
                    close()
                    onStarredStops()
                },
                onStarredRoutes = {
                    close()
                    onStarredRoutes()
                },
                onRecentStopsRoutes = {
                    close()
                    onRecentStopsRoutes()
                },
                onReminders = {
                    close()
                    onReminders()
                },
                onPlanTrip = {
                    close()
                    onPlanTrip()
                },
                onPayFare = {
                    close()
                    onPayFare()
                },
                onSettings = {
                    close()
                    onSettings()
                },
                onHelp = {
                    close()
                    onHelp()
                },
                onSendFeedback = {
                    close()
                    onSendFeedback()
                },
                onOpenSource = {
                    close()
                    onOpenSource()
                }
            )
        },
        content = content
    )
}

/**
 * The chrome drawn over the map inside the home scaffold's content [Box]: the weather chip, donation
 * card, route-mode header, and survey hero. A [BoxScope] extension so the overlays keep their
 * `align`/fill modifiers. HOME is always the map now, so these are unconditional — the former list
 * tabs are their own NavHost destinations.
 */
@Composable
private fun BoxScope.HomeMapOverlays(
    weatherViewModel: WeatherViewModel,
    donationViewModel: DonationViewModel,
    surveyViewModel: SurveyViewModel,
    focusBannerState: FocusBannerState?,
    onCloseFocus: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowAlerts: () -> Unit,
    onRecenterStop: () -> Unit,
    onSelectRouteDirection: (Int?) -> Unit,
    onFrameRoute: () -> Unit,
    onLearnMore: () -> Unit,
    onOpenSurvey: (url: String) -> Unit,
    focusBannerTopPx: Int,
    onFocusBannerBottom: (Int) -> Unit
) {
    // The caller offsets this whole overlay layer below the top chrome (one shared inset), so the
    // overlays only carry their own side margins here.
    // The weather chip feature module: self-wiring from its ViewModel. Sits below the floating search
    // field (which now occupies the top-end corner), not beside it.
    WeatherFeature(
        viewModel = weatherViewModel,
        onNearby = true,
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp)
    )
    // The donation feature module: the card (DonationsManager-gated) plus its dismiss dialog.
    DonationFeature(
        viewModel = donationViewModel,
        onNearby = true,
        onLearnMore = onLearnMore,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
    )
    // The map survey (Compose): hero card over the map + remaining-questions sheet. Self-wiring from
    // its ViewModel; self-triggers its request once a region has resolved.
    SurveyFeature(
        viewModel = surveyViewModel,
        onNearby = true,
        onOpenSurvey = onOpenSurvey,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    // The focus banner is a floating card centered below the top chrome. Drawn last so it sits above
    // weather / donation / survey cards while a stop or route is focused. The layer is already offset by the
    // clearance, but the map's top-padding derivation needs the card's bottom edge in map coordinates,
    // so add both the status-bar inset and chrome clearance back onto its reported height.
    if (focusBannerState != null) {
        val context = LocalContext.current
        FocusBanner(
            state = focusBannerState,
            onClose = onCloseFocus,
            onToggleFavorite = onToggleFavorite,
            onShowAlerts = onShowAlerts,
            onRecenterStop = onRecenterStop,
            onSelectDirection = onSelectRouteDirection,
            onFrameRoute = onFrameRoute,
            // Same destination as the arrivals drawer's route menu, wired locally rather than through
            // HomeActivityActions — the browser hand-off needs nothing but a Context.
            onShowSchedule = { url -> ExternalIntents.goToUrl(context, url) },
            onHeight = { h -> onFocusBannerBottom(h + focusBannerTopPx) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
        )
    } else {
        LaunchedEffect(Unit) { onFocusBannerBottom(0) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun SheetValue.toArrivalsSheetState() = when (this) {
    SheetValue.Hidden -> ArrivalsSheetState.Hidden
    SheetValue.PartiallyExpanded -> ArrivalsSheetState.Collapsed
    SheetValue.Expanded -> ArrivalsSheetState.Expanded
}

/**
 * The arrivals sheet's drag handle: a short grab bar tinted to sit on the panel surface (paired with
 * the scaffold's `sheetContainerColor`) so it reads as part of the panel, not a separate strip. Tapping
 * toggles peek<->full via [onToggle]; it never hides the sheet — the sheet has no `Hidden` drag anchor,
 * so it only leaves programmatically (animated peek height) when focus clears. Its own click shadows the
 * scaffold's built-in handle click.
 */
@Composable
private fun ArrivalsDragHandle(onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // The click sits on the outer padded Box (before the padding) so the tap target covers more than the
    // bar; it shadows the scaffold's own handle click. The bar itself is a short tinted pill. [modifier]
    // is the host's anchor slot (the onboarding "slide up" spotlight points here) — outermost so the
    // spotlight hugs just the handle, not the full-width header.
    Box(
        modifier = modifier
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = DRAG_HANDLE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center
    ) {
        DragHandleBar()
    }
}
