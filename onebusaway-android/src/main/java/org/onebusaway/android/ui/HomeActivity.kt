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
package org.onebusaway.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.io.request.ObaArrivalInfoResponse
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.report.ui.ReportLauncher
import org.onebusaway.android.travelbehavior.TravelBehaviorManager
import org.onebusaway.android.ui.nav.NavHelp
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.home.donation.DonationViewModel
import org.onebusaway.android.ui.home.weather.WeatherViewModel
import org.onebusaway.android.ui.home.AccessibilityAnalyticsEffect
import org.onebusaway.android.ui.home.HomeAnalyticsEffect
import org.onebusaway.android.ui.home.help.HelpAction
import org.onebusaway.android.ui.home.help.HelpViewModel
import org.onebusaway.android.ui.home.HomeCallbacks
import org.onebusaway.android.ui.home.ReportTarget
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.HomeScreen
import org.onebusaway.android.ui.home.HomeViewModel
import org.onebusaway.android.ui.home.HomeNavHost
import org.onebusaway.android.ui.home.HomeDestinationDeps
import org.onebusaway.android.ui.home.DeepLinkEffect
import org.onebusaway.android.ui.home.SettingsRehomeEffect
import org.onebusaway.android.ui.home.PaymentWarningDialog
import org.onebusaway.android.ui.nav.IntentRouteMapper
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.survey.SurveyViewModel
import org.onebusaway.android.ui.tutorial.ArrivalTutorial
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.ReminderUtils
import org.onebusaway.android.ui.tutorial.TutorialPrefs

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    // Shared with the NavHost destinations (My* / report / trip-details) that read preferences off the host.
    @Inject
    lateinit var prefsRepository: PreferencesRepository

    // Builds the per-stop ArrivalsViewModel for the home bottom-sheet host. Assisted because the sheet's
    // stop id is runtime-dynamic.
    @Inject
    lateinit var arrivalsViewModelFactory: ArrivalsViewModel.Factory

    // The add-region deep link applies custom API URLs through this (rather than reaching Application.get()).
    @Inject
    lateinit var regionRepository: RegionRepository

    // The last-known location, read off the host by the infrastructure-issue report screen to submit.
    @Inject
    lateinit var locationRepository: LocationRepository

    private val viewModel: HomeViewModel by viewModels()

    // The single source of truth for the map. MapFeature (in HomeScreen) renders it and self-wires its
    // callbacks/collectors/effects/lifecycle, and owns its mode + camera persistence via SavedStateHandle.
    private val mapViewModel: MapViewModel by viewModels()

    // The map survey (Compose), shown over the map on NEARBY. Activity-scoped.
    private val surveyViewModel: SurveyViewModel by viewModels()

    // The donation card feature module (Compose), shown over the map on NEARBY. Activity-scoped.
    private val donationViewModel: DonationViewModel by viewModels()

    // The weather chip feature module (Compose), shown over the map on NEARBY. Activity-scoped;
    // Hilt injects its weather + region dependencies.
    private val weatherViewModel: WeatherViewModel by viewModels()

    // The help / what's-new / legend dialogs feature module. Activity-scoped.
    private val helpViewModel: HelpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The whole screen is Compose: a ModalNavigationDrawer + HomeTopBar + BottomSheetScaffold whose
        // bottom sheet is the per-stop arrivals panel (ArrivalsSheetHost). No map-related View seam remains.

        val homeCallbacks = buildHomeCallbacks()

        // Stage any external "open this screen" intent and run its side effects, before setContent so
        // [DeepLinkEffect] observes it once the NavHost composes. Fresh launch only, so a rotation doesn't
        // re-fire reminder deletes / URL applies.
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val navController = rememberNavController()
            AccessibilityAnalyticsEffect()
            HomeAnalyticsEffect(viewModel.analyticsEvents)
            DeepLinkEffect(navController, viewModel.deepLinkRoute, viewModel::onDeepLinkRouteConsumed)
            // The welcome tutorial (now the Compose green welcome + map-stop spotlight sequence) is
            // started by HomeScreen off the same showWelcomeTutorial latch — no host effect needed.
            SettingsRehomeEffect(navController)
            HomeNavHost(
                navController = navController,
                home = HomeDestinationDeps(
                    homeViewModel = viewModel,
                    mapViewModel = mapViewModel,
                    surveyViewModel = surveyViewModel,
                    donationViewModel = donationViewModel,
                    weatherViewModel = weatherViewModel,
                    helpViewModel = helpViewModel,
                    arrivalsViewModelFactory = arrivalsViewModelFactory,
                    callbacks = homeCallbacks,
                ),
            )
            PaymentWarningDialog(viewModel.paymentWarning, viewModel::dismissPaymentWarning)
        }

        setupMapState()

        TravelBehaviorManager(this, applicationContext).registerTravelBehaviorParticipant()

        // The VM owns the startup region-check decision (defer to the map's permission result on a first
        // launch without permission, else check now). The permission read needs a Context, so it stays here.
        viewModel.onHomeStarted(
            PermissionUtils.hasGrantedAtLeastOnePermission(this, PermissionUtils.LOCATION_PERMISSIONS)
        )
    }

    /**
     * Bundles the home screen's tap/UI lambdas ([HomeCallbacks]) — a mix of activity-method references
     * and [HomeViewModel] method references — passed down to [HomeScreen] via the HOME destination.
     */
    private fun buildHomeCallbacks(): HomeCallbacks = HomeCallbacks(
        // The content rows navigate to their destinations (the map / the list screens). Each reports
        // its menu analytics, as the tab selections did.
        onStarredStops = {
            navigateToHomeRow(NavRoutes.HOME_STARRED_STOPS, R.string.analytics_label_button_press_star)
        },
        onStarredRoutes = {
            navigateToHomeRow(NavRoutes.HOME_STARRED_ROUTES, R.string.analytics_label_button_press_star)
        },
        onReminders = {
            navigateToHomeRow(NavRoutes.MY_REMINDERS, R.string.analytics_label_button_press_reminders)
        },
        onPlanTrip = ::onPlanTripSelected,
        onPayFare = ::onPayFareSelected,
        onSettings = ::onSettingsSelected,
        onHelp = ::onHelpSelected,
        onSendFeedback = ::onSendFeedbackSelected,
        onOpenSource = ::onOpenSourceSelected,
        onSearch = ::onSearch,
        onRecentStopsRoutes = ::onRecentStopsRoutes,
        onHelpAction = ::onHelpAction,
        // Stage the welcome sequence on the VM latch; HomeScreen starts it when the latch fires.
        onShowWelcomeTutorial = viewModel::requestWelcomeTutorial,
        onRegionChosen = viewModel::onRegionChosen,
        onSheetSettled = viewModel::onSheetSettled,
        onClearFocus = viewModel::requestClearMapFocus,
        onArrivalsLoaded = ::onArrivalsLoaded,
        onShowRouteOnMap = viewModel::requestShowRouteOnMap,
        onToggleSheet = viewModel::requestToggleSheet,
        onCancelRouteMode = ::onCancelRouteMode,
    )

    /**
     * A warm re-launch (singleTop) carrying an external screen intent — FCM CLEAR_TOP, the
     * NavigationService reminder PendingIntent, a pinned shortcut. Stage its route; the NavHost's
     * LaunchedEffect navigates. (Cold launches are handled in onCreate.)
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * In-app "show route on map": surface the map and enter route mode. The single-Activity replacement
     * for the old `HomeActivity.start(routeId)` intent round-trip — every in-app caller reaches this
     * directly instead of building a MapParams intent. [revealMap] navigates to the HOME (map) destination.
     */
    fun showRouteOnMap(routeId: String) {
        mapViewModel.toRoute(routeId)
        revealMap()
    }

    /**
     * In-app "show stop on map": surface the map and focus [stopId], recentering on it and peeking its
     * arrivals once they load. Replaces `HomeActivity.start(stopId, lat, lon)`. Unlike the cold-launch
     * [setupMapState]/applyInitialFocus (which only adopts a stop when none is focused), an explicit
     * "show on map" focuses this stop even if another is already up.
     */
    fun focusStopOnMap(stopId: String, lat: Double, lon: Double) {
        viewModel.onStopFocused(FocusedStop(stopId, null, null, lat, lon))
        viewModel.markPendingMapFocus()
        revealMap()
    }

    /**
     * Pops the NavHost back to HOME so the map is actually on screen. The "show on map" actions fire from
     * pushed destinations (trip details, route info, the My* lists, search, full-screen arrivals); without
     * this the map enters route/focus mode underneath but stays hidden behind that screen. Routes to HOME
     * through the same staged-deep-link path (which pops up to HOME), so it's a no-op when HOME is already
     * current. Restores the reveal the old `HomeActivity.start(MapParams…)` intent round-trip provided.
     */
    private fun revealMap() = viewModel.stageDeepLinkRoute(NavRoutes.HOME)

    /**
     * Navigate the NavHost to an in-app [route] (a [NavRoutes] string) — the single-Activity replacement
     * for `startActivity(launcher intent)` from in-app code that has no `navController` in scope (the
     * arrivals sheet, the My* helpers, the Compose overlays). Goes through the same staged-route path
     * the old intent round-trip resolved to, so the back-stack behavior is unchanged.
     */
    fun navigateTo(route: String) = viewModel.stageDeepLinkRoute(route)

    /** A drawer content row was tapped: navigate to its [route] (popping to HOME) and report analytics. */
    private fun navigateToHomeRow(route: String, @StringRes analyticsLabel: Int) {
        navigateTo(route)
        viewModel.reportMenuAnalytics(analyticsLabel)
    }

    /**
     * Handles an incoming external intent: runs any domain side effects it implies, then stages the
     * NavHost route it should open. Both entry points (cold launch in [onCreate], warm re-launch in
     * [onNewIntent]) funnel through here so the side-effect-then-route sequence stays in one place.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        applyIntentSideEffects(intent)
        viewModel.stageDeepLinkRoute(IntentRouteMapper.routeForIntent(intent))
        // "Show tutorials again" re-launches with this extra. Staging in this shared funnel (not just
        // onCreate) makes both the cold first-run and the singleTop warm re-launch (onNewIntent) honor it.
        if (intent?.extras?.getBoolean(TutorialPrefs.TUTORIAL_WELCOME) == true) {
            viewModel.requestWelcomeTutorial()
        }
    }

    /**
     * Runs the domain mutations implied by certain incoming intents, kept out of [IntentRouteMapper]'s
     * pure route mapping so that stays a side-effect-free translator: the `add-region` deep link applies
     * custom API URLs (clearing the region), and the FCM payload clears the now-fired reminder.
     */
    private fun applyIntentSideEffects(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (data?.scheme == IntentRouteMapper.ADD_REGION_SCHEME &&
            data.host == IntentRouteMapper.ADD_REGION_HOST
        ) {
            // Validating and applying the URLs is the region domain's job; we just parse them off the URI.
            regionRepository.applyCustomApiUrls(
                obaUrl = data.getQueryParameter("oba-url"),
                otpUrl = data.getQueryParameter("otp-url"),
            )
            return
        }
        intent.getStringExtra(ReminderUtils.ARRIVAL_PAYLOAD_KEY)?.let { arrivalJson ->
            ReminderUtils.handleArrivalPayload(applicationContext, arrivalJson)
        }
    }


    // --- Settings preference-screen host glue (re-homed from the former SettingsActivity) ------------

    /** Re-resolves the region after a backup restore (called from SettingsScreen's restore launcher),
     *  in case the restored data implies a different region — raising the picker if it's ambiguous. */
    fun refreshRegionsAfterRestore() {
        viewModel.refreshRegions()
    }

    /** The advanced-settings "refresh regions" (experimental-regions toggle) action, forwarded to the
     *  VM. Public so the (extracted) SETTINGS_ADVANCED destination can reach it off the host. */
    fun onExperimentalRegionsToggled() {
        viewModel.onExperimentalRegionsToggled()
    }

    // --- Nav-drawer one-shot actions (each navigates/launches + reports its own analytics) -----------
    // These were the launcher branches of the old goToNavDrawerItem when-switch; as plain per-row
    // callbacks they no longer route through the tab-selection path. The survey / donation / weather /
    // layers overlays gate themselves off the VM state, so there's no imperative show/hide here.

    private fun onPlanTripSelected() {
        viewModel.stageDeepLinkRoute(NavRoutes.TRIP_PLAN)
        viewModel.reportMenuAnalytics(R.string.analytics_label_button_press_trip_plan)
    }

    /** PAY_FARE shows a fare-payment warning (or launches the payment app) and reports no analytics. */
    private fun onPayFareSelected() {
        ExternalIntents.payFareOrWarningRegion(this)?.let { viewModel.showPaymentWarning(it) }
    }

    private fun onSettingsSelected() {
        viewModel.stageDeepLinkRoute(NavRoutes.SETTINGS)
        viewModel.reportMenuAnalytics(R.string.analytics_label_button_press_settings)
    }

    private fun onHelpSelected() {
        helpViewModel.showMenu()
        viewModel.reportMenuAnalytics(R.string.analytics_label_button_press_help)
    }

    private fun onSendFeedbackSelected() {
        goToSendFeedBack()
        viewModel.reportMenuAnalytics(R.string.analytics_label_button_press_feedback)
    }

    private fun onOpenSourceSelected() {
        ExternalIntents.goToUrl(this, getString(R.string.open_source_github))
        viewModel.reportMenuAnalytics(R.string.analytics_label_button_press_open_source)
    }

    /** The route header's cancel button: return to stop mode, preserving the current zoom + center. */
    private fun onCancelRouteMode() {
        mapViewModel.exitRouteMode()
    }

    /**
     * Runs the global search for [query] (from [HomeTopBar]'s search field) by navigating to the
     * search destination (staged through the VM's deep-link route since the navController lives in the
     * NavHost composition, not here).
     */
    private fun onSearch(query: String) {
        viewModel.stageDeepLinkRoute(NavRoutes.search(query))
    }

    /** Opens the recent stops/routes screen (the toolbar overflow item) — the MY_RECENT destination. */
    private fun onRecentStopsRoutes() {
        // The user found the overflow on their own — don't later spotlight it in the onboarding tutorial.
        prefsRepository.setBoolean(ArrivalTutorial.KEY_MORE_MENU, true)
        viewModel.stageDeepLinkRoute(NavRoutes.myRecent())
    }

    // --- Help-menu actions that are Activity operations (the dialog-opening ones live in HelpFeature) ---

    private fun onHelpAction(action: HelpAction) {
        when (action) {
            HelpAction.TUTORIALS -> {
                TutorialPrefs.resetAllTutorials(this)
                NavHelp.goHome(this, true)
            }
            HelpAction.AGENCIES -> viewModel.stageDeepLinkRoute(NavRoutes.AGENCIES)
            HelpAction.TWITTER -> {
                // The VM derives which URL fits the current region; the host just fires the ACTION_VIEW.
                // Analytics rides the VM's event so the ObaAnalytics call lives in HomeAnalyticsEffect.
                ExternalIntents.goToUrl(this, helpViewModel.twitterUrl())
                viewModel.reportMenuAnalytics(R.string.analytics_label_twitter)
            }
            HelpAction.CONTACT_US -> goToSendFeedBack()
            // LEGEND / WHATS_NEW open dialogs — handled by HelpFeature against HelpViewModel.
            HelpAction.LEGEND, HelpAction.WHATS_NEW -> Unit
        }
    }

    /**
     * Called (from ArrivalsSheetHost's responses collector) when the panel has new arrival info. The
     * focus decision + map dispatch live in [HomeViewModel.onArrivalsLoaded] (it owns the pending-focus
     * latch and emits a map directive MapFeature bridges to the map), so the host just forwards the
     * loaded stop. The arrivals-panel onboarding spotlights (ETA / panel / star / overflow) are now driven in
     * Compose by [org.onebusaway.android.ui.tutorial.ArrivalTutorial] off the same responses collector.
     */
    private fun onArrivalsLoaded(response: ObaArrivalInfoResponse) {
        val stop = response.stop ?: return
        viewModel.onArrivalsLoaded(stop, response.routes)
    }

    private fun goToSendFeedBack() {
        // The VM picks the report target (focused stop → last location → nothing); the host just launches.
        when (val target = viewModel.reportTarget()) {
            is ReportTarget.Stop -> target.stop.let {
                ReportLauncher.start(this, it.id, it.name, it.code, it.lat, it.lon)
            }
            is ReportTarget.Location -> ReportLauncher.start(this, target.lat, target.lon)
            ReportTarget.Generic -> ReportLauncher.start(this)
        }
    }

    /**
     * Sets up the initial map focus. A restored focus (SavedStateHandle: process death / rotation)
     * already drives the arrivals sheet via HomeScreen; otherwise adopt a stop deep-linked through the
     * intent (makeIntent). The VM decides which applies and marks the focus pending so the map recenters
     * and adds the marker once arrivals load.
     */
    private fun setupMapState() = viewModel.applyInitialFocus(FocusedStop.fromIntent(intent))

    companion object {
        /**
         * Extra on the [navIntent] SETTINGS intent requesting the settings destination show the
         * "check your region" dialog on first composition. Set by the report flow's region-validate
         * dialog when the user opts to change their region.
         */
        const val EXTRA_SHOW_CHECK_REGION_DIALOG = ".checkRegionDialog"

        /**
         * An intent that opens HomeActivity and navigates its NavHost to [route]. The generic in-app
         * entry point: a launcher facade builds this explicit intent carrying the route via
         * [NavRoutes.EXTRA_NAV_ROUTE]; [IntentRouteMapper] navigates there. Lets former screen Activities
         * be thin facades with no per-screen intent contract. (External contracts — shortcuts/FCM — use
         * [IntentRouteMapper]'s data-URI branches.)
         */
        @JvmStatic
        fun navIntent(context: Context, route: String): Intent =
            Intent(context, HomeActivity::class.java).putExtra(NavRoutes.EXTRA_NAV_ROUTE, route)
    }
}

/**
 * The [FocusedStop] a launch intent deep-links into (makeIntent's STOP_ID + CENTER_LAT/LON), or null
 * when it carries no usable stop — an id plus a real (non-zero) location. A plain launch carries
 * neither, so focus stays null. Parsing lives here with the intent contract, off HomeModels.
 */
private fun FocusedStop.Companion.fromIntent(intent: Intent): FocusedStop? {
    val extras = intent.extras ?: return null
    val id = extras.getString(MapParams.STOP_ID) ?: return null
    val lat = extras.getDouble(MapParams.CENTER_LAT)
    val lon = extras.getDouble(MapParams.CENTER_LON)
    if (lat == 0.0 || lon == 0.0) return null
    return FocusedStop(id, extras.getString(MapParams.STOP_NAME), extras.getString(MapParams.STOP_CODE), lat, lon)
}
