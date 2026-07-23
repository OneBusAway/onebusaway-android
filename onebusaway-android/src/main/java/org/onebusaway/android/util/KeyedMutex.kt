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
package org.onebusaway.android.util

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A mutex partitioned by key: [withLock] serializes only the actions that share a [key] and lets
 * unrelated keys run concurrently — one lightweight [Mutex] per distinct key, kept in a
 * [ConcurrentHashMap] so [withLock] is safe to call from any context (mirrors [SingleFlight]).
 *
 * The favorite stars (#2001) use it to serialize writes per entity id. Each tap on a star launches
 * an independent write coroutine; without this, two rapid taps on the same id (star then unstar)
 * could reach the store out of order and leave the DB — and the optimistic overlay that reconciles
 * against it — persistently disagreeing with the last tap. Because [Mutex] is fair (FIFO among
 * waiters) and acquiring the lock is the write's first act (nothing suspends before it), the writes
 * for one id run strictly in the order their [withLock] calls are reached; the callers launch on the
 * main dispatcher, which processes taps one at a time, so that order is tap order — and the store
 * always converges to the last tap. Different ids never contend.
 *
 * Keys are retained for the process lifetime (a favorited entity is a bounded set), so no removal
 * race with a concurrent locker is possible.
 */
class KeyedMutex<K : Any> {

    private val locks = ConcurrentHashMap<K, Mutex>()

    /** Runs [action] holding the lock for [key], excluding only other [withLock] calls for that key. */
    suspend fun <T> withLock(key: K, action: suspend () -> T): T = locks
        .getOrPut(key) { Mutex() }
        .withLock { action() }
}
