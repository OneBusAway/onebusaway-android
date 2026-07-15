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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalsLoaded
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.ListUiState
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.home.arrivals.ArrivalsSheetHost
import org.onebusaway.android.ui.home.chrome.MAP_TOP_CHROME_CLEARANCE
import org.onebusaway.android.ui.home.chrome.MapTopChrome
import org.onebusaway.android.ui.home.chrome.mapTopChromeOverlayInset
import org.onebusaway.android.ui.home.drawer.HomeNavDrawerSheet
import org.onebusaway.android.ui.home.drawer.NavDrawerViewModel
import org.onebusaway.android.ui.home.donation.DonationFeature
import org.onebusaway.android.ui.home.donation.DonationViewModel
import org.onebusaway.android.ui.home.help.HelpAction
import org.onebusaway.android.ui.home.help.HelpFeature
import org.onebusaway.android.ui.home.help.HelpViewModel
import org.onebusaway.android.ui.home.map.MapChrome
import org.onebusaway.android.ui.home.map.MapFeature
import org.onebusaway.android.ui.mylists.RecentItem
import org.onebusaway.android.ui.mylists.SearchRecentsRepository
import org.onebusaway.android.ui.mylists.rememberListVm
import org.onebusaway.android.ui.home.map.RouteHeaderOverlay
import org.onebusaway.android.ui.survey.SurveyFeature
import org.onebusaway.android.ui.tutorial.ArrivalTutorial
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.TutorialOverlay
import org.onebusaway.android.ui.tutorial.WelcomeTutorial
import org.onebusaway.android.ui.tutorial.tutorialAnchor
import org.onebusaway.android.ui.tutorial.rememberTutorialState
import org.onebusaway.android.ui.home.weather.WeatherFeature
import org.onebusaway.android.ui.home.weather.WeatherViewModel
import org.onebusaway.android.ui.home.widealert.WideAlertDialog
import org.onebusaway.android.ui.home.widealert.WideAlertViewModel
import org.onebusaway.android.ui.survey.SurveyViewModel

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
    // Search-box recents dropdown: tapping a stop opens its arrivals; tapping a route reveals it on the map.
    val onRecentStop: (id: String, name: String?) -> Unit,
    val onRecentRoute: (routeId: String) -> Unit,
    // Wraps [HomeActivityActions.onHelpActionExternal] with the one branch that's a navigation (AGENCIES).
    val onHelpAction: (HelpAction) -> Unit,
    val onShowTrip: (tripId: String, stopId: String) -> Unit,
    val onEditReminder: (args: ReminderEditorArgs) -> Unit,
    val onLearnMore: () -> Unit,
    val onOpenSurvey: (url: String) -> Unit,
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
    val onClearFocus: () -> Unit,
    val onArrivalsLoaded: (ArrivalsLoaded) -> Unit,
    val onShowRouteOnMap: (ShowRouteRequest) -> Unit,
    val onCancelRouteMode: () -> Unit,
)

