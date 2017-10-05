/*
 * Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com)
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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.map.googlemapsv2.LayerInfo;
import org.onebusaway.android.util.LayerUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import uk.co.markormesher.android_fab.SpeedDialMenuAdapter;

/**
 * Control the display of the available layers options in a speed dial when the layers Floating
 * Action Button is clicked.
 */
public class LayersSpeedDialAdapter extends SpeedDialMenuAdapter {

    private final Context context;

    /**
     * Hold information of which layers are activatedLayers
     */
    private Boolean[] activatedLayers;

    /**
     * Hold information of all available layers
     */
    private LayerInfo[] layers;

    /**
     * Listener to be called when a layer option is activatedLayers/deativated. It supports multiple.
     * Currently there is one listener added to actually add/remove the layer on the map and another
     * one to update the speed dial menu state.
     */
    private List<LayerActivationListener> layerActivationListeners = new ArrayList<>();

    public LayersSpeedDialAdapter(Context context) {
        this.context = context;
        setupLayers();
        activatedLayers = new Boolean[1];
        activatedLayers[0] = LayerUtils.isBikeshareLayerVisible();
    }

    public void addLayerActivationListener(LayerActivationListener listener) {
        layerActivationListeners.add(listener);
    }

    private void setupLayers() {
        layers = new LayerInfo[1];
        layers[0] = LayerUtils.bikeshareLayerInfo;
    }

    @Override
    protected int getCount() {
        return 1;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected MenuItem getViews(Context context, int position) {
        // Refresh active layer info
        activatedLayers[0] = LayerUtils.isBikeshareLayerVisible();

        LayerInfo layer = layers[position];
        MenuItem menuItem = new MenuItem();

        menuItem.iconDrawableId = layer.getIconDrawableId();

        // Adding a view so the color of the text match the color of the speed dial disc
        TextView label = new TextView(context);
        label.setText(layer.getLayerlabel());
        label.setTextColor(Color.WHITE);
        int labelDrawableId;
        if (activatedLayers[position]) {
            labelDrawableId = layer.getLabelBackgroundDrawableId();
        } else {
            labelDrawableId = R.drawable.speed_dial_disabled_item_label;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            label.setBackground(context.getResources().getDrawable(labelDrawableId));
        } else {
            label.setBackgroundDrawable(context.getResources().getDrawable(labelDrawableId));
        }

        menuItem.labelView = label;

        return menuItem;
    }

    @Override
    protected boolean onMenuItemClick(int position) {
        if (position < activatedLayers.length) {
            if (activatedLayers[position]) {
                for (LayerActivationListener listener : layerActivationListeners) {
                    if (listener != null) {
                        listener.onDeactivateLayer(layers[position]);
                    }
                }
            } else {
                for (LayerActivationListener listener : layerActivationListeners) {
                    if (listener != null) {
                        listener.onActivateLayer(layers[position]);
                    }
                }
            }
            activatedLayers[position] = !activatedLayers[position];
            persistSelection(position);
            return false;
        } else {
            return super.onMenuItemClick(position);
        }
    }

    /**
     * Store the layer activation state in the default shared preferences.
     * @param position position of the menu clicked
     */
    private void persistSelection(int position) {
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(context);
        sp.edit().putBoolean(layers[position].getSharedPreferenceKey(), activatedLayers[position]).apply();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected int getBackgroundColour(int position) {
        // Refresh active layer info
        activatedLayers[0] = LayerUtils.isBikeshareLayerVisible();

        int activatedColor = layers[position].getLayerColor();
        int deactivatedColor = context.getResources().getColor(R.color.layer_disabled);
        return activatedLayers[position] ?
                activatedColor : deactivatedColor;
    }

    @Override
    protected boolean rotateFab() {
        return true;
    }

    /**
     * Interface that any class wishing to respond to layer activation/deactivation must implement.
     */
    public interface LayerActivationListener {
        void onActivateLayer(LayerInfo layer);
        void onDeactivateLayer(LayerInfo layer);
    }
}
