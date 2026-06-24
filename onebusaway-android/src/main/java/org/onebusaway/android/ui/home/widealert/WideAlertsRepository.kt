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
package org.onebusaway.android.ui.home.widealert

import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.onebusaway.android.app.Application

/** A region-wide GTFS alert to surface to the user (drives the home wide-alert dialog). */
data class WideAlert(val title: String, val message: String, val url: String?)

/** Streams region-wide GTFS alerts for the current region. */
interface WideAlertsRepository {

    fun wideAlerts(regionId: String): Flow<WideAlert>
}

/**
 * Default implementation bridging the callback-based [org.onebusaway.android.widealerts.GtfsAlerts]
 * fetcher into a [Flow] (replaces HomeActivity's inline callback + `Handler(Looper)` hop). The
 * fetcher spawns its own thread and invokes the callback at most once — possibly never, when there
 * are no alerts — so a Flow is the correct shape: it simply emits zero or more alerts. No
 * [kotlinx.coroutines.Dispatchers.IO] is needed because the fetcher already threads itself; the
 * collector's context decides where emissions are observed.
 */
class DefaultWideAlertsRepository @Inject constructor() : WideAlertsRepository {

    override fun wideAlerts(regionId: String): Flow<WideAlert> = callbackFlow {
        Application.getGtfsAlerts().fetchAlerts(regionId) { title, message, url ->
            trySend(WideAlert(title, message, url))
        }
        // The fetcher exposes no cancellation handle; its background thread is short-lived.
        awaitClose { }
    }
}
