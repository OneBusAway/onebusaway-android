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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.api.RateLimitedException
import org.onebusaway.android.api.isRateLimited
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.time.ElapsedTime

/**
 * One "rate-limited until T" gate per deployment, shared by every request that deployment is sent
 * (#2410). After any request comes back a 429, the gate stays closed until [COOLDOWN] after that
 * request was sent, and a request meant for that deployment in the meantime fails fast with
 * [RateLimitedException] without touching the network.
 *
 * Puget Sound's limit is one bucket for everything this client sends: the AWS edge applies it
 * (`Server: awselb/2.0`) before the API key is even read (#2405). A caller backing off on its own
 * doesn't help while the others keep draining that bucket, and its retry gets refused too. Failing
 * fast keeps each caller's existing error path, and a poll loop's backoff still sees the failure as
 * [isRateLimited]. Holding requests until the gate reopened would release them together, which is
 * the burst that trips the limit in the first place.
 *
 * Keyed by deployment (the base URL), so one region's limit doesn't block another.
 */
internal class RateLimitGate(private val clock: ElapsedClock) {
    private val closedUntil = HashMap<String, ElapsedTime>()

    /**
     * Runs [block] for [deployment], unless that deployment's gate is closed, in which case it yields a
     * [RateLimitedException] failure without running it. A 429 from [block] closes the gate.
     *
     * The cooldown counts from when the request was sent, not when its 429 arrived: the refusal is
     * news about the bucket as it stood then. So a request that was already in flight when the gate
     * closed, and comes back refused later, doesn't push the gate out. A poll loop's retry one
     * interval after its own 429 always finds the gate open, with the round trip to spare. Only a real
     * 429 counts: a local refusal passed back out of [block] was never sent, so it says nothing new.
     */
    suspend fun <T> pass(deployment: String, block: suspend () -> Result<T>): Result<T> {
        closedFor(deployment)?.let { return Result.failure(RateLimitedException(it)) }
        val sentAt = clock.now()
        return block().onFailure { if (it.isRateLimited && it !is RateLimitedException) close(deployment, sentAt) }
    }

    /** How long [deployment]'s gate stays closed, or null when it is open. */
    @Synchronized
    fun closedFor(deployment: String): Duration? = closedUntil[deployment]?.let { it - clock.now() }?.takeIf { it.isPositive() }

    /** Closes [deployment]'s gate until [COOLDOWN] after [sentAt], unless a later-sent 429 already holds it longer. */
    @Synchronized
    private fun close(deployment: String, sentAt: ElapsedTime) {
        val until = sentAt + COOLDOWN
        closedUntil[deployment] = maxOf(until, closedUntil[deployment] ?: until)
    }

    companion object {
        /**
         * How long a 429 closes the gate. A tuning constant, signed off in #2410: the API sends no
         * `Retry-After`, and the only measurement of how long a refusal lasts is one probe of Puget
         * Sound's edge limiter (2026-10-23: still refused 10 s after the first 429, clear by 20 s).
         * It matches the poll loops' first retry after a 429 (`nextPollDelayMs`), so that retry is
         * the first request through, with nothing else sent in the meantime; it must not be longer.
         * If the limit is still standing, the retry's 429 closes the gate again.
         */
        val COOLDOWN = 10.seconds
    }
}
