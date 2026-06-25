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
package org.onebusaway.android.ui.home

import javax.inject.Inject
import org.onebusaway.android.util.PreferenceUtils

/**
 * The first-launch-ever flag the home startup flow gates on (was HomeActivity's `mInitialStartup` +
 * the `"initialStartup"` preference). Wrapping it here keeps [HomeViewModel] free of Android statics
 * while it owns the startup region-check decision. Reads are a cheap (memory-cached) boolean
 * preference, so no `Dispatchers.IO` hop is needed.
 */
interface StartupPreferencesRepository {

    /** True until [clearInitialStartup] is called once — i.e. only on the very first launch ever. */
    fun isInitialStartup(): Boolean

    /** Marks the first launch complete (persisted), so subsequent launches check the region eagerly. */
    fun clearInitialStartup()
}

class DefaultStartupPreferencesRepository @Inject constructor() : StartupPreferencesRepository {

    override fun isInitialStartup(): Boolean = PreferenceUtils.getBoolean(KEY_INITIAL_STARTUP, true)

    override fun clearInitialStartup() = PreferenceUtils.saveBoolean(KEY_INITIAL_STARTUP, false)

    private companion object {
        // The same slot HomeActivity used, so installed users keep their completed-first-launch flag.
        const val KEY_INITIAL_STARTUP = "initialStartup"
    }
}
