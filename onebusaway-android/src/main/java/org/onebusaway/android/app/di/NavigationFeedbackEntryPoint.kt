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
import org.onebusaway.android.nav.NavigationFeedbackRepository

/**
 * A Hilt [EntryPoint] that lets the post-trip feedback screen reach [NavigationFeedbackRepository].
 * That screen is a composable nav destination with no injection site of its own, and it needs the
 * repository to retire the notification that offered the prompt (same pattern as
 * [AnalyticsEntryPoint]).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface NavigationFeedbackEntryPoint {

    fun navigationFeedbackRepository(): NavigationFeedbackRepository

    companion object {
        /** Resolves the feedback repository from any [context] (its application is used). */
        fun get(context: Context): NavigationFeedbackRepository = EntryPointAccessors.fromApplication(
            context,
            NavigationFeedbackEntryPoint::class.java
        ).navigationFeedbackRepository()
    }
}
