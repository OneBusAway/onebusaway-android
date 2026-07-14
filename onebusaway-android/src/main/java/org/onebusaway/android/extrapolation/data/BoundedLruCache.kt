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
package org.onebusaway.android.extrapolation.data

/**
 * A generic bounded, access-ordered LRU cache backed by a plain [LinkedHashMap]: once [maxSize]
 * entries are held, each insert evicts the least-recently-used one. Every access (read or write)
 * promotes its entry.
 *
 * Every method is `@Synchronized`: the access-order map mutates on read as well as write, and
 * callers arrive on arbitrary threads, so all access must be serialized. Use [compute] rather
 * than a separate [get] and [put] when a write depends on the prior value — that keeps the
 * read-transform-write atomic instead of racing with a concurrent writer between the two calls.
 */
internal class BoundedLruCache<K : Any, V : Any>(private val maxSize: Int) {

    private val entries =
            object : LinkedHashMap<K, V>(16, 0.75f, /* accessOrder = */ true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
                        size > maxSize
            }

    /** The cached value for [key], or null if it was never cached (or has been evicted). */
    @Synchronized
    fun get(key: K): V? = entries[key]

    /** Stores [value] under [key], promoting it in the retention order. */
    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = value
    }

    /** Removes [key], returning its previous value when present. */
    @Synchronized
    fun remove(key: K): V? = entries.remove(key)

    /**
     * Atomically applies [transform] to the current value for [key] (or a fresh [default] if
     * absent), stores the result, and returns it — the compute-and-put that [get]+[put] can't
     * express without a race.
     */
    @Synchronized
    fun compute(key: K, default: () -> V, transform: (V) -> V): V {
        val newValue = transform(entries[key] ?: default())
        entries[key] = newValue
        return newValue
    }

    /** The keys currently cached (the eviction working set). */
    @Synchronized
    fun keys(): Set<K> = entries.keys.toSet()

    /** Drops all cached entries. */
    @Synchronized
    fun clear() {
        entries.clear()
    }
}
