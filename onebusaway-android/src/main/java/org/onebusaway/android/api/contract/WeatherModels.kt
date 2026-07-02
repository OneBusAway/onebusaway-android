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
package org.onebusaway.android.api.contract

import kotlinx.serialization.Serializable

/**
 * Weather response models for [WeatherWebService]. Like surveys, weather is served from the region's
 * sidecar host as bare (non-[ObaEnvelope]) JSON, so [WeatherResponse] decodes directly. Property
 * names mirror the wire's snake_case so they map without `@SerialName`. The current and hourly
 * forecasts share the same shape, so both reuse [WeatherForecast].
 */
@Serializable
data class WeatherResponse(
    val current_forecast: WeatherForecast? = null,
    val hourly_forecast: List<WeatherForecast> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val region_identifier: Int = 0,
    val region_name: String? = null,
    val retrieved_at: String? = null,
    val today_summary: String? = null,
    val units: String? = null,
)

@Serializable
data class WeatherForecast(
    val icon: String? = null,
    val precip_per_hour: Double = 0.0,
    val precip_probability: Double = 0.0,
    val summary: String? = null,
    val temperature: Double = 0.0,
    val temperature_feels_like: Double = 0.0,
    val time: Int = 0,
    val wind_speed: Double = 0.0,
)
