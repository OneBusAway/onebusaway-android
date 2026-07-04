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
package org.onebusaway.android.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.onebusaway.android.app.Application
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * In-memory [PreferencesRepository] for JVM unit tests. The synchronous accessors read/write a
 * backing map keyed by the resource id or string so they round-trip; the reactive [observeBoolean] /
 * [observeString] accessors are backed by a per-key [MutableStateFlow] so a test can flip one pref and
 * assert the resulting state change. A key that's never been set seeds its flow with [observeValue]
 * (booleans) / null (strings), preserving the old "single reactive value" behavior for callers that
 * only read one pref.
 *
 * Pre-seed synchronous values with the `setX` methods before constructing the subject under test.
 */
class FakePreferencesRepository(private val observeValue: Boolean = true) : PreferencesRepository {

    private val values = mutableMapOf<Any, Any?>()

    // Per-key reactive flows, keyed by the same key the synchronous map uses (resource id or string).
    private val boolFlows = mutableMapOf<Any, MutableStateFlow<Boolean>>()
    private val stringFlows = mutableMapOf<Any, MutableStateFlow<String?>>()

    // Un-set boolean keys seed to [observeValue] (legacy default-true behavior); strings to their default.
    private fun boolFlow(key: Any) =
        boolFlows.getOrPut(key) { MutableStateFlow((values[key] as Boolean?) ?: observeValue) }

    private fun stringFlow(key: Any, default: String?) =
        stringFlows.getOrPut(key) { MutableStateFlow((values[key] as String?) ?: default) }

    override fun observeBoolean(keyRes: Int, default: Boolean): Flow<Boolean> = boolFlow(keyRes)

    override fun observeString(keyRes: Int, default: String?): Flow<String?> = stringFlow(keyRes, default)

    override fun observeChanges(): Flow<Unit> = flowOf(Unit)

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(key: Any, default: T): T = (values[key] as T?) ?: default

    override fun getBoolean(keyRes: Int, default: Boolean) = read(keyRes, default)
    override fun getBoolean(key: String, default: Boolean) = read(key, default)
    override fun getString(keyRes: Int, default: String?) = read(keyRes, default)
    override fun getString(key: String, default: String?) = read(key, default)
    override fun getInt(keyRes: Int, default: Int) = read(keyRes, default)
    override fun getInt(key: String, default: Int) = read(key, default)
    override fun getLong(keyRes: Int, default: Long) = read(keyRes, default)
    override fun getLong(key: String, default: Long) = read(key, default)
    override fun getFloat(keyRes: Int, default: Float) = read(keyRes, default)
    override fun getFloat(key: String, default: Float) = read(key, default)

    // Mirrors the real impl: reads APP_LAUNCH_COUNT_KEY, so a test can seed it via setInt(key, n).
    override fun getAppLaunchCount() = getInt(Application.APP_LAUNCH_COUNT_KEY, 0)

    override fun setBoolean(keyRes: Int, value: Boolean) { values[keyRes] = value; boolFlow(keyRes).value = value }
    override fun setBoolean(key: String, value: Boolean) { values[key] = value; boolFlow(key).value = value }
    override fun setString(keyRes: Int, value: String?) { values[keyRes] = value; stringFlow(keyRes, null).value = value }
    override fun setString(key: String, value: String?) { values[key] = value; stringFlow(key, null).value = value }
    override fun setInt(keyRes: Int, value: Int) { values[keyRes] = value }
    override fun setInt(key: String, value: Int) { values[key] = value }
    override fun setLong(keyRes: Int, value: Long) { values[keyRes] = value }
    override fun setLong(key: String, value: Long) { values[key] = value }
    override fun setFloat(keyRes: Int, value: Float) { values[keyRes] = value }
    override fun setFloat(key: String, value: Float) { values[key] = value }
}
