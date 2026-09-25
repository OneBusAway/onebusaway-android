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
import org.onebusaway.android.demo.DemoModeController

/**
 * A Hilt [EntryPoint] reaching the app-singleton [DemoModeController] from a composable, which can't
 * be constructor-injected — the scripted tutorial's host turns demo mode on and off from `HomeScreen`
 * (#2164). Mirrors [PreferencesEntryPoint]; Hilt-reachable classes inject the controller directly.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DemoEntryPoint {

    fun demoModeController(): DemoModeController

    companion object {
        /** Resolves the [DemoModeController] from any [context] (its application is used). */
        fun get(context: Context): DemoModeController = EntryPointAccessors.fromApplication(context, DemoEntryPoint::class.java)
            .demoModeController()
    }
}
