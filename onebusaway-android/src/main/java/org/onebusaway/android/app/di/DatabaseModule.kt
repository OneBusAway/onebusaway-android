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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.onebusaway.android.database.AppDatabase
import org.onebusaway.android.database.DatabaseProvider
import org.onebusaway.android.database.oba.LegacyDataImporter
import org.onebusaway.android.database.oba.LegacyImportDao
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.RegionDao
import org.onebusaway.android.database.oba.RouteDao
import org.onebusaway.android.database.oba.RouteHeadsignFavoriteDao
import org.onebusaway.android.database.oba.ServiceAlertDao
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopRouteFilterDao
import org.onebusaway.android.database.oba.TripDao
import org.onebusaway.android.database.survey.dao.StudiesDao
import org.onebusaway.android.database.survey.dao.SurveysDao
import org.onebusaway.android.database.widealerts.dao.AlertDao
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * Provides the unified Room [AppDatabase] and its DAOs to Hilt-managed code (storage-modernization).
 * The instance is sourced from [DatabaseProvider] so it's the same singleton reached by the few
 * bare-[Context] callers (e.g. Backup, and the GTFS alert fetcher via [DatabaseEntryPoint]).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        DatabaseProvider.getDatabase(context)

    @Provides
    fun provideLegacyImportDao(db: AppDatabase): LegacyImportDao = db.legacyImportDao()

    @Provides
    fun provideServiceAlertDao(db: AppDatabase): ServiceAlertDao = db.serviceAlertDao()

    @Provides
    fun provideStopRouteFilterDao(db: AppDatabase): StopRouteFilterDao = db.stopRouteFilterDao()

    @Provides
    fun provideStopDao(db: AppDatabase): StopDao = db.stopDao()

    @Provides
    fun provideRouteDao(db: AppDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()

    @Provides
    fun provideRouteHeadsignFavoriteDao(db: AppDatabase): RouteHeadsignFavoriteDao =
        db.routeHeadsignFavoriteDao()

    @Provides
    fun provideRegionDao(db: AppDatabase): RegionDao = db.regionDao()

    @Provides
    fun provideNavStopDao(db: AppDatabase): NavStopDao = db.navStopDao()

    @Provides
    fun provideStudiesDao(db: AppDatabase): StudiesDao = db.studiesDao()

    @Provides
    fun provideSurveysDao(db: AppDatabase): SurveysDao = db.surveysDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertsDao()

    @Provides
    @Singleton
    fun provideLegacyDataImporter(
        @ApplicationContext context: Context,
        db: AppDatabase,
        prefs: PreferencesRepository,
    ): LegacyDataImporter = LegacyDataImporter(context, db, prefs)
}
