/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.onebusaway.android.R
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.region.Region
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.compose.TripMapScreen
import org.onebusaway.android.report.ui.reportGraph
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.arrivalsGraph
import org.onebusaway.android.ui.compose.components.OptOutInfoDialog
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.home.donation.DonationViewModel
import org.onebusaway.android.ui.home.help.HelpAction
import org.onebusaway.android.ui.home.help.HelpViewModel
import org.onebusaway.android.ui.home.nav.extraDestinations
import org.onebusaway.android.ui.home.weather.WeatherViewModel
import org.onebusaway.android.ui.mylists.myListsGraph
import org.onebusaway.android.ui.nav.IntentRouteMapper
import org.onebusaway.android.ui.nav.NavHelp
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.RESULT_MAP_ROUTE_ID
import org.onebusaway.android.ui.nav.consumeRouteReveal
import org.onebusaway.android.ui.nav.RESULT_MAP_STOP_ID
import org.onebusaway.android.ui.nav.consumeStopReveal
import org.onebusaway.android.ui.nav.navigateFromHome
import org.onebusaway.android.ui.settings.settingsGraph
import org.onebusaway.android.ui.survey.SurveyViewModel
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher
import org.onebusaway.android.ui.tripdetails.tripGraph
import org.onebusaway.android.ui.tripplan.tripPlanGraph
import org.onebusaway.android.ui.tutorial.ArrivalTutorial
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.PreferenceUtils

private const val TAG = "HomeNavHost"

/**
 * The HOME destination's dependency surface — the one destination that consumes the full home bundle
 * (the feature ViewModels, the list VMs, the arrivals factory, the Activity-bound [activityActions], and
 * the map seed). Built once in [HomeActivity.onCreate] and passed to [HomeNavHost]. Every *other*
 * destination instead recovers the host via `LocalContext.current.findActivity()` and reads its
 * (non-private) members, so only HOME needs this holder (the six feature VMs + the list VMs are private
 * to the activity).
 */
class HomeDestinationDeps(
    val homeViewModel: HomeViewModel,
    val mapViewModel: MapViewModel,
    val surveyViewModel: SurveyViewModel,
    val donationViewModel: DonationViewModel,
    val weatherViewModel: WeatherViewModel,
    val helpViewModel: HelpViewModel,
    val arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    val activityActions: HomeActivityActions,
)

/**
 * The single-Activity Navigation-Compose backbone: every screen is a NavHost destination.
 * Hosted by [HomeActivity] (which created [navController] and staged any external deep-link route);
 * external intents are translated to routes by `IntentRouteMapper`. Extracted out of `onCreate` so the
 * activity is a thin Compose-host shell. The HOME destination consumes [home]; the rest live in
 * per-feature `NavGraphBuilder` graphs that recover the host via [findActivity].
 */
