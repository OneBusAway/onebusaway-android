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

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.request.weather.ObaWeatherRequest

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
 * Default implementation wrapping the blocking weather REST call (replaces WeatherRequestTask) and
 * mapping the io/elements response to the decoupled [WeatherData] at the IO boundary, so consumers
 * never touch the Jackson model. The legacy AsyncTask treated a null response or any thrown
 * exception as a failure and only surfaced a response whose current forecast was present, so the
 * same is mapped to [Result.failure] here.
 */
class DefaultWeatherRepository @Inject constructor() : WeatherRepository {

    override suspend fun currentForecast(regionId: Long): Result<WeatherData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val forecast = ObaWeatherRequest.newRequest(regionId).call()?.current_forecast
                    ?: throw IOException("No weather forecast for region $regionId")
                WeatherData(forecast.icon ?: "", forecast.temperature, forecast.summary)
            }
        }
}
