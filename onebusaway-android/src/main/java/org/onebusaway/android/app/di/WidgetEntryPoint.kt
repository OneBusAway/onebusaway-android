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

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.time.ElapsedClock

/**
 * A Hilt [EntryPoint] exposing the singleton graph to [org.onebusaway.android.ui.widget.WidgetArrivalWorker],
 * which `WorkManager`'s default (reflection-based) factory constructs directly — not through Hilt — so
 * it can't be `@AndroidEntryPoint`-injected. Resolved via `EntryPointAccessors.fromApplication(...)`
 * inside `doWork()`, the same manual-entry-point idea [ArrivalsViewModelFactoryEntryPoint] uses for a
 * Compose destination that can't be field-injected either.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    fun stopArrivalsDataSource(): StopArrivalsDataSource

    fun elapsedClock(): ElapsedClock
}
