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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

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
    onReminders: () -> Unit,
    onPlanTrip: () -> Unit,
    onPayFare: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenSource: () -> Unit,
) {
    // Match the legacy drawer width; the Material3 default (360dp) is noticeably wider.
    ModalDrawerSheet(Modifier.width(dimensionResource(R.dimen.navigation_drawer_width))) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DrawerRow(R.string.navdrawer_item_starred_stops, R.drawable.ic_stop_flag_triangle, onStarredStops)
            DrawerRow(R.string.navdrawer_item_starred_routes, R.drawable.ic_bus, onStarredRoutes)
            if (showReminders) {
                DrawerRow(R.string.navdrawer_item_my_reminders, R.drawable.ic_drawer_alarm, onReminders)
            }
            if (planTripAvailable) {
                DrawerRow(R.string.navdrawer_item_plan_trip, R.drawable.ic_maps_directions, onPlanTrip)
            }
            if (payFareAvailable) {
                DrawerRow(R.string.navdrawer_item_pay_fare, R.drawable.ic_payment, onPayFare)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DrawerRow(R.string.navdrawer_item_open_source, R.drawable.ic_drawer_github, onOpenSource)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DrawerRow(R.string.navdrawer_item_settings, icon = null, onSettings)
            DrawerRow(R.string.navdrawer_item_help, icon = null, onHelp)
            DrawerRow(R.string.navdrawer_item_send_feedback, icon = null, onSendFeedback)
        }
    }
}

@Composable
private fun DrawerRow(
    @StringRes title: Int,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(stringResource(title)) },
        selected = false,
        icon = icon?.let { res ->
            // Pin to the standard 24dp; some drawer drawables are hi-res PNGs whose intrinsic size
            // would otherwise render oversized.
            { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(24.dp)) }
        },
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
