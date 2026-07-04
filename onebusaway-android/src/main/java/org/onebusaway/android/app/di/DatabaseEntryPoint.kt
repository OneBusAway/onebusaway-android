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
import org.onebusaway.android.database.AppDatabase
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.LegacyDataImporter
import org.onebusaway.android.database.oba.RouteDao
import org.onebusaway.android.database.oba.RouteHeadsignFavoriteDao
import org.onebusaway.android.database.oba.RouteRecorder
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.TripDao
import org.onebusaway.android.database.widealerts.AlertsRepository
import org.onebusaway.android.reminders.ReminderRepository

/**
 * A Hilt [EntryPoint] that lets code which can't be constructor-injected reach the unified Room DAOs —
 * specifically the My-tab list repositories, which are hand-built from a [Context] at a Compose call
 * site (`MyListScreens`). Prefer constructor injection elsewhere; this mirrors the existing
 * [NetworkEntryPoint]/[RegionEntryPoint] seam for the same non-injectable spot.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {

    fun appDatabase(): AppDatabase

    fun stopDao(): StopDao

    fun routeDao(): RouteDao

    fun tripDao(): TripDao

    fun routeHeadsignFavoriteDao(): RouteHeadsignFavoriteDao

    fun importGate(): ImportGate

    fun routeRecorder(): RouteRecorder

    fun reminderRepository(): ReminderRepository

    fun legacyDataImporter(): LegacyDataImporter

    fun alertsRepository(): AlertsRepository

    companion object {
        @JvmStatic
        fun get(context: Context): DatabaseEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, DatabaseEntryPoint::class.java
            )
    }
}
