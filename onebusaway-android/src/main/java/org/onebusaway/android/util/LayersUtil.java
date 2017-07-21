package org.onebusaway.android.util;

import android.os.Build;
import android.text.TextUtils;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.map.googlemapsv2.LayerInfo;
import org.onebusaway.android.map.googlemapsv2.bike.BikeStationOverlay;

/**
 * Created by carvalhorr on 7/21/17.
 */

public class LayersUtil {


    /**
     * Information necessary to create Speed Dial menu on the Layers FAB.
     * @return
     */
    public static final LayerInfo bikeshareLayerInfo = new LayerInfo() {
        @Override
        public String getLayerlabel() {
            return Application.get().getString(R.string.layers_speedial_bikeshare_label);
        }

        @Override
        public int getLabelBackgroundDrawableId() {
            return R.drawable.speed_dial_bikeshare_item_label;
        }

        @Override
        public int getIconDrawableId() {
            return R.drawable.ic_directions_bike_white;
        }


        @Override
        public int getLayerColor() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Application.get().getColor(R.color.layer_bikeshare_color);
            } else {
                //noinspection deprecation
                return Application.get().getResources().getColor(R.color.layer_bikeshare_color);
            }
        }

        @Override
        public String getSharedPreferenceKey() {
            return Application.get().getString(R.string.preference_key_layer_bikeshare_visible);
        }
    };


    private static boolean isLayerActive(String layerName) {
        // Bike layer is active if it is activated in the preferences and either the current region
        // supports it or a custom otp url is set. The custom otp url is used to make the testing
        // process easier
        if (bikeshareLayerInfo.getLayerlabel().equals(layerName)) {
            return Application.getPrefs().getBoolean(
                    Application.get().getString(R.string.preference_key_layer_bikeshare_activated), true)
                    && ((Application.get().getCurrentRegion() != null
                        && Application.get().getCurrentRegion().getSupportsOtpBikeshare())
                    || !TextUtils.isEmpty(Application.get().getCustomOtpApiUrl()));
        } else {
            return false;
        }
    }

    public static boolean isBikeshareLayerActive() {
        return isLayerActive(bikeshareLayerInfo.getLayerlabel());
    }
}
