/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), Microsoft Corporation
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
package org.onebusaway.android.ui.arrivals

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.nav.DeepLinkUris

/**
 * Opens the app on a stop: the map focused on it, with its real-time arrivals in the drawer.
 *
 * Not an Activity but a launcher facade that builds an explicit [HomeActivity] intent carrying the
 * stop's `content://…/stops/{id}` data URI (+ an optional name extra); `IntentRouteMapper` reads the
 * data URI back as a stop reveal and the host applies it to the map. The frozen class name
 * `org.onebusaway.android.ui.arrivals.ArrivalsListActivity` keeps resolving (for old pinned launcher
 * shortcuts) via an `<activity-alias>` → HomeActivity in the manifest, so the shortcut contract is
 * unchanged even though the standalone arrivals screen it once named is gone (#1898).
 */
object StopLauncher {

    class Builder(private val context: Context, stopId: String) {

        /** The built intent; Java callers see this as getIntent(). */
        val intent: Intent = Intent(context, HomeActivity::class.java).apply {
            data = Uri.withAppendedPath(DeepLinkUris.STOPS, stopId)
        }

        fun setStopName(stopName: String?): Builder {
            intent.putExtra(ArrivalsIntents.STOP_NAME, stopName)
            return this
        }

        fun start() {
            context.startActivity(intent)
        }
    }

    fun start(context: Context, stopId: String) {
        Builder(context, stopId).start()
    }
}
