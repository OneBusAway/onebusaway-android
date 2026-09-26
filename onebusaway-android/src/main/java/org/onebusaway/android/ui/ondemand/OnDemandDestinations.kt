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
package org.onebusaway.android.ui.ondemand

import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.ExternalIntents

/** The on-demand service page ([NavRoutes.ONDEMAND_SERVICE]); reached from a zone tap or the arrivals card. */
fun NavGraphBuilder.onDemandGraph(navController: NavHostController) {
    composable(
        NavRoutes.ONDEMAND_SERVICE,
        arguments = listOf(navArgument(NavRoutes.ARG_ONDEMAND_SERVICE_ID) { type = NavType.StringType })
    ) {
        val context = LocalContext.current
        val viewModel: OnDemandServiceViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        // Back from the dialer or browser, the booking deadline may have passed; restate it.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.representNow() }
        ObaTheme {
            OnDemandServiceScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRetry = viewModel::retry,
                // ACTION_DIAL never places the call itself; the rider confirms in the dialer.
                onCall = { phone -> ExternalIntents.goToPhoneDialer(context, phone) },
                onOpenUrl = { url -> ExternalIntents.goToUrl(context, url) }
            )
        }
    }
}
