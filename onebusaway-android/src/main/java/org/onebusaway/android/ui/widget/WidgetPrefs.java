/*
 * Copyright (C) 2025 Rob Godfrey (rob_godfrey@outlook.com)
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
package org.onebusaway.android.ui.widget;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Persists per-widget data to SharedPreferences using Gson for serialization.
 * Each widget instance is keyed by its appWidgetId.
 * <p>
 * Two types of data are stored per widget:
 * <p>
 * - {@link WidgetConfig}: the user's configuration (stop, name, route filter)
 * <p>
 * - {@link WidgetArrivalSnapshot}: the last fetched arrival data
 */
public class WidgetPrefs {
    private static final String TAG = "WidgetPrefs";
    private static final Gson GSON = new Gson();
    private static final String STOP_WIDGET_PREF_NAME = "stop_widgets";
    private static final String PENDING_CONFIG_KEY = "pending_pin_config";

    private WidgetPrefs() {
    }

    /// Serializes and persists a {@link WidgetConfig} for the given widget ID.
    public static void saveConfig(Context context, int widgetId, WidgetConfig config) {
        final String json = GSON.toJson(config);
        stopWidgetPrefs(context).edit()
                .putString(configKey(widgetId), json)
                .apply();
    }

    /// Serializes and persists a {@link WidgetArrivalSnapshot} for the given widget ID.
    public static void saveSnapshot(Context context, int widgetId, WidgetArrivalSnapshot snapshot) {
        final String json = GSON.toJson(snapshot);
        stopWidgetPrefs(context).edit()
                .putString(snapshotKey(widgetId), json)
                .apply();
    }

    /// Returns the saved {@link WidgetConfig} for the given widget ID, or {@code null} if none exists.
    @Nullable
    public static WidgetConfig loadConfig(Context context, int widgetId) {
        final String json = stopWidgetPrefs(context).getString(configKey(widgetId), null);
        return fromJson(json, WidgetConfig.class, widgetId);
    }

    /// Returns the saved {@link WidgetArrivalSnapshot} for the given widget ID, or {@code null} if none exists.
    @Nullable
    public static WidgetArrivalSnapshot loadSnapshot(Context context, int widgetId) {
        final String json = stopWidgetPrefs(context).getString(snapshotKey(widgetId), null);
        return fromJson(json, WidgetArrivalSnapshot.class, widgetId);
    }

    /// Saves a {@link WidgetConfig} as the pending pin config to be applied once the widget is placed.
    public static void savePendingPinConfig(Context context, WidgetConfig config) {
        final String json = GSON.toJson(config);
        stopWidgetPrefs(context).edit()
                .putString(PENDING_CONFIG_KEY, json)
                .apply();
    }

    /// Returns the pending pin config saved by {@link #savePendingPinConfig} and removes it from storage,
    /// or {@code null} if no pending config exists.
    @Nullable
    public static WidgetConfig loadAndClearPendingPinConfig(Context context) {
        final SharedPreferences prefs = stopWidgetPrefs(context);
        final String json = prefs.getString(PENDING_CONFIG_KEY, null);
        if (json != null) {
            prefs.edit().remove(PENDING_CONFIG_KEY).apply();
        }
        return fromJson(json, WidgetConfig.class, -1);
    }

    /// Removes all stored data (config and snapshot) for the given widget ID.
    public static void delete(Context context, int widgetId) {
        stopWidgetPrefs(context).edit()
                .remove(configKey(widgetId))
                .remove(snapshotKey(widgetId))
                .apply();
    }

    @Nullable
    private static <T> T fromJson(String json, Type type, int widgetId) {
        if (json == null) {
            return null;
        }
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to deserialize " + type + " for id=" + widgetId, e);
            return null;
        }
    }

    private static SharedPreferences stopWidgetPrefs(Context context) {
        return context.getSharedPreferences(STOP_WIDGET_PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String configKey(int widgetId) {
        return String.format("widget_%s_config", widgetId);
    }

    private static String snapshotKey(int widgetId) {
        return String.format("widget_%s_snapshot", widgetId);
    }
}