/**
 * The declarative home screen: a Compose `ModalNavigationDrawer` + an edge-to-edge Material3
 * `BottomSheetScaffold` (the map) with the floating [MapTopChrome] (menu + search FABs) over its top,
 * rendered from [HomeUiState] (state down) with taps dispatched through plain lambda callbacks +
 * [HomeViewModel] events (up). Replaces the imperative `HomeShellHost` bridge.
 *
 * The arrivals sheet inverts to declarative: **visibility is business state** — the sheet peeks iff
 * a stop is focused on NEARBY — driven by a [LaunchedEffect] keyed on that derived flag, so it never
 * fights a user drag. The sheet has no `Hidden` drag anchor (`skipHiddenState = true`), so peek is the
 * hard floor of the drag; show/hide is instead an animated peek height (0 <-> real peek) that slides
 * the whole sheet in and out. **Expansion (peek<->full)** is the live `SheetState`, toggled by the drag
 * handle and collapsed as a declarative reaction to route mode activating (`routeHeader != null`;
 * the screen alone knows the live state), plus [BackHandler]. The arrivals panel is hosted directly per focused stop (see [ArrivalsSheetHost]);
 * the map ([MapFeature]), the route-mode header ([RouteHeaderOverlay]), and the survey ([org.onebusaway.android.ui.survey.SurveyOverlay])
 * are all composables now — no map-related `AndroidView` / View seam remains.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    // The map is a self-wiring [MapFeature]; it composes only while HOME is the current destination, so
    // SDK init is already lazy. The route-mode header and survey are Compose overlays over it.
    homeViewModel: HomeViewModel,
    mapViewModel: MapViewModel,
    routeHeader: RouteHeader?,
    surveyViewModel: SurveyViewModel,
    donationViewModel: DonationViewModel,
    weatherViewModel: WeatherViewModel,
    helpViewModel: HelpViewModel,
    // Builds the per-focused-stop ArrivalsViewModel for the bottom-sheet host (assisted-injected;
    // the sheet's stop id is runtime-dynamic, so it can't be a plain hiltViewModel). Injected into
    // HomeActivity and threaded down.
    arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    // All the screen's tap/UI lambdas, bundled (see [HomeCallbacks]); brought into scope below via
    // `with` so the body references them unqualified.
    callbacks: HomeCallbacks,
) {
    with(callbacks) {
    with(activityActions) {
    ObaTheme {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val resources = LocalResources.current
        // Compute before entering mapTopChromeOverlayInset(), whose statusBarsPadding consumes this inset
        // for descendants. This is the route card's absolute top edge in map coordinates.
        val routeHeaderTopPx = WindowInsets.statusBars.getTop(density) +
            with(density) { MAP_TOP_CHROME_CLEARANCE.roundToPx() }
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
            skipHiddenState = true,
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

        // The collapsed peek is capped at a fixed fraction of the window height — a constant known up
        // front, so the open slide has a stable target that can't strand the drag (unlike a measured
        // height that grows as content loads). Short stops shrink below it to fit (see collapsedPeekDp).
        // (containerSize, not Configuration.screenHeightDp — the latter is lint-flagged as unreliable.)
        val capPeekDp = with(density) {
            (LocalWindowInfo.current.containerSize.height * PEEK_HEIGHT_FRACTION).toDp()
        }

        // The panel's total content height (px: pinned header + fully-laid-out list), reported once
        // measured (0 until then). Used only to shrink the peek below the cap for short stops. Not reset
        // on focus change — the next stop's panel overwrites it once laid out, avoiding a cap-bounce.
        var contentPx by remember { mutableIntStateOf(0) }
        // That content height as the on-screen peek it implies: the measured content plus the drag handle
        // above it and the nav-bar inset below (matching what the collapsed sheet actually shows).
        val contentPeekDp = with(density) { contentPx.toDp() } + DRAG_HANDLE_HEIGHT + peekBottomPadding

        // Visibility is business state: the sheet is shown (its peek slid up) iff a stop is focused.
        // Because there's no `Hidden` drag anchor, "shown" is a plain flag that drives the animated peek
        // height rather than a sheet drag state. The key is the focused stop id while shown (else null),
        // so the effect reacts to focus/tab changes but NOT to a user drag (same stop -> same key).
        var sheetShown by remember { mutableStateOf(false) }
        val showSheet = shouldShowSheet(state.focusedStop)
        val sheetKey = if (showSheet) state.focusedStop?.id else null
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
                // Show immediately on focus — a fixed-fraction peek can't strand the drag, so there's no
                // need to wait for arrivals; the peek shows a loading spinner until they land.
                sheetShown = true
            }
        }

        // Whether the reveal slide (peek 0 -> cap) has finished at a resting peek. The peek only shrinks
        // to fit short content once settled: retargeting mid-open would move the AnchoredDraggable anchor
        // and strand the sheet, so we slide up to the constant cap first, then shrink (flipped by the
        // animateDpAsState finished-listener below; reset when the sheet slides back to 0 on hide).
        var openSettled by remember { mutableStateOf(false) }

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
            finishedListener = { settled -> openSettled = sheetShown && settled > 0.dp },
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

        // Collapse the sheet to peek when the map enters route mode ("show vehicles on map"). A
        // declarative reaction to route-mode state rather than a VM command — the screen holds the live
        // SheetState, and route mode surfaces here as `routeHeader != null`.
        val routeModeActive = routeHeader != null
        LaunchedEffect(routeModeActive) {
            if (routeModeActive) runCatching { sheetState.partialExpand() }
        }

        // The "Found X region" snackbar (replaces the legacy toast): a one-shot VM event, shown once per
        // auto-select resolve. showSnackbar suspends until dismissed; Long ~ the old Toast.LENGTH_LONG.
        LaunchedEffect(Unit) {
            homeViewModel.regionFound.collect { name ->
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.region_region_found, name),
                    duration = SnackbarDuration.Long,
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

        // Back collapses an expanded sheet first, then (from peek) clears the focus, which hides it.
        // A hidden sheet leaves back to the system (mirrors the legacy !isSheetHidden() gate). Gated on
        // `sheetShown` since the sheet always rests at peek/expanded now (never a `Hidden` value).
        BackHandler(enabled = sheetShown) {
            when (sheetBackAction(sheetState.currentValue.toArrivalsSheetState())) {
                SheetBackAction.COLLAPSE -> scope.launch { runCatching { sheetState.partialExpand() } }
                SheetBackAction.CLEAR_FOCUS -> onClearFocus()
                SheetBackAction.NONE -> {}
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
            onOpenSource = onOpenSource,
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
                            modifier = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_PANEL),
                        )
                    },
                    sheetContent = {
                        ArrivalsSheetHost(
                            focusedStop = state.focusedStop,
                            sheetVisible = sheetShown,
                            arrivalsViewModelFactory = arrivalsViewModelFactory,
                            onArrivalsLoaded = onArrivalsLoaded,
                            // Showing vehicles on the map drags the sheet down to peek so the route is
                            // visible, whether or not route mode was already active (see collapseSheet).
                            onShowRouteOnMap = { request ->
                                onShowRouteOnMap(request)
                                collapseSheet()
                            },
                            onShowTrip = onShowTrip,
                            onEditReminder = onEditReminder,
                            onContentHeight = { px -> contentPx = px },
                            onTitleClick = homeViewModel::recenterOnFocusedStop,
                            showUndoSnackbar = { messageRes, actionRes, onAction ->
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = resources.getString(messageRes),
                                        actionLabel = actionRes?.let { resources.getString(it) },
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
                                }
                            },
                        )
                    }
                ) {
                    // Lift the FABs above the whole collapsed sheet peek; the target changes only on settle
                    // and MapChrome animates it. Local here since the screen holds the live SheetState. Only
                    // while the sheet is shown at peek — a hidden sheet also rests at PartiallyExpanded now.
                    val fabInsetTarget =
                        if (sheetShown && sheetState.currentValue == SheetValue.PartiallyExpanded) {
                            collapsedPeekDp
                        } else {
                            0.dp
                        }
                    Box(Modifier.fillMaxSize()) {
                        // The map, with the chrome drawn over it: weather/donation/route-header/survey. The
                        // list "tabs" are now their own NavHost destinations, so HOME is always the map.
                        MapFeature(
                            mapViewModel = mapViewModel,
                            homeViewModel = homeViewModel,
                            fabBottomInset = fabInsetTarget,
                            modifier = Modifier.fillMaxSize(),
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
                                    routeHeader = routeHeader,
                                    onCancelRouteMode = onCancelRouteMode,
                                    // The switch-direction affordance calls straight into the map VM (which
                                    // re-filters stops/vehicles + persists the choice), like the height report below.
                                    onSelectRouteDirection = mapViewModel::selectRouteDirection,
                                    // Tapping the header body reframes the map to the route's full extent (VM
                                    // re-issues the retained route framing).
                                    onFrameRoute = mapViewModel::frameRoute,
                                    onLearnMore = onLearnMore,
                                    onOpenSurvey = onOpenSurvey,
                                    routeHeaderTopPx = routeHeaderTopPx,
                                    // This layer converts measured card height to its map-space bottom edge;
                                    // the map VM adds marker clearance and owns the resulting content padding.
                                    onRouteHeaderBottom = mapViewModel::setRouteHeaderBottom,
                                )
                            }
                            // The FAB row itself only takes the status-bar inset (no clearance) so it sits at
                            // the very top; the overlay layer above adds the clearance below it.
                            MapTopChrome(
                                onOpenDrawer = openDrawer,
                                onSearch = onSearch,
                                recents = recents,
                                onRecentStop = onRecentStop,
                                onRecentRoute = onRecentRoute,
                                // Recent stops/routes lives in the drawer, so the onboarding spotlight points at
                                // the menu FAB that opens it (was the retired overflow ⋮).
                                menuModifier = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_MORE_MENU),
                                modifier = Modifier.statusBarsPadding(),
                            )
                        }
                    }
                }
            }
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
            onShowWelcomeTutorial = onShowWelcomeTutorial,
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
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // The drawer's region/feature gating is a self-wired feature module (NavDrawerViewModel), collected
    // here so the screen doesn't thread the booleans through HomeUiState.
    val availability by hiltViewModel<NavDrawerViewModel>().availability.collectAsStateWithLifecycle()
    // Every row closes the drawer before dispatching, matching the legacy single onSelect path.
    fun close() { scope.launch { drawerState.close() } }
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
                onStarredStops = { close(); onStarredStops() },
                onStarredRoutes = { close(); onStarredRoutes() },
                onRecentStopsRoutes = { close(); onRecentStopsRoutes() },
                onReminders = { close(); onReminders() },
                onPlanTrip = { close(); onPlanTrip() },
                onPayFare = { close(); onPayFare() },
                onSettings = { close(); onSettings() },
                onHelp = { close(); onHelp() },
                onSendFeedback = { close(); onSendFeedback() },
                onOpenSource = { close(); onOpenSource() },
            )
        },
        content = content,
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
    routeHeader: RouteHeader?,
    onCancelRouteMode: () -> Unit,
    onSelectRouteDirection: (Int) -> Unit,
    onFrameRoute: () -> Unit,
    onLearnMore: () -> Unit,
    onOpenSurvey: (url: String) -> Unit,
    routeHeaderTopPx: Int,
    onRouteHeaderBottom: (Int) -> Unit,
) {
    // The caller offsets this whole overlay layer below the top chrome (one shared inset), so the
    // overlays only carry their own side margins here.
    // The weather chip feature module: self-wiring from its ViewModel. Sits below the floating search
    // field (which now occupies the top-end corner), not beside it.
    WeatherFeature(
        viewModel = weatherViewModel,
        onNearby = true,
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp),
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
        modifier = Modifier.align(Alignment.TopCenter),
    )
    // The route-mode header, now a floating card centered below the top chrome (so the menu + search
    // FABs stay clear and tappable in route mode). Drawn last of the overlays so it sits above the
    // weather / donation / survey cards when a route is active. The layer is already offset by the
    // clearance, but the map's top-padding derivation needs the card's bottom edge in map coordinates,
    // so add both the status-bar inset and chrome clearance back onto its reported height.
    if (routeHeader != null) {
        RouteHeaderOverlay(
            header = routeHeader,
            onCancel = onCancelRouteMode,
            onSelectDirection = onSelectRouteDirection,
            onFrameRoute = onFrameRoute,
            onHeight = { h -> onRouteHeaderBottom(h + routeHeaderTopPx) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
        )
    } else {
        LaunchedEffect(Unit) { onRouteHeaderBottom(0) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun SheetValue.toArrivalsSheetState() = when (this) {
    SheetValue.Hidden -> ArrivalsSheetState.Hidden
    SheetValue.PartiallyExpanded -> ArrivalsSheetState.Collapsed
    SheetValue.Expanded -> ArrivalsSheetState.Expanded
}

// The custom drag handle's geometry — the visible bar plus the vertical padding above and below it.
// Shared by [ArrivalsDragHandle] (what it draws) and the peek-height math (how much room it needs), so
// the two can't drift apart.
private val DRAG_HANDLE_BAR_HEIGHT = 4.dp
private val DRAG_HANDLE_VERTICAL_PADDING = 9.dp
private val DRAG_HANDLE_HEIGHT = DRAG_HANDLE_BAR_HEIGHT + DRAG_HANDLE_VERTICAL_PADDING * 2

// The collapsed drawer peek is capped at this fraction of the screen height (short stops shrink to
// fit their content below it). A starting value to tune by eye.
private const val PEEK_HEIGHT_FRACTION = 0.30f

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
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // Same muted grey as the header's icon tint, so the handle matches the panel chrome.
            color = colorResource(R.color.navdrawer_icon_tint),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Box(Modifier.size(width = 32.dp, height = DRAG_HANDLE_BAR_HEIGHT))
        }
    }
}
