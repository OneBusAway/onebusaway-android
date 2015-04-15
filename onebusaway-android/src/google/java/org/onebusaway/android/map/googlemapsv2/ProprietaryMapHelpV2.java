package org.onebusaway.android.map.googlemapsv2;

import org.onebusaway.android.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * Helper methods specific to Google Maps API v2
 */
public class ProprietaryMapHelpV2 {

    /**
     * Returns true if Android Maps V2 is installed, false if it is not
     */
    public static boolean isMapsInstalled(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo("com.google.android.apps.maps", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Prompts the user to install Android Maps V2 from Google Play
     */
    public static void promptUserInstallMaps(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(context.getString(R.string.please_install_google_maps_dialog_title));
        builder.setCancelable(false);
        builder.setPositiveButton(context.getString(R.string.install_google_maps_positive_button),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(context.getString(R.string.android_maps_v2_market_url)));
                        context.startActivity(intent);
                    }
                }
        );
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
