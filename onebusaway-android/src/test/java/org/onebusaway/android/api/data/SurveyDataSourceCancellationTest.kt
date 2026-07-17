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
package org.onebusaway.android.api.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.onebusaway.android.api.contract.SurveyWebService

/**
 * A cancelled survey fetch must let its [CancellationException] propagate rather than swallow it into a
 * `Result.failure`. `runCatching` catches everything (including cancellation), so the data source
 * rethrows the cancellation before it can be logged or reported as an ordinary failure — otherwise a
 * cancelled coroutine would keep running and structured concurrency would break. This mirrors
 * `SingleFlightTest`'s "cancelled block propagates" coverage; the same rethrow guard is applied across
 * the api/repository layer (ObaApiProvider, the search repositories, etc.), which are Android-typed and
 * so not directly JVM-testable here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyDataSourceCancellationTest {

    /** A web service whose calls behave as if the coroutine was cancelled mid-request. */
    private val cancellingService = object : SurveyWebService {
        override suspend fun getStudy(url: String, userId: String?) =
            throw CancellationException("cancelled")

        override suspend fun submitSurvey(
            url: String,
            userIdentifier: String?,
            surveyId: Int,
            stopIdentifier: String?,
            stopLatitude: Double,
            stopLongitude: Double,
            responses: String,
        ) = throw CancellationException("cancelled")
    }

    private val dataSource = DefaultSurveyDataSource(cancellingService)

    @Test
    fun `a cancelled study fetch propagates instead of becoming a Result failure`() = runTest {
        try {
            dataSource.studies("url", userId = null)
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertTrue(e.message == "cancelled")
        }
    }

    @Test
    fun `a cancelled submit propagates instead of becoming a Result failure`() = runTest {
        try {
            dataSource.submit("url", null, 1, null, 0.0, 0.0, "[]")
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertTrue(e.message == "cancelled")
        }
    }
}
