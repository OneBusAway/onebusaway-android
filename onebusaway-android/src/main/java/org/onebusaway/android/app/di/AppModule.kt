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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.onebusaway.android.app.Application
import org.onebusaway.android.donations.DonationsManager
import org.onebusaway.android.util.TimeProvider

/**
 * Provides the process-wide singletons that still live on [Application] (the de-facto DI container), so
 * Hilt-managed code can inject them instead of reaching `Application.getX()` statically. During the
 * transition they're sourced from the existing [Application] instances. (RegionRepository and
 * LocationRepository are real Hilt `@Singleton`s — they're bound in
 * `RepositoryModule`, not here.) More singletons get added here as converted consumers need them.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Process-lifetime scope for fire-and-forget persistence work (DataStore + repository writes). */
    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The Preferences DataStore backing [PreferencesRepository]. A one-time SharedPreferencesMigration
    // imports the existing default prefs file ("<package>_preferences"), so existing users' values carry
    // over on first access; thereafter that file is unused.
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @AppScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, context.packageName + "_preferences")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    @Provides
    @Singleton
    fun provideDonationsManager(): DonationsManager = Application.getDonationsManager()

    /** Wall-clock source — the single `System.currentTimeMillis()` boundary for injected consumers. */
    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider { System.currentTimeMillis() }
}
