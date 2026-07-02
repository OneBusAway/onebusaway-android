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
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * The arrivals-reminders client — separate from [ObaWebService] because reminders are served from
 * the region's sidecar host, not the OBA `where` host. Both calls pass the resolved URL via [Url],
 * so this service runs WITHOUT [ApiParamsInterceptor]; the Retrofit base URL is a throwaway. Mirrors
 * [SurveyWebService].
 */
interface ReminderWebService {

    /**
     * Creates an alarm (form-urlencoded POST to `…/regions/{id}/alarms`). Returns the created alarm,
     * whose [ReminderResponse.url] is the delete path to persist for later cancellation.
     */
    @FormUrlEncoded
    @POST
    suspend fun createAlarm(
        @Url url: String,
        @Field("stop_id") stopId: String,
        @Field("service_date") serviceDate: Long,
        @Field("stop_sequence") stopSequence: Int,
        @Field("trip_id") tripId: String,
        @Field("user_push_id") userPushId: String,
        @Field("seconds_before") secondsBefore: Int,
        @Field("vehicle_id") vehicleId: String?,
        @Field("operating_system") operatingSystem: String = "android",
    ): ReminderResponse

    /** Deletes a previously-created alarm by its delete [url]. The body is unused (status only). */
    @DELETE
    suspend fun deleteAlarm(@Url url: String): Response<Unit>
}
