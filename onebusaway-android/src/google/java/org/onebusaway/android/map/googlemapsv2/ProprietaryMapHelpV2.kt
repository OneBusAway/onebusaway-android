/* Copyright (C) 2015 University of South Florida, Sean J. Barbeau */
package org.onebusaway.android.map.googlemapsv2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import com.google.android.gms.maps.model.Marker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.onebusaway.android.R

/** Helper methods specific to proprietary Google Maps integration. */
object ProprietaryMapHelpV2 {
    @JvmStatic
    fun isMapsInstalled(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                "com.google.android.apps.maps",
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            getApplicationInfoLegacy(context.packageManager)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun getApplicationInfoLegacy(packageManager: PackageManager) {
        packageManager.getApplicationInfo("com.google.android.apps.maps", 0)
    }

    @JvmStatic
    fun promptUserInstallMaps(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setMessage(context.getString(R.string.please_install_google_maps_dialog_title))
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.install_google_maps_positive_button)) { _, _ ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    context.getString(R.string.android_maps_v2_market_url).toUri()
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
