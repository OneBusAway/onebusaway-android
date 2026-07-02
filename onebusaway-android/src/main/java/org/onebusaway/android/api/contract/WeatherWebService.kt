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

import retrofit2.http.GET
import retrofit2.http.Url

/**
 * The weather client — separate from [ObaWebService] because weather is served from the region's
 * sidecar host (`weather_api_endpoint`, with the region id substituted), not the OBA `where` host,
 * and the response is bare JSON. The caller passes the resolved URL via [Url], so this service runs
 * WITHOUT [ApiParamsInterceptor]; the Retrofit base URL is a throwaway. Mirrors [SurveyWebService].
 */
interface WeatherWebService {

    @GET
    suspend fun getWeather(@Url url: String): WeatherResponse
}
