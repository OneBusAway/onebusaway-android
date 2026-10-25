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
package org.onebusaway.android.api.net

import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.RateLimitedException
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.time.ElapsedTime
import retrofit2.HttpException
import retrofit2.Response

/**
 * The shared rate-limit gate (#2410): a 429 from any request closes its deployment's gate until a
 * cooldown after that request was sent, and nothing is sent to that deployment until it reopens.
 */
class RateLimitGateTest {

    private var now = ElapsedTime(1_000_000)
    private val gate = RateLimitGate(ElapsedClock { now })
    private var sent = 0

    private suspend fun send(deployment: String = PUGET_SOUND, outcome: Result<String> = Result.success("ok")): Result<String> = gate.pass(deployment) {
        sent++
        outcome
    }

    @Test
    fun anOpenGateSendsTheRequest() = runTest {
        assertEquals("ok", send().getOrThrow())
        assertEquals(1, sent)
    }

    @Test
    fun aClosedGateFailsFastWithoutSending() = runTest {
        send(outcome = Result.failure(edge429()))

        now += 3.seconds
        val refused = send().exceptionOrNull()

        assertTrue("refused as rate-limited, got $refused", refused is RateLimitedException)
        assertEquals("only the request that met the 429 was sent", 1, sent)
    }

    @Test
    fun theGateReopensAfterTheCooldown() = runTest {
        send(outcome = Result.failure(edge429()))

        now += RateLimitGate.COOLDOWN

        assertEquals("ok", send().getOrThrow())
        assertEquals(2, sent)
    }

    @Test
    fun theOBAServersOwn429ClosesTheGateToo() = runTest {
        send(outcome = Result.failure(ObaApiException(429)))

        assertTrue(send().exceptionOrNull() is RateLimitedException)
    }

    @Test
    fun otherFailuresLeaveTheGateOpen() = runTest {
        send(outcome = Result.failure(ObaApiException(500)))
        send(outcome = Result.failure(IOException("connection reset")))

        assertEquals("ok", send().getOrThrow())
        assertEquals(3, sent)
    }

    @Test
    fun theCooldownCountsFromWhenThe429sRequestWasSent() = runTest {
        // The 429 takes 300 ms to come back; the retry is timed a cooldown from its arrival, less a hair.
        gate.pass(PUGET_SOUND) {
            now += 300.milliseconds
            Result.failure<String>(edge429())
        }

        now += RateLimitGate.COOLDOWN - 1.milliseconds

        assertEquals("ok", send().getOrThrow())
    }

    @Test
    fun anInFlightRequestsLate429DoesNotExtendTheCooldown() = runTest {
        // Sent alongside the first, it comes back refused 4 s later: news about the same moment.
        gate.pass(PUGET_SOUND) {
            send(outcome = Result.failure(edge429()))
            now += 4.seconds
            Result.failure<String>(edge429())
        }

        now += RateLimitGate.COOLDOWN - 4.seconds

        assertEquals("ok", send().getOrThrow())
    }

    @Test
    fun theLatestSent429SetsTheDeadlineWhicheverReturnsFirst() = runTest {
        // The first request is still in flight when a second, sent 2 s later, comes back refused.
        gate.pass(PUGET_SOUND) {
            now += 2.seconds
            send(outcome = Result.failure(edge429()))
            now += 3.seconds
            Result.failure<String>(edge429())
        }

        now += RateLimitGate.COOLDOWN - 4.seconds
        assertTrue("a cooldown after the first was sent, but not the second", send().exceptionOrNull() is RateLimitedException)

        now += 1.seconds
        assertEquals("ok", send().getOrThrow())
    }

    @Test
    fun aLocalRefusalPassedOutOfTheBlockLeavesTheGateOpen() = runTest {
        // Nothing reached the server, so it says nothing new about the limit.
        send(outcome = Result.failure(RateLimitedException(1.seconds)))

        assertEquals("ok", send().getOrThrow())
        assertEquals(2, sent)
    }

    @Test
    fun oneDeploymentsLimitDoesNotBlockAnother() = runTest {
        send(PUGET_SOUND, Result.failure(edge429()))

        assertEquals("ok", send(TAMPA).getOrThrow())
    }

    private fun edge429() = HttpException(
        Response.error<Unit>(429, """{"message": "Too frequent requests."}""".toResponseBody())
    )

    private companion object {
        const val PUGET_SOUND = "https://api.pugetsound.onebusaway.org/"
        const val TAMPA = "https://api.tampa.onebusaway.org/api/"
    }
}
