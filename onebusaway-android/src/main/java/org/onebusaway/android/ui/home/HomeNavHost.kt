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
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.map.compose.TripMapScreen
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.ObaAnalytics
import org.onebusaway.android.io.PlausibleAnalytics
import org.onebusaway.android.io.elements.ObaRegion
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.report.ui.reportGraph
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.arrivals.arrivalsGraph
import org.onebusaway.android.ui.compose.components.OptOutInfoDialog
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.home.donation.DonationViewModel
import org.onebusaway.android.ui.home.help.HelpViewModel
import org.onebusaway.android.ui.home.nav.extraDestinations
import org.onebusaway.android.ui.home.weather.WeatherViewModel
import org.onebusaway.android.ui.mylists.myListsGraph
import org.onebusaway.android.ui.nav.NavHelp
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.settings.settingsGraph
import org.onebusaway.android.ui.survey.SurveyViewModel
import org.onebusaway.android.ui.tripdetails.tripGraph
import org.onebusaway.android.ui.tripplan.tripPlanGraph
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.PreferenceUtils

/**
 * The HOME destination's dependency surface — the one destination that consumes the full home bundle
 * (the feature ViewModels, the list VMs, the arrivals factory, the callbacks, and the map seed). Built
 * once in [HomeActivity.onCreate] and passed to [HomeNavHost]. Every *other* destination instead
 * recovers the host via `LocalContext.current.findActivity()` and reads its (non-private) members, so
 * only HOME needs this holder (the six feature VMs + the list VMs are private to the activity).
 */
class HomeDestinationDeps(
    val homeViewModel: HomeViewModel,
    val mapViewModel: MapViewModel,
    val surveyViewModel: SurveyViewModel,
    val donationViewModel: DonationViewModel,
    val weatherViewModel: WeatherViewModel,
    val helpViewModel: HelpViewModel,
    val arrivalsViewModelFactory: ArrivalsViewModel.Factory,
    val callbacks: HomeCallbacks,
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
        composable(NavRoutes.HOME) {
            val state by home.homeViewModel.uiState.collectAsStateWithLifecycle()
            val routeHeader by home.mapViewModel.routeHeader.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                sheetCommands = home.homeViewModel.sheetCommands,
                homeViewModel = home.homeViewModel,
                mapViewModel = home.mapViewModel,
                routeHeader = routeHeader,
                surveyViewModel = home.surveyViewModel,
                donationViewModel = home.donationViewModel,
                weatherViewModel = home.weatherViewModel,
                helpViewModel = home.helpViewModel,
                arrivalsViewModelFactory = home.arrivalsViewModelFactory,
                callbacks = home.callbacks,
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
 * Consumes a staged deep-link route (the [HomeViewModel.deepLinkRoute] latch) once the NavHost is ready
 * (and on each `onNewIntent`): navigates to it, popping up to HOME, then clears the latch via
 * [onConsumed]. Lifted verbatim from the former inline `onCreate` effect.
 */
@Composable
internal fun DeepLinkEffect(
    navController: NavHostController,
    deepLinkRoute: StateFlow<String?>,
    onConsumed: () -> Unit,
) {
    val pending by deepLinkRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { route ->
            navController.navigate(route) {
                popUpTo(NavRoutes.HOME) { inclusive = false }
                launchSingleTop = true
            }
            onConsumed()
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
        ObaAnalytics.setAccessibility(
            FirebaseAnalytics.getInstance(context),
            am.isTouchExplorationEnabled,
        )
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
            val firebase = FirebaseAnalytics.getInstance(context)
            val plausible = Application.get().plausibleInstance
            when (event) {
                is HomeAnalyticsEvent.RegionSelected ->
                    ObaAnalytics.setRegion(plausible, firebase, event.regionName)
                is HomeAnalyticsEvent.MenuItem ->
                    ObaAnalytics.reportUiEvent(
                        firebase, plausible, PlausibleAnalytics.REPORT_MENU_EVENT_URL,
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
    val activity = LocalContext.current.findActivity() as HomeActivity
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
    paymentWarning: StateFlow<ObaRegion?>,
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
