/* Copyright (C) 2015 University of South Florida, Sean J. Barbeau */
package org.onebusaway.android.map.googlemapsv2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.google.android.gms.maps.model.Marker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.onebusaway.android.R

/** Helper methods specific to proprietary Google Maps integration. */
object ProprietaryMapHelpV2 {
    @Suppress("DEPRECATION")
    @JvmStatic
    fun isMapsInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo("com.google.android.apps.maps", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    @JvmStatic
    fun promptUserInstallMaps(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setMessage(context.getString(R.string.please_install_google_maps_dialog_title))
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.install_google_maps_positive_button)) { _, _ ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(context.getString(R.string.android_maps_v2_market_url))
                )
                if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    context.startActivity(intent)
                } else {
                    MaterialAlertDialogBuilder(context)
                        .setMessage(context.getString(R.string.no_play_store))
                        .setCancelable(true)
                        .setPositiveButton(context.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
            .show()
    }

    @JvmStatic fun setZIndex(marker: Marker, zIndex: Float) {
        marker.zIndex = zIndex
    }
}
