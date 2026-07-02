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
 * The bike-rental client — separate from [ObaWebService] because bike stations are fetched from an
 * OpenTripPlanner server (the region's `otpBaseUrl`, or the user's custom OTP url), not the OBA
 * `where` host, and the response is plain JSON. The caller builds the full OTP URL (base +
 * `routers/default/bike_rental` + the viewport bbox) and passes it via [Url], so this service runs
 * WITHOUT [ApiParamsInterceptor]; the Retrofit base URL is a throwaway. Mirrors [RegionsWebService].
 */
interface BikeWebService {

    @GET
    suspend fun getBikeStations(@Url url: String): BikeRentalStationsDto
}
