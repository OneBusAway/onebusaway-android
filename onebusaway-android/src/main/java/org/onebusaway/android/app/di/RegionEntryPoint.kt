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
import org.onebusaway.android.region.RegionRepository

/**
 * A Hilt [EntryPoint] that lets code which can't be constructor- or field-injected — static Java
 * utilities, the ContentProvider — reach the [RegionRepository] seam (and thus the current region)
 * instead of `Application.get().getCurrentRegion()` (D5). It needs a [Context] only to resolve the
 * singleton graph; the returned repository is the same app-singleton every injected consumer shares.
 *
 * Use it only where injection genuinely isn't available — Hilt-reachable classes (Activities,
 * Fragments, Services, ViewModels, Workers) should inject [RegionRepository] directly, and a
 * pure helper should take the region (or the value it needs) as a parameter where practical.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RegionEntryPoint {

    fun regionRepository(): RegionRepository

    companion object {
        /** Resolves the [RegionRepository] from any [context] (its application is used). */
        @JvmStatic
        fun get(context: Context): RegionRepository =
            EntryPointAccessors.fromApplication(context, RegionEntryPoint::class.java)
                .regionRepository()
    }
}
