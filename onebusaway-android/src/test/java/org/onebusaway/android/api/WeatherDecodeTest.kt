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
package org.onebusaway.android.api

import org.onebusaway.android.api.contract.WeatherResponse
import org.onebusaway.android.api.contract.WeatherForecast

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Covers the weather decode path. Weather comes from the region's sidecar host as bare JSON (no
 * [ObaEnvelope]), so [WeatherResponse] decodes directly. The body below mirrors the live Puget Sound
 * payload shape (current + hourly forecasts sharing [WeatherForecast]); snake_case keys map without
 * `@SerialName`.
 */
class WeatherDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesWeatherResponse() {
        val body = """
            {
              "latitude": 47.6367,
              "longitude": -122.6953,
              "region_identifier": 1,
              "region_name": "Puget Sound",
              "retrieved_at": "2026-06-26T19:07:59.574+00:00",
              "units": "us",
              "today_summary": "Breezy this afternoon.",
              "current_forecast": {
                "icon": "wind",
                "precip_per_hour": 0.0,
                "precip_probability": 0.05,
                "summary": "Breezy and Partly Cloudy",
                "temperature": 62.42,
                "temperature_feels_like": 60.78,
                "time": 1782500400,
                "wind_speed": 15.77
              },
              "hourly_forecast": [
                {
                  "icon": "partly-cloudy-day", "precip_per_hour": 0.0, "precip_probability": 0.05,
                  "summary": "Partly Cloudy", "temperature": 62.15, "temperature_feels_like": 62.15,
                  "time": 1782500400, "wind_speed": 14.97
                },
                {
                  "icon": "wind", "precip_per_hour": 0.0, "precip_probability": 0.06,
                  "summary": "Breezy and Partly Cloudy", "temperature": 63.23,
                  "temperature_feels_like": 63.23, "time": 1782504000, "wind_speed": 15.41
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<WeatherResponse>(body)

        assertEquals(1, response.region_identifier)
        assertEquals("Puget Sound", response.region_name)
        assertEquals("us", response.units)
        assertEquals("Breezy this afternoon.", response.today_summary)

        val current = response.current_forecast
        assertNotNull(current)
        assertEquals("wind", current!!.icon)
        assertEquals(62.42, current.temperature, 1e-6)
        assertEquals(60.78, current.temperature_feels_like, 1e-6)
        assertEquals("Breezy and Partly Cloudy", current.summary)
        assertEquals(15.77, current.wind_speed, 1e-6)
        assertEquals(1782500400, current.time)

        assertEquals(2, response.hourly_forecast.size)
        assertEquals("partly-cloudy-day", response.hourly_forecast[0].icon)
        assertEquals(63.23, response.hourly_forecast[1].temperature, 1e-6)
    }
}
