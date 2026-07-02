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
 * The regions-directory client — separate from [ObaWebService] because regions is fetched from a
 * fixed directory host (`regions_api_url`, e.g. https://regions.onebusaway.org/regions-v3.json),
 * not a per-region OBA host. It runs *before* a region is selected (it's how regions are
 * discovered), so it deliberately does NOT go through [ApiParamsInterceptor] (no host rewrite, no
 * key/version params). The absolute URL is passed per call via [Url], so the Retrofit base URL is a
 * throwaway and the flavor-specific filename (regions-v3/-v4.json) needs no special handling.
 */
interface RegionsWebService {

    @GET
    suspend fun getRegions(@Url url: String): ObaEnvelope<ListWithReferences<RegionDto>>
}