@Composable
fun HomeNavHost(
    navController: NavHostController,
    home: HomeDestinationDeps,
) {
    NavHost(navController = navController, startDestination = NavRoutes.HOME) {
        composable(NavRoutes.HOME) { entry ->
            // Apply a one-shot "reveal on map" handed back by a pushed destination (route info, search,
            // the My* lists) via HOME's own SavedStateHandle, then consume it (set-null) so it neither
            // re-fires on recomposition nor survives a later route-mode exit + process death (MapViewModel's
            // persisted ROUTE_ID stays the restore authority). The map/home VMs are already in scope here.
            val handle = entry.savedStateHandle
            val revealRouteId by handle.getStateFlow<String?>(RESULT_MAP_ROUTE_ID, null)
                .collectAsStateWithLifecycle()
            val revealStopId by handle.getStateFlow<String?>(RESULT_MAP_STOP_ID, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(revealRouteId) {
                if (revealRouteId == null) return@LaunchedEffect
                // Read + consume the route id and its optional direction anchor atomically via the typed
                // helper (which owns both key names), mirroring the stop branch below.
                val request = handle.consumeRouteReveal() ?: return@LaunchedEffect
                home.mapViewModel.toRoute(request)
            }
            LaunchedEffect(revealStopId) {
                if (revealStopId == null) return@LaunchedEffect
                // Read + consume all three keys atomically via the typed helper (which owns the key names
                // and Double types). A non-null result applies the focus; a null result here means STOP_ID
                // was present but lat/lon were missing.
                val reveal = handle.consumeStopReveal()
                if (reveal != null) {
                    home.homeViewModel.onStopFocused(
                        FocusedStop(reveal.stopId, null, null, reveal.lat, reveal.lon)
                    )
                    home.homeViewModel.markPendingMapFocus()
                } else {
                    // Keys already consumed; record the dropped focus so the latent path is findable
                    // (see consumeStopReveal for why this is only reachable on a corrupted handle).
                    Log.w(TAG, "Dropped a partial stop reveal: stop id present but lat/lon missing")
                    FirebaseCrashlytics.getInstance().recordException(
                        IllegalStateException("Partial stop reveal: stop id present but lat/lon missing")
                    )
                }
            }
            val state by home.homeViewModel.uiState.collectAsStateWithLifecycle()
            val routeHeader by home.mapViewModel.routeHeader.collectAsStateWithLifecycle()
            // The in-app navigation lambdas: built here, where the NavController is in scope (the rest of
            // the home callbacks — the Activity-bound ones — arrive via [home.activityActions]). Each pops
            // up to HOME + de-dupes the top via [navigateFromHome], and the menu rows report their analytics.
            // Remembered so the ~20 lambdas aren't re-allocated on every HOME recomposition.
            val context = LocalContext.current
            val callbacks = remember(navController, home, context) {
                val a = home.activityActions
                // A menu row: navigate (popping to HOME) + report its analytics.
                fun menuNav(route: String, @StringRes label: Int) {
                    navController.navigateFromHome(route)
                    home.homeViewModel.reportMenuAnalytics(label)
                }
                HomeCallbacks(
                    activityActions = a,
                    onStarredStops = { menuNav(NavRoutes.HOME_STARRED_STOPS, R.string.analytics_label_button_press_star) },
                    onStarredRoutes = { menuNav(NavRoutes.HOME_STARRED_ROUTES, R.string.analytics_label_button_press_star) },
                    onReminders = { menuNav(NavRoutes.MY_REMINDERS, R.string.analytics_label_button_press_reminders) },
                    onPlanTrip = { menuNav(NavRoutes.TRIP_PLAN, R.string.analytics_label_button_press_trip_plan) },
                    onSettings = { menuNav(NavRoutes.SETTINGS, R.string.analytics_label_button_press_settings) },
                    onSearch = { query -> navController.navigateFromHome(NavRoutes.search(query)) },
                    onRecentStopsRoutes = {
                        // The user found the overflow on their own — don't later spotlight it in onboarding.
                        PreferencesEntryPoint.get(context).setBoolean(ArrivalTutorial.KEY_MORE_MENU, true)
                        navController.navigateFromHome(NavRoutes.myRecent())
                    },
                    onHelpAction = { action ->
                        if (action == HelpAction.AGENCIES) navController.navigateFromHome(NavRoutes.AGENCIES)
                        else a.onHelpActionExternal(action)
                    },
                    onShowTrip = { tripId, stopId ->
                        navController.navigateFromHome(
                            NavRoutes.tripDetails(tripId, stopId, TripDetailsLauncher.SCROLL_MODE_STOP)
                        )
                    },
                    onEditReminder = { args -> navController.navigateFromHome(NavRoutes.tripInfo(args)) },
                    onLearnMore = { navController.navigateFromHome(NavRoutes.DONATION_LEARN_MORE) },
                    onOpenSurvey = { url -> navController.navigateFromHome(NavRoutes.surveyWebView(url)) },
                )
            }
            HomeScreen(
                state = state,
                homeViewModel = home.homeViewModel,
                mapViewModel = home.mapViewModel,
                routeHeader = routeHeader,
                surveyViewModel = home.surveyViewModel,
                donationViewModel = home.donationViewModel,
                weatherViewModel = home.weatherViewModel,
                helpViewModel = home.helpViewModel,
                arrivalsViewModelFactory = home.arrivalsViewModelFactory,
                callbacks = callbacks,
            )
        }
        // Trip-focus map (speed estimation): drives the shared MapViewModel, so it's registered here
        // where home.mapViewModel is in scope (rather than in a findActivity-based graph).
        composable(
            NavRoutes.TRIP_MAP,
            arguments = listOf(
                navArgument(NavRoutes.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(NavRoutes.ARG_LINE_COLOR) { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            ObaTheme {
                TripMapScreen(
                    mapViewModel = home.mapViewModel,
                    tripId = entry.arguments?.getString(NavRoutes.ARG_TRIP_ID).orEmpty(),
                    lineColorArgb = entry.arguments?.getInt(NavRoutes.ARG_LINE_COLOR) ?: 0,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // The rest of the graph, grouped by feature (each a NavGraphBuilder extension near its
        // feature; they recover the host via findActivity rather than threading dependencies).
        arrivalsGraph(navController)
        tripGraph(navController)
        myListsGraph(navController)
        homeListsGraph(navController)
        settingsGraph(navController)
        reportGraph(navController)
        tripPlanGraph(navController)
        extraDestinations(navController)
    }
}

/**
 * Drains [HomeActivity]'s `launchIntents` channel once the NavHost is composed (cold launch) and as each
 * `onNewIntent` enqueues more (warm relaunch): for each external intent it runs the intent's domain side
 * effects, translates it to a route via [IntentRouteMapper] (null = stay on the map), then navigates there
 * popping up to HOME. This is the only navigation that can't hold the NavController itself — the OS hands
 * intents to the Activity, which exists before/around the composition. The channel is an UNLIMITED queue
 * (not a latch) so rapid, distinct back-to-back intents are each delivered exactly once rather than
 * overwriting one another before the collector consumes them (#1582).
 *
 * The collect is gated on `repeatOnLifecycle(STARTED)` so we never navigate while the activity is STOPPED.
 * The UNLIMITED channel buffers any intent submitted during that window and replays it when the lifecycle
 * returns to STARTED, so the gate costs no deliveries (#1592).
 */
@Composable
internal fun LaunchIntentEffect(
    navController: NavHostController,
    launchIntents: Flow<Intent>,
    onSideEffects: (Intent) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(navController, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launchIntents.collect { i ->
                onSideEffects(i)
                IntentRouteMapper.routeForIntent(i)?.let { navController.navigateFromHome(it) }
            }
        }
    }
}

/**
 * Reports the device accessibility (TalkBack) state to Firebase on each ON_START — replaces the former
 * HomeActivity.onStart. Re-reports on every foreground so a TalkBack toggle made while backgrounded is
 * captured. Fetches the analytics process-singleton from the Context per the codebase convention
 * (ExtraDestinations/MapFeature) rather than holding it as a host field.
 */
@Composable
internal fun AccessibilityAnalyticsEffect() {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        AnalyticsEntryPoint.get(context).setAccessibility(am.isTouchExplorationEnabled)
    }
}

/**
 * Reports the ViewModel's [HomeAnalyticsEvent]s (region auto-selects, nav/help menu selections) to
 * Firebase + Plausible. The single home-screen home for the formerly scattered imperative `ObaAnalytics`
 * calls; the decision of *what* to report stays in the VM, dispatch (which needs a `Context`) lives here.
 */
@Composable
internal fun HomeAnalyticsEffect(analyticsEvents: SharedFlow<HomeAnalyticsEvent>) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(analyticsEvents) {
        analyticsEvents.collect { event ->
            val analytics = AnalyticsEntryPoint.get(context)
            when (event) {
                is HomeAnalyticsEvent.RegionSelected ->
                    analytics.setRegion(event.regionName)
                is HomeAnalyticsEvent.MenuItem ->
                    analytics.reportUiEvent(
                        PlausibleAnalytics.REPORT_MENU_EVENT_URL,
                        resources.getString(event.labelRes), null,
                    )
            }
        }
    }
}

/**
 * Re-home when leaving the settings subtree if the user re-enabled auto-select-region or changed the
 * custom OTP URL (ported from the former SettingsActivity.onDestroy). Both baselines (the auto-select
 * pref and the OTP URL pref) are captured on entry and compared on exit, so a re-home fires only when
 * one actually changed during this settings visit. Recovers the host only for the go-home helper.
 */
@Composable
internal fun SettingsRehomeEffect(navController: NavHostController) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(navController) {
        val settingsRoutes = setOf(NavRoutes.SETTINGS, NavRoutes.SETTINGS_ADVANCED)
        val autoSelectKey = activity.getString(R.string.preference_key_auto_select_region)
        val otpUrlKey = activity.getString(R.string.preference_key_otp_api_url)
        var autoSelectInitial: Boolean? = null
        var otpUrlInitial: String? = null
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route in settingsRoutes) {
                if (autoSelectInitial == null) {
                    autoSelectInitial = PreferenceUtils.getBoolean(autoSelectKey, true)
                    otpUrlInitial = PreferenceUtils.getString(otpUrlKey)
                }
            } else if (autoSelectInitial != null) {
                val reEnabledAutoSelect =
                    PreferenceUtils.getBoolean(autoSelectKey, true) && autoSelectInitial == false
                // Compare the persisted OTP URL itself (entry vs exit) rather than a host-held "changed"
                // flag — same idiom as auto-select above, so no blackboard state lives on the activity.
                val otpUrlChanged = PreferenceUtils.getString(otpUrlKey) != otpUrlInitial
                autoSelectInitial = null
                otpUrlInitial = null
                if (reEnabledAutoSelect || otpUrlChanged) {
                    NavHelp.goHome(activity, false)
                }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

/**
 * The fare-payment warning (former imperative payment_warning_dialog): shown over any destination when
 * the PAY_FARE menu item needs a region's warning before launching. Observes the dialog state the
 * [HomeViewModel] owns ([paymentWarning]); [onDismiss] clears it on confirm/dismiss.
 */
@Composable
internal fun PaymentWarningDialog(
    paymentWarning: StateFlow<Region?>,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val warnRegion by paymentWarning.collectAsStateWithLifecycle()
    warnRegion?.let { region ->
        ObaTheme {
            OptOutInfoDialog(
                title = region.paymentWarningTitle.orEmpty(),
                icon = painterResource(android.R.drawable.ic_dialog_alert),
                iconTint = colorResource(R.color.alert_icon_error),
                body = region.paymentWarningBody.orEmpty(),
                optOutLabel = stringResource(R.string.main_never_ask_again),
                onOptOut = {
                    PreferenceUtils.saveBoolean(
                        activity.getString(R.string.preference_key_never_show_payment_warning_dialog), it
                    )
                },
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    onDismiss()
                    ExternalIntents.startPaymentIntent(activity, region)
                },
                onDismissRequest = onDismiss,
            )
        }
    }
}
