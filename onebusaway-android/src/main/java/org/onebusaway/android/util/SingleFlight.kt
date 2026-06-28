/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Keyed single-flight execution: concurrent [run] calls for the same key share one execution of
 * the block, so duplicate work is coalesced — including across poll ticks when a slow execution
 * outlives its caller's retry interval. Entries clear themselves on completion, so a later call
 * runs fresh.
 *
 * Failures resolve to null for every joined caller (logged once); retry policy is left to
 * callers.
 *
 * Concurrency-safe: the in-flight map is a [ConcurrentHashMap], whose atomic `computeIfAbsent`
 * coalesces racing callers, so [run] may be invoked concurrently from any context. The coalesced
 * block always runs on [scope]'s dispatcher, not the caller's.
 */
class SingleFlight<K : Any, V : Any>(private val scope: CoroutineScope) {

    private val inFlight = ConcurrentHashMap<K, Deferred<V?>>()

    /** Runs [block] for [key] in [scope], or joins the in-flight execution for the same key. */
    suspend fun run(key: K, block: suspend () -> V?): V? =
            inFlight
                    // computeIfAbsent is atomic, so concurrent callers for one key share a Deferred.
                    // The coroutine is LAZY: await() (below) starts it only after computeIfAbsent has
                    // returned and stored the Deferred, so the finally/remove can never run
                    // re-entrantly — even for an eager dispatcher and a non-suspending block.
                    .computeIfAbsent(key) {
                        scope.async(start = CoroutineStart.LAZY) {
                            try {
                                block()
                            } catch (e: CancellationException) {
                                throw e // scope cancelled — propagate, don't mask as a null result
                            } catch (e: Exception) {
                                Log.e(TAG, "Single-flight block failed for $key", e)
                                null
                            } finally {
                                inFlight.remove(key)
                            }
                        }
                    }
                    .await()

    companion object {
        private const val TAG = "SingleFlight"
    }
}
