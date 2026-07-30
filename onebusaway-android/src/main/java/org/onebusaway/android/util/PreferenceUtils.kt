/* Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com) */
package org.onebusaway.android.util

import android.content.Context
import android.os.Bundle
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import java.util.Locale
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * A thin synchronous facade over the [PreferencesRepository] seam — every read/write routes through
 * the repository (resolved via [PreferencesEntryPoint]) rather than touching `SharedPreferences`
 * directly. That keeps these static helpers, and their many call sites, working unchanged while the
 * underlying store is free to move (e.g. to DataStore) behind the seam.
 *
 * The [Application.get] reach in [repo] is the one deliberately-kept service-locator hop (#1636):
 * threading a `Context` through every call site would be churn without changing the seam. The real
 * removal is making this object injectable, which is tracked separately.
 */
object PreferenceUtils {
    private fun repo(): PreferencesRepository = PreferencesEntryPoint.get(Application.get())

    fun saveString(key: String, value: String?) = repo().setString(key, value)

    fun saveInt(key: String, value: Int) = repo().setInt(key, value)

    fun saveLong(key: String, value: Long) = repo().setLong(key, value)

    fun saveBoolean(key: String, value: Boolean) = repo().setBoolean(key, value)

    private fun saveFloat(key: String, value: Float) = repo().setFloat(key, value)

    fun saveDouble(key: String, value: Double) = saveLong(key, value.toRawBits())

    fun getDouble(key: String, defaultValue: Double): Double = Double.fromBits(repo().getLong(key, defaultValue.toRawBits()))

    fun getStopSortOrderFromPreferences(context: Context): Int = sortOrderFromPreferences(context, R.array.sort_stops, R.string.preference_key_default_stop_sort)

    fun getReminderSortOrderFromPreferences(context: Context): Int = sortOrderFromPreferences(context, R.array.sort_reminders, R.string.preference_key_default_reminder_sort)

    private fun sortOrderFromPreferences(context: Context, @ArrayRes optionsRes: Int, @StringRes keyRes: Int): Int {
        val resources = context.resources
        val options = resources.getStringArray(optionsRes)
        val selected = repo().getString(resources.getString(keyRes), options[0])
        return options.take(2).indexOfFirst { selected.equals(it, ignoreCase = true) }.coerceAtLeast(0)
    }

    fun saveMapViewToPreferences(lat: Double, lon: Double, zoom: Float) {
        saveDouble(MapParams.CENTER_LAT, lat)
        saveDouble(MapParams.CENTER_LON, lon)
        saveFloat(MapParams.ZOOM, zoom)
    }

    fun maybeRestoreMapViewToBundle(bundle: Bundle) {
        val lat = getDouble(MapParams.CENTER_LAT, 0.0)
        val lon = getDouble(MapParams.CENTER_LON, 0.0)
        val zoom = getFloat(MapParams.ZOOM, MapParams.DEFAULT_ZOOM.toFloat())
        if (lat != 0.0 && lon != 0.0 && zoom != 0f) {
            bundle.putDouble(MapParams.CENTER_LAT, lat)
            bundle.putDouble(MapParams.CENTER_LON, lon)
            bundle.putFloat(MapParams.ZOOM, zoom)
        }
    }

    fun getString(key: String): String? = repo().getString(key, null)

    fun getString(key: String, defaultValue: String?): String? = repo().getString(key, defaultValue)

    fun getLong(key: String, defaultValue: Long): Long = repo().getLong(key, defaultValue)

    fun getFloat(key: String, defaultValue: Float): Float = repo().getFloat(key, defaultValue)

    fun getInt(key: String, defaultValue: Int): Int = repo().getInt(key, defaultValue)

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = repo().getBoolean(key, defaultValue)

    fun userDeniedLocationPermission(context: Context): Boolean = getBoolean(
        context.getString(R.string.preferences_key_user_denied_location_permissions),
        false
    )

    fun setUserDeniedLocationPermissions(context: Context, value: Boolean) = saveBoolean(
        context.getString(R.string.preferences_key_user_denied_location_permissions),
        value
    )

    fun getUnitsAreMetricFromPreferences(context: Context): Boolean {
        val metric = context.getString(R.string.preferences_preferred_units_option_metric)
        val automatic = context.getString(R.string.preferences_preferred_units_option_automatic)
        val preferred = repo().getString(
            context.getString(R.string.preference_key_preferred_units),
            automatic
        )
        return if (preferred.equals(automatic, ignoreCase = true)) {
            !Locale.getDefault().isO3Country.equals(Locale.US.isO3Country, ignoreCase = true)
        } else {
            preferred.equals(metric, ignoreCase = true)
        }
    }
}
