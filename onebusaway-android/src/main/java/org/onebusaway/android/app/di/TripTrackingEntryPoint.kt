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
import org.onebusaway.android.tracking.TrackedRouteStore
import org.onebusaway.android.tracking.TripTrackingController

/**
 * A Hilt [EntryPoint] for [TripTrackingController], reached from the arrival action handlers — which
 * are built around an Activity rather than constructor-injected (mirrors [FirebaseMessagingEntryPoint],
 * used from the same handlers for the reminder gate).
 *
 * Use it only where injection genuinely isn't available; Hilt-reachable classes (ViewModels,
 * repositories, Services) should inject [TripTrackingController] directly.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TripTrackingEntryPoint {

    fun tripTrackingController(): TripTrackingController

    fun trackedRouteStore(): TrackedRouteStore

    companion object {
        /** Resolves the [TripTrackingController] singleton from any [context] (its application is used). */
        fun get(context: Context): TripTrackingController = accessor(context).tripTrackingController()

        /** Resolves the [TrackedRouteStore] singleton — which rows are tracked, for a surface that
         *  reads that state without acting on it and cannot be constructor-injected. */
        fun store(context: Context): TrackedRouteStore = accessor(context).trackedRouteStore()

        private fun accessor(context: Context) = EntryPointAccessors.fromApplication(context, TripTrackingEntryPoint::class.java)
    }
}
