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
package org.onebusaway.android.ui.widget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.onebusaway.android.time.WallTime

/**
 * Persists per-widget data to `SharedPreferences`, as JSON (via kotlinx.serialization, this app's
 * existing JSON stack — see [org.onebusaway.android.directions.model.toJson] for the same
 * encode/decode-to-a-string shape). Each widget instance is keyed by its `appWidgetId`.
 *
 * Two kinds of data are stored per widget: the user's [WidgetConfig] (stop, name, route filter) and
 * the last-fetched [WidgetArrivalSnapshot].
 */
object WidgetPrefs {

    private const val TAG = "WidgetPrefs"
    private const val STOP_WIDGET_PREF_NAME = "stop_widgets"
    private const val PENDING_CONFIG_KEY = "pending_pin_config"

    // How long a saved pending-pin config is trusted before StopTimesWidget.onUpdate's fallback (for
    // devices where requestPinAppWidget places the widget without firing the ACTION_APPLY_PENDING_CONFIG
    // callback) will apply it to a newly-placed, unconfigured widget. There's only one slot for this —
    // it can't be scoped to a specific placement, since the whole point of the fallback is covering the
    // case where nothing ties a placement back to a request — so bounding its age keeps an abandoned
    // request (the user backed out, or started a second one before the first was ever placed) from
    // resurfacing on an unrelated later widget instead of just being dropped.
    private val PENDING_CONFIG_MAX_AGE = 5.minutes

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PendingPinConfig(val savedAtMs: Long, val config: WidgetConfig)

    /** Serializes and persists a [WidgetConfig] for the given widget id. */
    fun saveConfig(context: Context, widgetId: Int, config: WidgetConfig) {
        stopWidgetPrefs(context).edit()
            .putString(configKey(widgetId), json.encodeToString(WidgetConfig.serializer(), config))
            .apply()
    }

    /** Serializes and persists a [WidgetArrivalSnapshot] for the given widget id. */
    fun saveSnapshot(context: Context, widgetId: Int, snapshot: WidgetArrivalSnapshot) {
        stopWidgetPrefs(context).edit()
            .putString(snapshotKey(widgetId), json.encodeToString(WidgetArrivalSnapshot.serializer(), snapshot))
            .apply()
    }

    /** The saved [WidgetConfig] for the given widget id, or null if none exists. */
    fun loadConfig(context: Context, widgetId: Int): WidgetConfig? = decode(
        WidgetConfig.serializer(),
        stopWidgetPrefs(context).getString(configKey(widgetId), null),
        widgetId
    )

    /** The saved [WidgetArrivalSnapshot] for the given widget id, or null if none exists. */
    fun loadSnapshot(context: Context, widgetId: Int): WidgetArrivalSnapshot? = decode(
        WidgetArrivalSnapshot.serializer(),
        stopWidgetPrefs(context).getString(snapshotKey(widgetId), null),
        widgetId
    )

    /** Saves a [WidgetConfig] as the pending pin config, applied once `requestPinAppWidget` places the widget. */
    fun savePendingPinConfig(context: Context, config: WidgetConfig) {
        val pending = PendingPinConfig(savedAtMs = WallTime.now().epochMs, config = config)
        stopWidgetPrefs(context).edit()
            .putString(PENDING_CONFIG_KEY, json.encodeToString(PendingPinConfig.serializer(), pending))
            .apply()
    }

    /**
     * Returns the pending pin config saved by [savePendingPinConfig] and removes it from storage — or
     * null if none exists, or the saved one is older than [PENDING_CONFIG_MAX_AGE] (an abandoned request
     * from before the one that's actually being placed now; see [PENDING_CONFIG_MAX_AGE]). Either way
     * the stored record is cleared, so a stale one is never read twice.
     */
    fun loadAndClearPendingPinConfig(context: Context): WidgetConfig? {
        val prefs = stopWidgetPrefs(context)
        val raw = prefs.getString(PENDING_CONFIG_KEY, null)
        if (raw != null) prefs.edit().remove(PENDING_CONFIG_KEY).apply()
        val pending = decode(PendingPinConfig.serializer(), raw, widgetId = -1) ?: return null
        val age = WallTime.now() - WallTime(pending.savedAtMs)
        if (age < Duration.ZERO || age > PENDING_CONFIG_MAX_AGE) {
            Log.w(TAG, "Discarding pending pin config, age=$age")
            return null
        }
        return pending.config
    }

    /** Removes all stored data (config and snapshot) for the given widget id. */
    fun delete(context: Context, widgetId: Int) {
        stopWidgetPrefs(context).edit()
            .remove(configKey(widgetId))
            .remove(snapshotKey(widgetId))
            .apply()
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, raw: String?, widgetId: Int): T? {
        if (raw == null) return null
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to deserialize ${serializer.descriptor.serialName} for id=$widgetId", e)
            null
        }
    }

    private fun stopWidgetPrefs(context: Context): SharedPreferences = context.getSharedPreferences(STOP_WIDGET_PREF_NAME, Context.MODE_PRIVATE)

    private fun configKey(widgetId: Int) = "widget_${widgetId}_config"

    private fun snapshotKey(widgetId: Int) = "widget_${widgetId}_snapshot"
}
