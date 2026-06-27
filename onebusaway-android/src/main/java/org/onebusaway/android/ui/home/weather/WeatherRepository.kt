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
package org.onebusaway.android.ui.home.weather

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.WeatherWebService
import org.onebusaway.android.region.RegionRepository

/**
 * The current weather forecast, decoupled from the io/elements response. The raw icon string and
 * Fahrenheit temperature are kept so the [WeatherCard] can map them to a drawable + formatted string
 * at render time, leaving the ViewModel free of resource/preference lookups and unit-testable.
 */
data class WeatherData(val icon: String, val temperatureF: Double, val summary: String?)

/** Provides the current weather forecast for a region, for the home map's weather chip. */
interface WeatherRepository {

    suspend fun currentForecast(regionId: Long): Result<WeatherData>
}

/**
 * Default implementation fetching the weather forecast from the region's sidecar host via
 * [WeatherWebService] and mapping the response to the decoupled [WeatherData] at the IO boundary, so
 * consumers never touch the wire model. A missing region/sidecar URL, a thrown exception, or a
 * response without a current forecast all map to [Result.failure] (matching the legacy AsyncTask).
 */
class DefaultWeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val weatherService: WeatherWebService,
) : WeatherRepository {

    override suspend fun currentForecast(regionId: Long): Result<WeatherData> = runCatching {
        val base = regionRepository.region.value?.sidecarBaseUrl
            ?: throw IOException("No sidecar base URL for region $regionId")
        val url = base + context.getString(R.string.weather_api_endpoint)
            .replace("regionID", regionId.toString())
        val forecast = weatherService.getWeather(url).current_forecast
            ?: throw IOException("No weather forecast for region $regionId")
        WeatherData(forecast.icon ?: "", forecast.temperature, forecast.summary)
    }
}
