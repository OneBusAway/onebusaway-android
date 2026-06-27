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
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.io.request.ObaArrivalInfoResponse
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.map.MapViewModel
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
import org.onebusaway.android.ui.home.HomeActivityActions
import org.onebusaway.android.ui.home.ReportTarget
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.HomeScreen
import org.onebusaway.android.ui.home.HomeViewModel
import org.onebusaway.android.ui.home.HomeNavHost
import org.onebusaway.android.ui.home.HomeDestinationDeps
import org.onebusaway.android.ui.home.LaunchIntentEffect
import org.onebusaway.android.ui.home.SettingsRehomeEffect
import org.onebusaway.android.ui.home.PaymentWarningDialog
import org.onebusaway.android.ui.home.RegionPickerHost
import org.onebusaway.android.ui.nav.IntentRouteMapper
import org.onebusaway.android.ui.nav.NavRoutes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.onebusaway.android.ui.survey.SurveyViewModel
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.ReminderUtils
import org.onebusaway.android.ui.tutorial.TutorialPrefs

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    // Builds the per-stop ArrivalsViewModel for the home bottom-sheet host. Assisted because the sheet's
    // stop id is runtime-dynamic.
    @Inject
    lateinit var arrivalsViewModelFactory: ArrivalsViewModel.Factory

    // The add-region deep link applies custom API URLs through this (rather than reaching Application.get()).
    @Inject
    lateinit var regionRepository: RegionRepository

    // The launch-intent channel: the OS delivers external entry points (deep links, FCM, launcher
    // shortcuts) to this Activity before/around the composition, so they're surfaced here for the NavHost's
    // [LaunchIntentEffect] to translate (via IntentRouteMapper) and open once composed. Seeded on a fresh
    // launch only (onCreate), fed warm relaunches via onNewIntent — the in-app navigation no longer flows
    // through here (it uses the NavController directly). An UNLIMITED queue (not a latch) so a fresh-launch
    // intent staged before the NavHost composes isn't lost, and so rapid, distinct back-to-back intents are
    // each delivered exactly once rather than overwriting one another before they're consumed (#1582).
    private val launchIntents = Channel<Intent>(Channel.UNLIMITED)

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

        val activityActions = buildActivityActions()

        // Surface the launch intent for the NavHost's [LaunchIntentEffect] (it runs the side effects then
        // opens the translated route once composed). Fresh launch only, so a rotation doesn't re-fire
        // reminder deletes / URL applies.
        if (savedInstanceState == null) {
            launchIntents.trySend(intent)
        }

        setContent {
            val navController = rememberNavController()
            AccessibilityAnalyticsEffect()
            HomeAnalyticsEffect(viewModel.analyticsEvents)
            LaunchIntentEffect(navController, launchIntents.receiveAsFlow(), ::applyLaunchIntentSideEffects)
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
                    activityActions = activityActions,
                ),
            )
            PaymentWarningDialog(viewModel.paymentWarning, viewModel::dismissPaymentWarning)
            // The forced region picker, driven reactively off the repository (RegionPickerViewModel). At the
            // setContent root so its window overlays whatever screen triggered the re-resolve.
            RegionPickerHost()
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
     * Bundles the home screen's *Activity-bound* tap/UI lambdas ([HomeActivityActions]) — the ones that
     * need `ExternalIntents`/`ReportLauncher`/`startActivity` or forward to an Activity-owned ViewModel.
     * The navigation lambdas (which need the NavController) are built in the HOME composable and combined
     * with these into the full [HomeCallbacks]. `onHelpActionExternal` handles every [HelpAction] branch
     * except `AGENCIES`, which is a navigation supplied by the composable.
     */
    private fun buildActivityActions(): HomeActivityActions = HomeActivityActions(
        onPayFare = ::onPayFareSelected,
        onHelp = ::onHelpSelected,
        onSendFeedback = ::onSendFeedbackSelected,
        onOpenSource = ::onOpenSourceSelected,
        onHelpActionExternal = ::onHelpAction,
        // Stage the welcome sequence on the VM latch; HomeScreen starts it when the latch fires.
        onShowWelcomeTutorial = viewModel::requestWelcomeTutorial,
        onSheetSettled = viewModel::onSheetSettled,
        onClearFocus = viewModel::requestClearMapFocus,
        onArrivalsLoaded = ::onArrivalsLoaded,
        onShowRouteOnMap = viewModel::requestShowRouteOnMap,
        onToggleSheet = viewModel::requestToggleSheet,
        onCancelRouteMode = ::onCancelRouteMode,
    )

    /**
     * A warm re-launch (singleTop) carrying an external screen intent — FCM CLEAR_TOP, the
     * NavigationService reminder PendingIntent, a pinned shortcut. Surface it for [LaunchIntentEffect]
     * (cold launches are seeded in onCreate).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntents.trySend(intent)
    }

    /**
     * The domain mutations a launch intent implies, run by [LaunchIntentEffect] before it opens the
     * translated route: the `add-region`/FCM side effects (see [applyIntentSideEffects]) and the
     * "show tutorials again" welcome re-request. Kept off [IntentRouteMapper] so it stays a pure translator.
     */
    private fun applyLaunchIntentSideEffects(intent: Intent) {
        applyIntentSideEffects(intent)
        if (intent.extras?.getBoolean(TutorialPrefs.TUTORIAL_WELCOME) == true) {
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


    // --- Nav-drawer one-shot actions (each navigates/launches + reports its own analytics) -----------
    // These were the launcher branches of the old goToNavDrawerItem when-switch; as plain per-row
    // callbacks they no longer route through the tab-selection path. The survey / donation / weather /
    // layers overlays gate themselves off the VM state, so there's no imperative show/hide here.

    /** PAY_FARE shows a fare-payment warning (or launches the payment app) and reports no analytics. */
    private fun onPayFareSelected() {
        ExternalIntents.payFareOrWarningRegion(this)?.let { viewModel.showPaymentWarning(it) }
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

    // --- Help-menu actions that are Activity operations (the dialog-opening ones live in HelpFeature) ---

    private fun onHelpAction(action: HelpAction) {
        when (action) {
            HelpAction.TUTORIALS -> {
                TutorialPrefs.resetAllTutorials(this)
                NavHelp.goHome(this, true)
            }
            // AGENCIES is a navigation — handled by the composable-supplied wrapper, not here.
            HelpAction.AGENCIES -> Unit
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
