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
package org.onebusaway.android.ui.nav

import android.net.Uri
import org.onebusaway.android.BuildConfig

/**
 * The `content://` deep-link vocabulary the app uses as *Intent data* to open a stop or route — pinned
 * launcher shortcuts and external deep links. The arrivals/route-info launchers build these URIs and
 * [IntentRouteMapper] parses the incoming path segment.
 *
 * Carried over from the retired ObaProvider ContentProvider (storage-modernization): the authority must
 * stay under `BuildConfig.DATABASE_AUTHORITY` (originally "com.joulespersecond.oba") so already-pinned
 * shortcuts and previously-shared links keep resolving. This is pure URI/string vocabulary — no
 * ContentProvider or database is behind it.
 */
object DeepLinkUris {

    /** Content authority the stop/route deep-link URIs are namespaced under. */
    private val AUTHORITY: String = BuildConfig.DATABASE_AUTHORITY

    /** First path segment of a stop deep link: `content://<authority>/stops/{stopId}`. */
    const val STOPS_PATH = "stops"

    /** First path segment of a route deep link: `content://<authority>/routes/{routeId}`. */
    const val ROUTES_PATH = "routes"

    /** Base stop URI; append the stop id to target a stop. */
    val STOPS: Uri = Uri.parse("content://$AUTHORITY/$STOPS_PATH")

    /** Base route URI; append the route id to target a route. */
    val ROUTES: Uri = Uri.parse("content://$AUTHORITY/$ROUTES_PATH")
}
