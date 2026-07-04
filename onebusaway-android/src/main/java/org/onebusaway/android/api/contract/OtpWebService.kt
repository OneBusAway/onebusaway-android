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

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * The OpenTripPlanner trip-planner client — separate from [ObaWebService] because the `/plan` request
 * goes to the region's OTP server (its `otpBaseUrl`, or the user's custom OTP url), not the OBA
 * `where` host. The caller ([org.onebusaway.android.ui.tripplan.DefaultTripPlanRepository]) assembles
 * the full OTP URL (base + `routers/default/plan` + the query it builds from the OTP `Request`) and
 * passes it via [Url], so — like [BikeWebService] — this runs WITHOUT `ApiParamsInterceptor` and the
 * Retrofit base URL is a throwaway.
 *
 * Returns the raw [ResponseBody] (not a decoded DTO) as a **synchronous** [Call], for two reasons:
 * the body is parsed through the shared [OtpPlanParser] (keeping the OTP-library `Response` mapping +
 * malformed-body handling in one place, and letting the repository detect the old-server 404 to fall
 * back to the legacy URL structure), and the repository has a blocking `planBlocking` path (the Java
 * `RealtimeService` worker) that must call it without a coroutine.
 */
interface OtpWebService {

    @GET
    fun plan(@Url url: String): Call<ResponseBody>
}
