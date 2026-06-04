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

import android.util.LruCache

/**
 * Permanent-identity registry with bounded payload retention. The same key always resolves to the
 * same instance for the life of the process, so holding a reference is always safe — capacity
 * pressure never destroys identity, it clears a value's *payload* ([clearPayload]) and leaves the
 * shell to refill if the key is ever written again.
 *
 * Retention: at most [maxWarm] values hold payload at once, evicted least-recently-used first.
 * [retain] inserts/promotes (writes), [acquire]/[lookup] promote on access (reads), so an
 * actively used key is never the eviction victim.
 *
 * Confinement: not thread-safe. All calls must be confined to a single thread (in practice the
 * main thread) — that confinement is what makes the unlocked identity map safe.
 */
class ShellRegistry<K : Any, V : Any>(
        maxWarm: Int,
        private val create: (K) -> V,
        private val clearPayload: (V) -> Unit
) {

    /** Permanent identity map: one instance per key, never removed. */
    private val registry = HashMap<K, V>()

    /** The keys currently retaining payload, in LRU order. Eviction clears payload only. */
    private val warm =
            object : LruCache<K, V>(maxWarm) {
                override fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {
                    // Only clear on capacity eviction — put() replacing an entry with the same
                    // instance also lands here, with evicted == false.
                    if (evicted) clearPayload(oldValue)
                }
            }

    /**
     * The permanent instance for [key], created on first use; promotes in the retention order.
     * Use when acquiring a reference to hold or write to; for transient reads prefer [lookup],
     * which doesn't create a shell as a side effect.
     */
    fun acquire(key: K): V {
        promote(key)
        return registry.getOrPut(key) { create(key) }
    }

    /**
     * Transient-read lookup: the instance if the key has ever been observed (promoting it in the
     * retention order), or null without creating a shell.
     */
    fun lookup(key: K?): V? {
        if (key == null) return null
        promote(key)
        return registry[key]
    }

    /** Acquires [key] as a retention-tracked write target: its payload counts as warm. */
    fun retain(key: K): V {
        val value = registry.getOrPut(key) { create(key) }
        warm.put(key, value)
        return value
    }

    /** Keys currently retaining payload (the eviction working set). */
    fun warmKeys(): Set<K> = warm.snapshot().keys

    /** Full reset, including identity — references held by callers are orphaned. */
    fun clear() {
        registry.clear()
        warm.evictAll()
    }

    /** Marks the key recently used so eviction prefers idle entries. No-op if not warm. */
    private fun promote(key: K) {
        // LruCache.get moves the entry to most-recently-used. Result intentionally unused.
        warm.get(key)
    }
}
