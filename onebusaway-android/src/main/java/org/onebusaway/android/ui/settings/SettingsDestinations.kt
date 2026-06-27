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
package org.onebusaway.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.ui.about.AboutScreen
import org.onebusaway.android.ui.about.buildVersionText
import org.onebusaway.android.ui.agencies.AgenciesRoute
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavHelp
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.regions.RegionsRoute

/**
 * The settings / info navigation cluster: the agencies list, the manual region picker, the about
 * screen, and the pure-Compose settings + advanced-settings screens. Host-bound actions (theme
 * recreate, go-home, refresh-regions, OTP-URL change) recover the host via [findActivity].
 */
fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    // Agencies destination: the transit agencies in the current region.
    // Reached in-app from the help menu (HelpAction.AGENCIES navigates here via a
    // navIntent route). State lives in the Hilt AgenciesViewModel. Non-exported.
    composable(NavRoutes.AGENCIES) {
        ObaTheme {
            AgenciesRoute(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
            )
        }
    }
    // Regions destination: the manual OBA region (server) picker. Reached
    // in-app from Settings (navController.navigate(NavRoutes.REGIONS)). Selecting
    // a region is terminal; on selection (which may disable auto-select, surfaced via the
    // toast) we pop back, matching the legacy "set region, return home" behavior.
    composable(NavRoutes.REGIONS) {
        val activity = LocalContext.current.findActivity()
        ObaTheme {
            RegionsRoute(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onRegionSelected = { autoSelectDisabled ->
                    if (autoSelectDisabled) {
                        Toast.makeText(
                            activity,
                            R.string.region_disabled_auto_selection,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    navController.popBackStack()
                },
            )
        }
    }
    // About destination: version / license / contributor info. Reached in-app
    // from Settings (navController.navigate(NavRoutes.ABOUT)). No VM; the version
    // line is computed from the package info via buildVersionText. Non-exported.
    composable(NavRoutes.ABOUT) {
        ObaTheme {
            AboutScreen(
                versionText = buildVersionText(LocalContext.current),
                onBack = { navController.popBackStack() },
            )
        }
    }
    // Settings destination (former SettingsActivity): a pure-Compose settings
    // screen ([SettingsRoute]). Reached in-app from the home drawer's Settings item and from
    // the report flow (region-validate dialog, with the EXTRA_SHOW_CHECK_REGION_DIALOG extra on the
    // HomeActivity intent). Host-bound actions (theme recreate, go-home, donate/browser) are
    // passed as lambdas; the Advanced sub-screen is its own destination below.
    composable(NavRoutes.SETTINGS) {
        val activity = LocalContext.current.findActivity()
        ObaTheme {
            SettingsRoute(
                onNavigateToRegions = { navController.navigate(NavRoutes.REGIONS) },
                onNavigateToAbout = { navController.navigate(NavRoutes.ABOUT) },
                onNavigateToAdvanced = {
                    navController.navigate(NavRoutes.SETTINGS_ADVANCED)
                },
                onBack = { navController.popBackStack() },
                onRecreate = { activity.recreate() },
                onGoHomeResetTutorial = { NavHelp.goHome(activity, true) },
                onOpenDonate = {
                    activity.startActivity(
                        Application.getDonationsManager().buildOpenDonationsPageIntent()
                    )
                },
                onOpenPoweredByOba = {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(activity.getString(R.string.powered_by_oba_url))
                        )
                    )
                },
            )
        }
    }
    composable(NavRoutes.SETTINGS_ADVANCED) {
        val activity = LocalContext.current.findActivity()
        ObaTheme {
            AdvancedSettingsRoute(
                onBack = { navController.popBackStack() },
                onGoHome = { NavHelp.goHome(activity, false) },
            )
        }
    }
}
