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
package org.onebusaway.android.ui.report.customerservice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageRequest

/**
 * A transit agency's customer-service contact options, decoupled from the io/elements response
 * types. Blank email/url/phone are normalized to null so the UI only needs one check each.
 */
data class AgencyContact(
    val id: String,
    val name: String,
    val email: String?,
    val url: String?,
    val phone: String?
)

/** Provides the customer-service contacts for the agencies covering the current region. */
interface CustomerServiceRepository {

    suspend fun getAgencies(): Result<List<AgencyContact>>
}

/**
 * Default implementation wrapping the blocking OBA REST call (replacing the legacy AgenciesLoader).
 * RequestBase.call() never throws — errors surface as a non-OBA_OK code — so failures map to
 * [Result.failure] here.
 */
class DefaultCustomerServiceRepository @Inject constructor(@ApplicationContext private val context: Context) : CustomerServiceRepository {

    override suspend fun getAgencies(): Result<List<AgencyContact>> = withContext(Dispatchers.IO) {
        val response = ObaAgenciesWithCoverageRequest.newRequest(context).call()
        if (response == null || response.code != ObaApi.OBA_OK) {
            return@withContext Result.failure(
                IOException("Agencies request failed with code " + response?.code)
            )
        }
        Result.success(response.agencies.mapNotNull { coverage ->
            val agency = response.getAgency(coverage.id) ?: return@mapNotNull null
            AgencyContact(
                id = agency.id,
                name = agency.name,
                email = agency.email?.takeIf { it.isNotEmpty() },
                url = agency.url?.takeIf { it.isNotEmpty() },
                phone = agency.phone?.takeIf { it.isNotEmpty() }
            )
        })
    }
}
