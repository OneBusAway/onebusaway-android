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
package org.onebusaway.android.ui.tripplan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.util.PreferenceUtils

/** Loads the user's saved trip-plan advanced options (travel mode, max walk, transfers, stairs). */
interface AdvancedSettingsRepository {
    fun load(): AdvancedSettings
}

/**
 * Reads the advanced-options preferences — ported verbatim from `TripPlanActivity.loadAdvancedSettings`
 * so [TripPlanViewModel] can be constructor-injected (and JVM-tested with a fake) instead of being
 * hand-fed the settings by its host. (PreferenceUtils still reaches the prefs statically; replacing
 * that with an injected SharedPreferences is broader preferences work.)
 */
class DefaultAdvancedSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : AdvancedSettingsRepository {

    override fun load(): AdvancedSettings {
        val modeId = PreferenceUtils.getInt(
            context.getString(R.string.preference_key_trip_plan_travel_by), 0
        )
        val maxWalk = PreferenceUtils.getDouble(
            context.getString(R.string.preference_key_trip_plan_maximum_walking_distance), 0.0
        )
        val optimize = PreferenceUtils.getBoolean(
            context.getString(R.string.preference_key_trip_plan_minimize_transfers), false
        )
        val wheelchair = PreferenceUtils.getBoolean(
            context.getString(R.string.preference_key_trip_plan_avoid_stairs), false
        )
        return AdvancedSettings(
            modeId = modeId,
            maxWalkMeters = maxWalk.takeIf { it != 0.0 && it != Double.MAX_VALUE },
            optimizeTransfers = optimize,
            wheelchair = wheelchair
        )
    }
}
