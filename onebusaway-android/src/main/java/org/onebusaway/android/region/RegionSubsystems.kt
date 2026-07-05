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
package org.onebusaway.android.region

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.RegionEntryPoint

/**
 * Wires the region-*derived* Open311 reporting endpoints to the [RegionRepository] region flow. Instead
 * of the old region write transaction imperatively rebuilding them, they re-initialize whenever the
 * observed region changes — the §1.2 "everything that cares observes it" form. The `StateFlow` replays
 * its seeded value immediately (`Main.immediate`), so this also performs the initial init, subsuming the
 * former onCreate `initOpen311(currentRegion)` call.
 *
 * The other region-derived subsystem — the Plausible/Umami analytics emitters — observes the same region
 * flow independently, from [org.onebusaway.android.analytics.AnalyticsProvider].
 *
 * Started once from `Application.onCreate` (after `initObaRegion`, so the repo seeds from the region
 * already loaded). The collector runs for the process lifetime — an app-global subscription.
 */
object RegionSubsystems {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    fun observe(app: Application) {
        scope.launch {
            RegionEntryPoint.get(app).region.collect { region ->
                Open311Subsystem.applyRegion(region)
            }
        }
    }
}
