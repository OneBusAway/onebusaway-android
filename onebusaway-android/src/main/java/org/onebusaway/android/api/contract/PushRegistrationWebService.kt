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

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * The OBACloud push-registration client (issue #1957) — like [ReminderWebService], it is served from
 * the region's sidecar host, not the OBA `where` host, so both calls pass the resolved URL via [Url]
 * and this service runs WITHOUT `ApiParamsInterceptor` (the Retrofit base URL is a throwaway).
 *
 * Registering the device's FCM token proactively (rather than only as a side effect of creating a trip
 * alarm) is what lets OBACloud deliver service-alert notifications to riders who enable notifications
 * but never set an alarm, capture the device locale, and keep tokens from ageing out at 180 days.
 *
 * Both calls return `204 No Content` on success, so the body is unused — [Response] carries only the
 * status. The registration endpoint is unauthenticated (ownership is possession of the token) and
 * rate-limited to 30 requests/minute/IP, so callers must avoid redundant registrations.
 */
interface PushRegistrationWebService {

    /**
     * Registers (or refreshes) this device's push token with the region. Form-urlencoded POST to
     * `…/regions/{id}/push_registrations`. [testDevice] must be sent on every call so a device is not
     * accidentally reset out of the test audience; [locale] is the device's BCP-47 tag sent as-is.
     */
    @FormUrlEncoded
    @POST
    suspend fun register(
        @Url url: String,
        @Field("token") token: String,
        @Field("locale") locale: String,
        @Field("test_device") testDevice: Boolean,
        @Field("operating_system") operatingSystem: String = "android",
    ): Response<Unit>

    /**
     * Unregisters this device's push [token] from the region (when the rider opts out of
     * notifications). DELETE to `…/regions/{id}/push_registrations`.
     *
     * The issue does not pin down how the token is conveyed to the DELETE; mirroring [register], it is
     * sent as a form-encoded body field. If the OBACloud contract turns out to expect a query param
     * instead, switch this to `@Query("token")`. Verify against the server spec before release.
     */
    @FormUrlEncoded
    @HTTP(method = "DELETE", hasBody = true)
    suspend fun unregister(@Url url: String, @Field("token") token: String): Response<Unit>
}
