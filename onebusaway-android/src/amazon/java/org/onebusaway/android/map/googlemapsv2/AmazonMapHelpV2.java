package org.onebusaway.android.map.googlemapsv2;

import com.amazon.geo.mapsv2.util.AmazonMapsRuntimeUtil;
import com.amazon.geo.mapsv2.util.ConnectionResult;

import android.app.Activity;
import android.content.Context;

/**
 * Helper methods specific to Amazon Maps API v2
 */
public class AmazonMapHelpV2 {

    public static boolean isGoogleMapsInstalled(Context context) {
        int resultCode = AmazonMapsRuntimeUtil
                .isAmazonMapsRuntimeAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    public static void promptUserInstallGoogleMaps(final Context context) {
        if (context instanceof Activity) {
            Activity a = (Activity) context;
            int resultCode = AmazonMapsRuntimeUtil
                    .isAmazonMapsRuntimeAvailable(context);
            AmazonMapsRuntimeUtil.getErrorDialog(resultCode, a, 0).show();
        }
    }
}
