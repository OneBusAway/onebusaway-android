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

import android.util.Log
import edu.usf.cutr.open311client.Open311Manager
import edu.usf.cutr.open311client.models.Open311Option
import org.onebusaway.android.BuildConfig

/**
 * The region-derived Open311 reporting endpoints: reconfigures the (process-global) [Open311Manager]
 * whenever the active region changes. Driven by [RegionSubsystems] (which observes the region flow);
 * this holds the endpoint-init body that used to live on `Application`. Stateless glue over the
 * `Open311Manager` singleton.
 */
object Open311Subsystem {

    private const val TAG = "Open311Subsystem"

    /** Rebuilds the Open311 endpoints for [region]: clears the current set, then re-registers each of the
     * region's configured servers. A [region] with no servers (or null) just clears them. */
    fun applyRegion(region: Region?) {
        if (BuildConfig.DEBUG) {
            Open311Manager.getSettings().setDebugMode(true)
            Open311Manager.getSettings().setDryRun(true)
            Log.w(TAG, "Open311 issue reporting is in debug/dry run mode - no issues will be submitted.")
        }

        Open311Manager.clearOpen311()

        region?.open311Servers?.forEach { server ->
            val option = Open311Option(
                server.baseUrl,
                server.apiKey,
                server.jurisdictionId?.takeIf { it.isNotEmpty() },
            )
            Open311Manager.initOpen311WithOption(option)
        }
    }
}
