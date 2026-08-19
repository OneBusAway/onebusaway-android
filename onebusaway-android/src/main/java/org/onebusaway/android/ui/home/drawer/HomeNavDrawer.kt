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
package org.onebusaway.android.ui.home.drawer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.BetaPill
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.ScriptedTutorial
import org.onebusaway.android.ui.tutorial.tutorialAnchor

/**
 * Compose `ModalNavigationDrawer` content replacing `NavigationDrawerFragment` + the navdrawer_* XML.
 * Every row is a plain navigating launcher (the map "tabs" are now real NavHost destinations, so they
 * navigate just like the action rows): the content destinations first, then the region/feature-gated
 * actions. The drawer only appears on the map (HOME), so no row needs a selected-highlight. Dividers
 * sit literally where they render — before the Open-Source row and before the Settings group.
 */
@Composable
fun HomeNavDrawerSheet(
    showReminders: Boolean,
    planTripAvailable: Boolean,
    payFareAvailable: Boolean,
    onStarredStops: () -> Unit,
    onStarredRoutes: () -> Unit,
    onRecentStopsRoutes: () -> Unit,
    onReminders: () -> Unit,
    onPlanTrip: () -> Unit,
    onPayFare: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenSource: () -> Unit
) {
    // Match the legacy drawer width; the Material3 default (360dp) is noticeably wider.
    ModalDrawerSheet(Modifier.width(dimensionResource(R.dimen.navigation_drawer_width))) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // The two starred rows are spotlighted together by the scripted tour's "starred items end
            // up here" step (#2164), so the anchor wraps the pair rather than either one.
            Column(Modifier.tutorialAnchor(LocalTutorialState.current, ScriptedTutorial.KEY_DRAWER_STARRED)) {
                DrawerRow(R.string.navdrawer_item_starred_stops, R.drawable.stop_flag, onStarredStops)
                DrawerRow(R.string.navdrawer_item_starred_routes, R.drawable.ic_route, onStarredRoutes)
            }
            DrawerRow(R.string.my_recent_menu_title, R.drawable.history_24, onRecentStopsRoutes)
            if (showReminders) {
                DrawerRow(R.string.navdrawer_item_my_reminders, R.drawable.ic_drawer_alarm, onReminders)
            }
            if (planTripAvailable) {
                // The trip planner is still in beta, and says so with a pill beside its name rather
                // than "(beta)" glued into the string every locale has to repeat (#2253).
                DrawerRow(
                    R.string.navdrawer_item_plan_trip,
                    R.drawable.ic_maps_directions,
                    onPlanTrip,
                    beta = true
                )
            }
            if (payFareAvailable) {
                DrawerRow(R.string.navdrawer_item_pay_fare, R.drawable.credit_card, onPayFare)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DrawerRow(R.string.navdrawer_item_open_source, R.drawable.github_invertocat_black, onOpenSource)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DrawerRow(R.string.navdrawer_item_settings, icon = null, onSettings)
            DrawerRow(R.string.navdrawer_item_help, icon = null, onHelp)
            DrawerRow(R.string.navdrawer_item_send_feedback, icon = null, onSendFeedback)
        }
    }
}

/** The gap between a row's name and its [BetaPill] — a word's worth, so the pill reads as a tag on the
 *  name rather than as part of it. */
private val BETA_PILL_GAP = 8.dp

@Composable
private fun DrawerRow(
    @StringRes title: Int,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    beta: Boolean = false
) {
    NavigationDrawerItem(
        label = {
            if (beta) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BETA_PILL_GAP),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(title))
                    BetaPill()
                }
            } else {
                Text(stringResource(title))
            }
        },
        selected = false,
        icon = icon?.let { res ->
            // Pin to the standard 24dp; some drawer drawables are hi-res PNGs whose intrinsic size
            // would otherwise render oversized.
            { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(24.dp)) }
        },
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
