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
package org.onebusaway.android.preferences

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.onebusaway.android.app.di.AppScope

/**
 * Injected access to user preferences — the single seam in front of the persisted store (replacing
 * scattered `Application.getPrefs()` / `PreferenceUtils` reads).
 *
 * Two ways in:
 * - [observeBoolean] / [observeString] / [observeChanges] are the *reactive* seam — a consumer that
 *   would otherwise poll a pref on resume collects it instead.
 * - The synchronous `getX` / `setX` accessors are the one-shot replacement for `PreferenceUtils`.
 *
 * Every accessor comes in two key forms. The `@StringRes` overload lets a caller name a pref by its
 * resource id and stay Context-free / JVM-testable (the implementation resolves it); the `String`
 * overload handles keys that are const or runtime strings rather than resources (e.g. the
 * region-version slots, a map layer's preference key).
 */
interface PreferencesRepository {

    /** Emits the current value of the boolean pref [keyRes] and re-emits on every change. */
    fun observeBoolean(@StringRes keyRes: Int, default: Boolean): Flow<Boolean>

    /** Emits the current value of the string pref [keyRes] and re-emits on every change. */
    fun observeString(@StringRes keyRes: Int, default: String?): Flow<String?>

    /**
     * Emits once immediately, then a [Unit] on every preference change (any key). For a screen that
     * derives its whole state from many keys at once (e.g. Settings), collecting this and re-reading
     * the values synchronously is simpler than combining a flow per key.
     */
    fun observeChanges(): Flow<Unit>

    fun getBoolean(@StringRes keyRes: Int, default: Boolean): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean

    fun getString(@StringRes keyRes: Int, default: String?): String?
    fun getString(key: String, default: String?): String?

    fun getInt(@StringRes keyRes: Int, default: Int): Int
    fun getInt(key: String, default: Int): Int

    fun getLong(@StringRes keyRes: Int, default: Long): Long
    fun getLong(key: String, default: Long): Long

    fun getFloat(@StringRes keyRes: Int, default: Float): Float
    fun getFloat(key: String, default: Float): Float

    fun setBoolean(@StringRes keyRes: Int, value: Boolean)
    fun setBoolean(key: String, value: Boolean)

    fun setString(@StringRes keyRes: Int, value: String?)
    fun setString(key: String, value: String?)

    fun setInt(@StringRes keyRes: Int, value: Int)
    fun setInt(key: String, value: Int)

    fun setLong(@StringRes keyRes: Int, value: Long)
    fun setLong(key: String, value: Long)

    fun setFloat(@StringRes keyRes: Int, value: Float)
    fun setFloat(key: String, value: Float)
}

/**
 * Default implementation backed by Preferences [DataStore].
 *
 * DataStore is async (Flow/suspend), but many callers — `PreferenceUtils`, background services, the
 * content provider — read synchronously and can't suspend. So this keeps a synchronous, read-your-writes
 * in-memory [cache] of the latest [Preferences]: it's seeded once on construction (which runs the
 * one-time SharedPreferences→DataStore migration) and is kept current by [put]'s optimistic updates.
 * Writes update the cache synchronously and persist asynchronously. Single-process only (the app has no
 * `android:process`), which is required for a single DataStore instance — and is what lets the
 * optimistic cache be the source of truth: this repository is the only writer.
 *
 * Note: the cache is intentionally NOT mirrored from a `dataStore.data` collector. DataStore commits
 * (and their flow emissions) are async and ordered after [put]'s synchronous cache update, so a
 * collector could deliver a *stale* emission of an earlier write after a newer optimistic write and
 * clobber it — breaking read-your-writes (see PreferencesRepositoryReadYourWritesTest). Reactive callers
 * read [dataStore]`.data` directly via the `observe*` methods, so they don't need the cache.
 */
class DefaultPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    @AppScope private val scope: CoroutineScope,
) : PreferencesRepository {

    // Seeded synchronously so the first synchronous read (e.g. from Application.onCreate) sees real
    // values; the first DataStore access also performs the migration. Kept current by [put] thereafter.
    @Volatile
    private var cache: Preferences = runBlocking { dataStore.data.first() }

    // --- reactive ---

    override fun observeBoolean(keyRes: Int, default: Boolean): Flow<Boolean> {
        val key = booleanPreferencesKey(context.getString(keyRes))
        return dataStore.data.map { it[key] ?: default }.distinctUntilChanged()
    }

    override fun observeString(keyRes: Int, default: String?): Flow<String?> {
        val key = stringPreferencesKey(context.getString(keyRes))
        return dataStore.data.map { it[key] ?: default }.distinctUntilChanged()
    }

    override fun observeChanges(): Flow<Unit> = dataStore.data.map { }.conflate()

    // --- synchronous reads (from the cache) ---
    // The @StringRes overloads resolve the key and delegate to the String overloads.

    override fun getBoolean(keyRes: Int, default: Boolean) = getBoolean(context.getString(keyRes), default)
    override fun getBoolean(key: String, default: Boolean) = cache[booleanPreferencesKey(key)] ?: default

    override fun getString(keyRes: Int, default: String?) = getString(context.getString(keyRes), default)
    override fun getString(key: String, default: String?) = cache[stringPreferencesKey(key)] ?: default

    override fun getInt(keyRes: Int, default: Int) = getInt(context.getString(keyRes), default)
    override fun getInt(key: String, default: Int) = cache[intPreferencesKey(key)] ?: default

    override fun getLong(keyRes: Int, default: Long) = getLong(context.getString(keyRes), default)
    override fun getLong(key: String, default: Long) = cache[longPreferencesKey(key)] ?: default

    override fun getFloat(keyRes: Int, default: Float) = getFloat(context.getString(keyRes), default)
    override fun getFloat(key: String, default: Float) = cache[floatPreferencesKey(key)] ?: default

    // --- writes (optimistic cache update + async persist) ---

    override fun setBoolean(keyRes: Int, value: Boolean) = setBoolean(context.getString(keyRes), value)
    override fun setBoolean(key: String, value: Boolean) = put(booleanPreferencesKey(key), value)

    override fun setString(keyRes: Int, value: String?) = setString(context.getString(keyRes), value)
    override fun setString(key: String, value: String?) = put(stringPreferencesKey(key), value)

    override fun setInt(keyRes: Int, value: Int) = setInt(context.getString(keyRes), value)
    override fun setInt(key: String, value: Int) = put(intPreferencesKey(key), value)

    override fun setLong(keyRes: Int, value: Long) = setLong(context.getString(keyRes), value)
    override fun setLong(key: String, value: Long) = put(longPreferencesKey(key), value)

    override fun setFloat(keyRes: Int, value: Float) = setFloat(context.getString(keyRes), value)
    override fun setFloat(key: String, value: Float) = put(floatPreferencesKey(key), value)

    /** Apply [value] (or remove the key when null) to the cache immediately, then persist async. */
    private fun <T : Any> put(key: Preferences.Key<T>, value: T?) {
        cache = cache.toMutablePreferences().apply {
            if (value == null) remove(key) else set(key, value)
        }.toPreferences()
        scope.launch {
            dataStore.edit { prefs -> if (value == null) prefs.remove(key) else prefs[key] = value }
        }
    }
}
