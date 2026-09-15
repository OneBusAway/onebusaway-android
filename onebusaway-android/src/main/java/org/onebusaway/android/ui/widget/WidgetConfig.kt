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
package org.onebusaway.android.ui.widget

import kotlinx.serialization.Serializable

/**
 * The user's configuration for a single Stop Times widget instance. Persisted (as JSON, via
 * [WidgetPrefs]) keyed by the widget's `appWidgetId`.
 *
 * @param routeShortNames route id -> short name (e.g. "1_100" -> "44"), for the up-to-3 routes the
 * widget shows.
 */
@Serializable
data class WidgetConfig(
    val stopId: String,
    val stopName: String,
    val widgetName: String,
    val routeShortNames: Map<String, String> = emptyMap()
)
