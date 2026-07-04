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
package org.onebusaway.android.app.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.onebusaway.android.ui.tripplan.TripPlanRepository

/**
 * A Hilt [EntryPoint] that lets code which can't be constructor- or field-injected — the Java
 * `RealtimeService` IntentService — reach the [TripPlanRepository] seam. It replaces the legacy
 * `TripRequest` AsyncTask that the service used to construct directly.
 *
 * Use it only where injection genuinely isn't available; Hilt-reachable classes (Activities,
 * Fragments, ViewModels) should inject [TripPlanRepository] directly.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TripPlanRepositoryEntryPoint {

    fun tripPlanRepository(): TripPlanRepository

    companion object {
        /** Resolves the [TripPlanRepository] from any [context] (its application is used). */
        @JvmStatic
        fun get(context: Context): TripPlanRepository =
            EntryPointAccessors.fromApplication(context, TripPlanRepositoryEntryPoint::class.java)
                .tripPlanRepository()
    }
}
