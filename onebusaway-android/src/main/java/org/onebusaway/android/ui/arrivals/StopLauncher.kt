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
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.nav.DeepLinkUris

/**
 * Opens the mapless arrivals board for a stop. The stops data URI is shared with legacy pinned
 * shortcuts, whose frozen activity aliases still target HomeActivity. IntentRouteMapper translates
 * both old and new shortcuts into the same Compose destination.
 */
object StopLauncher {

    class Builder(private val context: Context, stopId: String) {

        /** The built intent; Java callers see this as getIntent(). */
        val intent: Intent = Intent(context, HomeActivity::class.java).apply {
            data = DeepLinkUris.STOPS.buildUpon().appendPath(stopId).build()
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
