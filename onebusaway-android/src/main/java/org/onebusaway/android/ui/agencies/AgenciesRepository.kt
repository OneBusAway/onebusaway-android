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
package org.onebusaway.android.ui.agencies

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageRequest

/**
 * A transit agency as displayed on the supported agencies screen, decoupled from the
 * io/elements response types.
 *
 * @param url the agency's website, or null if it has none (never blank)
 */
data class AgencyItem(
    val id: String,
    val name: String,
    val url: String?
)

/** Provides the list of transit agencies covered by the current region. */
interface AgenciesRepository {

    suspend fun getAgencies(): Result<List<AgencyItem>>
}

/**
 * Default implementation wrapping the blocking OBA REST call. Note that RequestBase.call()
 * never throws — errors surface as a response with a non-OBA_OK code — so failures are mapped
 * to [Result.failure] here.
 */
class DefaultAgenciesRepository @Inject constructor(@ApplicationContext private val context: Context) : AgenciesRepository {

    override suspend fun getAgencies(): Result<List<AgencyItem>> = withContext(Dispatchers.IO) {
        val response = ObaAgenciesWithCoverageRequest.newRequest(context).call()
        if (response == null || response.code != ObaApi.OBA_OK) {
            return@withContext Result.failure(
                IOException("Agencies request failed with code " + response?.code)
            )
        }
        Result.success(response.agencies.mapNotNull { agencyWithCoverage ->
            val agency = response.getAgency(agencyWithCoverage.id) ?: return@mapNotNull null
            AgencyItem(
                id = agency.id,
                name = agency.name,
                // Normalize blank URLs to null so consumers only need one check
                url = agency.url?.takeIf { it.isNotEmpty() }
            )
        })
    }
}
