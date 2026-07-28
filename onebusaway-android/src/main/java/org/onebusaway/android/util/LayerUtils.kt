/* Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com) */
package org.onebusaway.android.util

import android.content.Context
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint

/** Utility methods related to optional map layers. */
object LayerUtils {
    @JvmStatic
    fun isBikeshareLayerVisible(context: Context): Boolean = BikeshareAvailability.isStationLayerEnabled(context) &&
        PreferencesEntryPoint.get(context)
            .getBoolean(R.string.preference_key_layer_bikeshare_visible, true)
}
