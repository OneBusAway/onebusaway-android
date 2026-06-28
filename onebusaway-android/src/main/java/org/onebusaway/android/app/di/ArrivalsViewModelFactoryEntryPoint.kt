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
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel

/**
 * A Hilt [EntryPoint] exposing the assisted [ArrivalsViewModel.Factory] to Compose destinations that
 * build the arrivals VM inside a `viewModelFactory {}` and therefore can't field-inject it. It lets
 * those destinations stop recovering the factory off the host via an `as HomeActivity` cast.
 *
 * [ArrivalsViewModel] is deliberately plain `@AssistedInject` (not `@HiltViewModel`) — the home sheet
 * builds it against a non-Hilt-aware per-stop ViewModelStoreOwner — so the factory can't be served by
 * `hiltViewModel(creationCallback = …)`; exposing the existing factory here changes nothing about the
 * VM. The factory's only non-assisted dependency (ArrivalsRepository) is reachable from the singleton
 * graph, and each `create(...)` still mints a fresh (unscoped) repository per VM.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArrivalsViewModelFactoryEntryPoint {

    fun arrivalsViewModelFactory(): ArrivalsViewModel.Factory

    companion object {
        /** Resolves the [ArrivalsViewModel.Factory] from any [context] (its application is used). */
        @JvmStatic
        fun get(context: Context): ArrivalsViewModel.Factory =
            EntryPointAccessors.fromApplication(context, ArrivalsViewModelFactoryEntryPoint::class.java)
                .arrivalsViewModelFactory()
    }
}
