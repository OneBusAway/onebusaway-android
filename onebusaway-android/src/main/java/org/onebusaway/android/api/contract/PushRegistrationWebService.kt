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
import retrofit2.http.Query
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
     * `…/regions/{id}/push_registrations` (issue #1957). The server upserts on `(region, token)` and
     * overwrites the stored value from each call, so [testDevice] must be sent every time (an omitted
     * `test_device` resets it to `false`) — the wire value is the literal `true`/`false` the contract
     * documents. [locale] is the device's BCP-47 tag sent as-is; the server maps it to its translation
     * catalog itself.
     *
     * [description] is a human-readable device label that the server requires **only** when
     * [testDevice] is true, rejecting the call otherwise with
     * `422 {"error":"Unable to register device","messages":["Description can't be blank"]}`. Pass null
     * for an ordinary registration and Retrofit omits the field entirely — deliberate, so an ordinary
     * rider's device model is never sent.
     *
     * OBACloud's push-notifications documentation now specifies this field, confirming the behaviour
     * this client was originally built against by probing the deployed server: free text "≤255 chars
     * identifying the device to admins" (e.g. `"Aaron's iPhone 17"`), "server-enforced when
     * `test_device=true` (422 if blank)", and cleared server-side when a device is demoted to
     * `test_device=false`. The rider supplies the value, and it is capped at
     * `PUSH_DESCRIPTION_MAX_LENGTH` before it reaches here.
     */
    @FormUrlEncoded
    @POST
    suspend fun register(
        @Url url: String,
        @Field("token") token: String,
        @Field("locale") locale: String,
        @Field("test_device") testDevice: Boolean,
        @Field("description") description: String?,
        @Field("operating_system") operatingSystem: String = "android"
    ): Response<Unit>

    /**
     * Unregisters this device's push [token] from the region (when the rider opts out of
     * notifications). DELETE to `…/regions/{id}/push_registrations` with the token as a query param
     * (issue #1957) — the form OBACloud's push-notifications documentation specifies:
     * `DELETE /api/v2/regions/{region_id}/push_registrations?token={token}`. It also documents a `404`
     * (token never registered) as equivalent to success, which [PushRegistrationClient] treats as such.
     */
    @HTTP(method = "DELETE")
    suspend fun unregister(@Url url: String, @Query("token") token: String): Response<Unit>
}
