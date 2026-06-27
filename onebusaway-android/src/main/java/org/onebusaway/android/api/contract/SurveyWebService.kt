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

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The surveys ("studies") client — separate from [ObaWebService] because surveys are served from the
 * region's *sidecar* host, not the OBA `where` host, and the responses are bare JSON (no
 * [ObaEnvelope]). Each call passes the fully-resolved sidecar URL via [Url], so this service runs
 * WITHOUT [ApiParamsInterceptor] (no host rewrite, no key/version params); the Retrofit base URL is a
 * throwaway. Mirrors [RegionsWebService].
 */
interface SurveyWebService {

    /** Fetches the available studies for a region (the URL already has the region id substituted). */
    @GET
    suspend fun getStudy(
        @Url url: String,
        @Query("user_id") userId: String?,
    ): StudyResponse

    /**
     * Submits survey answers as a form post. [url] is the submit endpoint, optionally suffixed with a
     * prior response id to update follow-up answers. [responses] is the JSON-encoded answer array.
     */
    @FormUrlEncoded
    @POST
    suspend fun submitSurvey(
        @Url url: String,
        @Field("user_identifier") userIdentifier: String?,
        @Field("survey_id") surveyId: Int,
        @Field("stop_identifier") stopIdentifier: String?,
        @Field("stop_latitude") stopLatitude: Double,
        @Field("stop_longitude") stopLongitude: Double,
        @Field("responses") responses: String,
    ): SubmitSurveyResponse
}
