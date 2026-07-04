/*
 * Copyright (C) 2010-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
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
package org.onebusaway.android.ui.routeinfo

import org.onebusaway.android.ui.HomeActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.onebusaway.android.ui.nav.DeepLinkUris

/**
 * Launches the route-info screen (a route's stops grouped by direction).
 *
 * Route info is a NavHost destination hosted by [HomeActivity]; this is no longer an
 * Activity but a launcher facade that builds an explicit [HomeActivity] intent carrying the route's
 * `content://…/routes/{id}` data URI. HomeActivity's intent translator reads it and navigates to the
 * route-info destination. The frozen class names `org.onebusaway.android.ui.RouteInfoActivity` and
 * `com.joulespersecond.seattlebusbot.RouteInfoActivity` keep resolving (old pinned route shortcuts)
 * via `<activity-alias>` → HomeActivity in the manifest.
 */
object RouteInfoLauncher {

    @JvmStatic
    fun start(context: Context, routeId: String) {
        context.startActivity(makeIntent(context, routeId))
    }

    @JvmStatic
    fun makeIntent(context: Context, routeId: String): Intent =
        Intent(context, HomeActivity::class.java).apply {
            data = Uri.withAppendedPath(DeepLinkUris.ROUTES, routeId)
        }
}
