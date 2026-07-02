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

import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.api.data.LocationSearchDataSource
import org.onebusaway.android.api.data.ProblemReportDataSource

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.onebusaway.android.api.contract.RegionsWebService
import org.onebusaway.android.api.contract.ReminderWebService

/**
 * A Hilt [EntryPoint] that lets code which can't be constructor- or field-injected reach the shared
 * io/client services and data sources. It needs a [Context] only to resolve the singleton graph; the
 * returned instance is the same app-singleton every injected consumer shares.
 *
 * Seam rule for reaching the modernized REST client, in order of preference:
 * 1. **An io/client data source** (e.g. `RouteDataSource`) when a domain model is shared across
 *    features or the consumer is another repository — depend on that, not on the web service.
 * 2. **Constructor injection** directly into Hilt-reachable consumers (most feature repositories,
 *    `@HiltViewModel`s, services).
 * 3. **This EntryPoint** only where injection genuinely isn't available — e.g. a repository
 *    hand-built from a [Context] at a Compose call site (the `MyListScreens` search repos). Resolve
 *    it at the construction boundary and pass the dependency into the constructor; don't bury the
 *    lookup inside the repository's business logic (keeps the dependency declared and testable).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkEntryPoint {

    fun regionsWebService(): RegionsWebService

    fun reminderWebService(): ReminderWebService

    fun locationSearchDataSource(): LocationSearchDataSource

    fun stopArrivalsDataSource(): StopArrivalsDataSource

    fun problemReportDataSource(): ProblemReportDataSource

    companion object {
        /** Resolves the shared [RegionsWebService] from any [context] (its application is used). */
        @JvmStatic
        fun getRegions(context: Context): RegionsWebService =
            EntryPointAccessors.fromApplication(context, NetworkEntryPoint::class.java)
                .regionsWebService()

        /** Resolves the shared [ReminderWebService] from any [context] (its application is used). */
        @JvmStatic
        fun getReminder(context: Context): ReminderWebService =
            EntryPointAccessors.fromApplication(context, NetworkEntryPoint::class.java)
                .reminderWebService()

        /** Resolves the shared [LocationSearchDataSource] from any [context] (its application is used). */
        @JvmStatic
        fun getLocationSearch(context: Context): LocationSearchDataSource =
            EntryPointAccessors.fromApplication(context, NetworkEntryPoint::class.java)
                .locationSearchDataSource()

        /** Resolves the shared [StopArrivalsDataSource] from any [context] (its application is used). */
        @JvmStatic
        fun getStopArrivals(context: Context): StopArrivalsDataSource =
            EntryPointAccessors.fromApplication(context, NetworkEntryPoint::class.java)
                .stopArrivalsDataSource()

        /** Resolves the shared [ProblemReportDataSource] from any [context] (its application is used). */
        @JvmStatic
        fun getProblemReport(context: Context): ProblemReportDataSource =
            EntryPointAccessors.fromApplication(context, NetworkEntryPoint::class.java)
                .problemReportDataSource()
    }
}
