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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalsLoaded
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.ui.compose.rememberSheetExpandProgress
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.home.arrivals.ArrivalsSheetHost
import org.onebusaway.android.ui.home.chrome.HomeTopBar
import org.onebusaway.android.ui.home.drawer.HomeNavDrawerSheet
import org.onebusaway.android.ui.home.drawer.NavDrawerViewModel
import org.onebusaway.android.ui.home.donation.DonationFeature
import org.onebusaway.android.ui.home.donation.DonationViewModel
import org.onebusaway.android.ui.home.help.HelpAction
import org.onebusaway.android.ui.home.help.HelpFeature
import org.onebusaway.android.ui.home.help.HelpViewModel
import org.onebusaway.android.ui.home.map.MapChrome
import org.onebusaway.android.ui.home.map.MapFeature
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
 * The declarative home screen: a Compose `ModalNavigationDrawer` + [HomeTopBar] + Material3
 * `BottomSheetScaffold`, rendered from [HomeUiState] (state down) with taps dispatched through plain
 * lambda callbacks + [HomeViewModel] events (up). Replaces the imperative `HomeShellHost` bridge.
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
        val snackbarHostState = remember { SnackbarHostState() }
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

        // The arrivals sheet's measurement state, reported by the panel via onPreferredHeight — local to
        // the screen, since the panel and the sheet both live here (no need to round-trip the VM). Seeded
        // at the two-arrivals height so the first reveal doesn't flash undersized (legacy default), and
        // arrivalsReady gates the peek open until the focused stop's arrivals load (reset on focus change).
        var peekArrivalCount by remember { mutableStateOf(2) }
        var routeFiltering by remember { mutableStateOf(false) }
        var arrivalsReady by remember { mutableStateOf(false) }
        LaunchedEffect(state.focusedStop?.id) { arrivalsReady = false }

        // The arrivals header height for the current preview count + filter offset (no drag handle).
        val peekHeaderDp = arrivalsPeekHeight(peekArrivalCount, routeFiltering)
        val peekHeaderPx = with(density) { peekHeaderDp.roundToPx() }

        // Grow the sheet peek by the system navigation-bar inset (height varies by handset) so the
        // collapsed peek's pinned header clears the bottom chrome. The panel matches this with its own
        // content inset (see ArrivalsPanel), so the revealed gap is empty rather than clipped content.
        val peekBottomPadding = navigationBarBottomPadding()

        // The full collapsed-sheet peek: the reported header (less the phantom in-panel-handle budget its
        // dimens still bake in — see LEGACY_IN_PANEL_HANDLE_BUDGET), plus the real scaffold drag handle
        // above it, plus the navigation-bar inset below. Both the scaffold's peek height and the FAB lift
        // use this, so the FABs clear the whole collapsed sheet (handle included), not just the header.
        val collapsedPeekDp =
            peekHeaderDp - LEGACY_IN_PANEL_HANDLE_BUDGET + DRAG_HANDLE_HEIGHT + peekBottomPadding

        // The drawer's live open fraction (0 = collapsed peek, 1 = fully expanded), read each frame by
        // the arrivals panel to morph the peek rows in lockstep with the drag. measureModifier feeds it
        // the sheet's container height (attached to the scaffold below).
        val sheetProgress = rememberSheetExpandProgress(sheetState, collapsedPeekDp)

        // Visibility is business state: the sheet is shown (its peek slid up) iff a stop is focused.
        // Because there's no `Hidden` drag anchor, "shown" is a plain flag that drives the animated peek
        // height rather than a sheet drag state. The key is the focused stop id while shown (else null),
        // so the effect reacts to focus/tab changes but NOT to a user drag (same stop -> same key).
        var sheetShown by remember { mutableStateOf(false) }
        val showSheet = shouldShowSheet(state.focusedStop)
        val sheetKey = if (showSheet) state.focusedStop?.id else null
        // Re-keyed on arrivalsReady so the peek slides up once the focused stop's arrivals load.
        LaunchedEffect(sheetKey, arrivalsReady) {
            if (sheetKey == null) {
                // Hide: an expanded sheet is first collapsed to peek (so it then slides straight down as
                // the peek retracts, rather than staying stuck at the top with no `Hidden` anchor to fall
                // to); peek == the current value otherwise, so this is a no-op there.
                runCatching {
                    if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand()
                }
                sheetShown = false
            } else {
                // Show only once the focused stop's arrivals have loaded, so the peek animates straight to
                // its final height. Growing to a stale height and then resizing when the count resolves
                // moves the peek anchor mid-animation, which strands the AnchoredDraggable (the sheet
                // sticks partway up). The effect re-runs when arrivalsReady flips true (cancelling this
                // wait); the timeout is a fallback so a stop whose arrivals are slow or fail still shows.
                if (!arrivalsReady) delay(SHEET_OPEN_LOAD_TIMEOUT_MS)
                sheetShown = true
            }
        }

        // The peek height actually handed to the scaffold: the real peek while shown, 0 while hidden.
        // Animating between the two slides the whole sheet up from / down past the bottom edge — the
        // slide-in/out that the removed `Hidden` anchor used to provide.
        val visiblePeekDp by animateDpAsState(
            targetValue = if (sheetShown) collapsedPeekDp else 0.dp,
            label = "sheetPeek",
        )

        // Report the resting position back to the activity (map padding / recenter / arrivals preview).
        // While hidden the sheet still rests at `PartiallyExpanded` (just with a 0 peek), so fold the
        // shown flag in: a hidden sheet reports `Hidden` (map padding 0), else its live expansion.
        LaunchedEffect(sheetState) {
            snapshotFlow {
                if (!sheetShown) ArrivalsSheetState.Hidden else sheetState.currentValue.toArrivalsSheetState()
            }.collect { value ->
                onSheetSettled(value, peekHeaderPx)
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
            onReminders = onReminders,
            onPlanTrip = onPlanTrip,
            onPayFare = onPayFare,
            onSettings = onSettings,
            onHelp = onHelp,
            onSendFeedback = onSendFeedback,
            onOpenSource = onOpenSource,
        ) {
            // Provide the tutorial state to the whole screen (top bar, map, and sheet) so their spotlight
            // anchors register; [TutorialOverlay] below draws from the same state.
            CompositionLocalProvider(LocalTutorialState provides tutorialState) {
            // The TopAppBar applies its own top window inset (status bar), so the Column doesn't.
            Column(Modifier.fillMaxSize()) {
                HomeTopBar(
                    // HOME is always the map now; the list tabs are their own destinations with their
                    // own top bars, so the home bar shows no list sort/clear.
                    title = stringResource(R.string.navdrawer_item_nearby),
                    showSort = false,
                    showClear = false,
                    clearLabel = R.string.my_option_clear_starred_stops,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSearch = onSearch,
                    onSort = {},
                    onClear = {},
                    onRecentStopsRoutes = onRecentStopsRoutes,
                    // Wire the top bar's overflow anchor slot to the "recent stops/routes" spotlight target.
                    overflowModifier = Modifier.tutorialAnchor(tutorialState, ArrivalTutorial.KEY_MORE_MENU),
                )
                BottomSheetScaffold(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(sheetProgress.measureModifier),
                    scaffoldState = scaffoldState,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    // The animated peek: real peek while shown, 0 while hidden — slides the sheet in/out.
                    sheetPeekHeight = visiblePeekDp,
                    // Paint the sheet container (incl. the strip behind the drag handle) the same color
                    // the arrivals panel body paints, so the handle reads as part of the panel rather
                    // than sitting on a separate default-colored strip.
                    sheetContainerColor = MaterialTheme.colorScheme.surface,
                    sheetDragHandle = { ArrivalsDragHandle(onToggle = toggleSheet) },
                    sheetContent = {
                        ArrivalsSheetHost(
                            focusedStop = state.focusedStop,
                            sheetVisible = sheetShown,
                            expandProgress = sheetProgress.fraction,
                            arrivalsViewModelFactory = arrivalsViewModelFactory,
                            onArrivalsLoaded = onArrivalsLoaded,
                            onShowRouteOnMap = onShowRouteOnMap,
                            onShowTrip = onShowTrip,
                            onEditReminder = onEditReminder,
                            onPreferredHeight = { count, filtering ->
                                peekArrivalCount = count
                                routeFiltering = filtering
                                arrivalsReady = true
                            },
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
                        HomeMapOverlays(
                            weatherViewModel = weatherViewModel,
                            donationViewModel = donationViewModel,
                            surveyViewModel = surveyViewModel,
                            routeHeader = routeHeader,
                            onCancelRouteMode = onCancelRouteMode,
                            // The switch-direction affordance calls straight into the map VM (which
                            // re-filters stops/vehicles + persists the choice), like the height report below.
                            onSelectRouteDirection = mapViewModel::selectRouteDirection,
                            onLearnMore = onLearnMore,
                            onOpenSurvey = onOpenSurvey,
                            // The route header reports its height straight to the map VM (which owns the
                            // padding derivation), so the host isn't a relay between the two features.
                            onRouteHeaderHeight = mapViewModel::setRouteHeaderHeight,
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
 * the toolbar hamburger (via the host-owned [drawerState]), so gestures are enabled only while it's
 * already open — a left-edge drag on the map must pan the map, not peel the drawer open.
 */
@Composable
private fun HomeDrawer(
    drawerState: DrawerState,
    onStarredStops: () -> Unit,
    onStarredRoutes: () -> Unit,
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
    onLearnMore: () -> Unit,
    onOpenSurvey: (url: String) -> Unit,
    onRouteHeaderHeight: (Int) -> Unit,
) {
    // The weather chip feature module: self-wiring from its ViewModel.
    WeatherFeature(
        viewModel = weatherViewModel,
        onNearby = true,
        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
    )
    // The donation feature module: the card (DonationsManager-gated) plus its dismiss dialog.
    DonationFeature(
        viewModel = donationViewModel,
        onNearby = true,
        onLearnMore = onLearnMore,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 62.dp)
    )
    // The route-mode header (Compose), top-aligned over the map — drawn above the weather/donation
    // cards so its opaque bar + cancel button own the top in route mode. Reports its height for the
    // map's top padding; clears it when dismissed.
    if (routeHeader != null) {
        RouteHeaderOverlay(
            header = routeHeader,
            onCancel = onCancelRouteMode,
            onSelectDirection = onSelectRouteDirection,
            onHeight = onRouteHeaderHeight,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        )
    } else {
        LaunchedEffect(Unit) { onRouteHeaderHeight(0) }
    }
    // The map survey (Compose): hero card over the map + remaining-questions sheet. Self-wiring from
    // its ViewModel; self-triggers its request once a region has resolved.
    SurveyFeature(
        viewModel = surveyViewModel,
        onNearby = true,
        onOpenSurvey = onOpenSurvey,
        modifier = Modifier.align(Alignment.TopCenter),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private fun SheetValue.toArrivalsSheetState() = when (this) {
    SheetValue.Hidden -> ArrivalsSheetState.Hidden
    SheetValue.PartiallyExpanded -> ArrivalsSheetState.Collapsed
    SheetValue.Expanded -> ArrivalsSheetState.Expanded
}

/** Maps the arrivals preview count + route-filter flag to the collapsed peek header height. */
@Composable
private fun arrivalsPeekHeight(arrivalCount: Int, filtering: Boolean): Dp {
    val base = dimensionResource(
        when (arrivalsPeekTier(arrivalCount)) {
            ArrivalsPeekTier.TWO_OR_MORE -> R.dimen.arrival_header_height_two_arrivals
            ArrivalsPeekTier.ONE -> R.dimen.arrival_header_height_one_arrival
            ArrivalsPeekTier.NONE -> R.dimen.arrival_header_height_no_arrivals
        }
    )
    val offset = if (filtering) {
        dimensionResource(R.dimen.arrival_header_height_offset_filter_routes)
    } else {
        0.dp
    }
    return base + offset
}

// The custom drag handle's geometry — the visible bar plus the vertical padding above and below it.
// Shared by [ArrivalsDragHandle] (what it draws) and the peek-height math (how much room it needs), so
// the two can't drift apart.
private val DRAG_HANDLE_BAR_HEIGHT = 4.dp
private val DRAG_HANDLE_VERTICAL_PADDING = 9.dp
private val DRAG_HANDLE_HEIGHT = DRAG_HANDLE_BAR_HEIGHT + DRAG_HANDLE_VERTICAL_PADDING * 2

// The reported arrivals-header dimens (arrival_header_height_*) still bake in this much room for the
// legacy in-panel handle the scaffold drag handle replaced; collapsedPeekDp subtracts it back out and
// adds the real DRAG_HANDLE_HEIGHT instead.
private val LEGACY_IN_PANEL_HANDLE_BUDGET = 20.dp

/**
 * The arrivals sheet's drag handle: a short grab bar tinted to sit on the panel surface (paired with
 * the scaffold's `sheetContainerColor`) so it reads as part of the panel, not a separate strip. Tapping
 * toggles peek<->full via [onToggle]; it never hides the sheet — the sheet has no `Hidden` drag anchor,
 * so it only leaves programmatically (animated peek height) when focus clears. Its own click shadows the
 * scaffold's built-in handle click.
 */
@Composable
private fun ArrivalsDragHandle(onToggle: () -> Unit) {
    // The click sits on the outer padded Box (before the padding) so the tap target covers more than the
    // bar; it shadows the scaffold's own handle click. The bar itself is a short tinted pill.
    Box(
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = DRAG_HANDLE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // Same muted grey as the header's star/icon tint, so the handle matches the panel chrome.
            color = colorResource(R.color.navdrawer_icon_tint),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Box(Modifier.size(width = 32.dp, height = DRAG_HANDLE_BAR_HEIGHT))
        }
    }
}

/**
 * How long the sheet waits for a freshly-focused stop's arrivals to load before peeking open anyway.
 * The common path opens sooner — the open effect re-runs the instant arrivals load (arrivalsReady) —
 * so this only bounds the wait when a stop's arrivals are slow or fail, ensuring its sheet still shows.
 */
private const val SHEET_OPEN_LOAD_TIMEOUT_MS = 800L
