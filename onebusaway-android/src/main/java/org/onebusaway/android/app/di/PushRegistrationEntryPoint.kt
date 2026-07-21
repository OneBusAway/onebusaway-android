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
import org.onebusaway.android.push.PushRegistrationManager

/**
 * A Hilt [EntryPoint] that lets code which can't be constructor-injected — `Application.onCreate`, an
 * `Activity` lifecycle callback — reach the injected [PushRegistrationManager] singleton (mirrors
 * [FirebaseMessagingEntryPoint]). It needs a [Context] only to resolve the singleton graph.
 *
 * Use it only where injection genuinely isn't available — Hilt-reachable classes should inject
 * [PushRegistrationManager] directly.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushRegistrationEntryPoint {

    fun pushRegistrationManager(): PushRegistrationManager

    companion object {
        /** Resolves the [PushRegistrationManager] singleton from any [context] (its application is used). */
        @JvmStatic
        fun get(context: Context): PushRegistrationManager = EntryPointAccessors.fromApplication(context, PushRegistrationEntryPoint::class.java)
            .pushRegistrationManager()
    }
}
