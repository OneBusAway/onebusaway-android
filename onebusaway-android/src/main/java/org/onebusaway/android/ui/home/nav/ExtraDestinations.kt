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
package org.onebusaway.android.ui.home.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.analytics.FirebaseAnalytics
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.ObaAnalytics
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.nav.revealRouteOnMap
import org.onebusaway.android.ui.nav.revealStopOnMap
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.home.donation.DonationLearnMoreScreen
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nightlight.NightLightRoute
import org.onebusaway.android.ui.searchresults.SearchResultsRoute
import org.onebusaway.android.ui.searchresults.SearchResultsViewModel
import org.onebusaway.android.ui.survey.SurveyWebViewScreen
import org.onebusaway.android.util.DBUtil

/**
 * The standalone one-off destinations that don't belong to a larger feature graph: the donation
 * "learn more" explainer, the external-survey WebView, the night-light screen, and the search-results
 * screen. Each recovers the host via [findActivity] where it needs Activity/Context behavior.
 */
fun NavGraphBuilder.extraDestinations(navController: NavHostController) {
    // Donation "learn more" destination: the why-donate explainer. Reached
    // in-app from the home donation card (a navIntent route → translator). The donate
    // button reproduces the former Activity's behavior: dismiss any
    // pending donation requests, open the donations page, then pop back. Non-exported.
    composable(NavRoutes.DONATION_LEARN_MORE) {
        val context = LocalContext.current
        ObaTheme {
            DonationLearnMoreScreen(
                onBack = { navController.popBackStack() },
                onDonate = {
                    Application.getDonationsManager().dismissDonationRequests()
                    context.startActivity(
                        Application.getDonationsManager().buildOpenDonationsPageIntent()
                    )
                    navController.popBackStack()
                },
            )
        }
    }
    // Survey web view destination: the external-survey WebView. Reached in-app when the home survey
    // overlay's onOpenSurvey fires (HomeNavHost: navController.navigateFromHome(NavRoutes.surveyWebView(url)))
    // — a direct in-process navigation, no facade/translator. The survey URL is the nav-arg.
    // Non-exported; no alias.
    composable(
        NavRoutes.SURVEY_WEB_VIEW,
        arguments = listOf(
            navArgument(NavRoutes.ARG_URL) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val url = backStackEntry.arguments?.getString(NavRoutes.ARG_URL).orEmpty()
        ObaTheme {
            SurveyWebViewScreen(
                url = url,
                onBack = { navController.popBackStack() },
            )
        }
    }
    // Night light: the flashing screen riders show to flag drivers. Reached
    // from the arrivals overflow and old pinned launcher shortcuts (frozen NightLightActivity
    // name → alias → HomeActivity, routed by component name). Window/brightness/orientation
    // concerns live in NightLightRoute for as long as it's on the back stack.
    composable(NavRoutes.NIGHT_LIGHT) {
        ObaTheme {
            NightLightRoute(onBack = { navController.popBackStack() })
        }
    }
    // Search results (system ACTION_SEARCH + the home top-bar search field). The query is a
    // nav-arg; result taps route to the in-NavHost destinations (route info / arrivals) or
    // the map. Re-search when the query arg changes (a fresh search reuses this entry).
    composable(
        NavRoutes.SEARCH,
        arguments = listOf(
            navArgument(NavRoutes.ARG_QUERY) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStackEntry ->
        val activity = LocalContext.current.findActivity()
        // The Firebase analytics process-singleton, fetched from the Context like everywhere else
        // (MapFeature/TripPlanScreen) rather than reaching into the host for it.
        val firebaseAnalytics = remember { FirebaseAnalytics.getInstance(activity) }
        val query = backStackEntry.arguments?.getString(NavRoutes.ARG_QUERY).orEmpty()
        val searchVm: SearchResultsViewModel = hiltViewModel()
        LaunchedEffect(query) {
            ObaAnalytics.reportSearchEvent(
                Application.get().plausibleInstance, firebaseAnalytics, query
            )
            searchVm.search(query)
        }
        ObaTheme {
            SearchResultsRoute(
                viewModel = searchVm,
                onBack = { navController.popBackStack() },
                onRouteListStops = { route ->
                    DBUtil.addRouteToDB(
                        activity, route.id, route.shortName, route.longName, route.url
                    )
                    navController.navigate(NavRoutes.routeInfo(route.id))
                },
                onRouteShowOnMap = { route ->
                    DBUtil.addRouteToDB(
                        activity, route.id, route.shortName, route.longName, route.url
                    )
                    navController.revealRouteOnMap(route.id)
                },
                onStopArrivals = { stop ->
                    navController.navigate(NavRoutes.arrivals(stop.id))
                },
                onStopShowOnMap = { stop ->
                    navController.revealStopOnMap(stop.id, stop.latitude, stop.longitude)
                },
            )
        }
    }
}
