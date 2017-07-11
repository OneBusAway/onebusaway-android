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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.TextView;

import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.map.googlemapsv2.LayerInfo;
import org.onebusaway.android.map.googlemapsv2.bike.BikeStationOverlay;

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
     * Hold information of which layers are activated
     */
    private Boolean[] activated;

    /**
     * Hold information of all available layers
     */
    private LayerInfo[] layers;

    /**
     * Listener to be called when a layer option is activated/deativated. It supports multiple.
     * Currently there is one listener added to actually add/remove the layer on the map and another
     * one to update the speed dial menu state.
     */
    private List<LayerActivationListener> layerActivationListeners = new ArrayList<>();

    public LayersSpeedDialAdapter(Context context) {
        this.context = context;
        setupLayers();
        setupActivated();
    }

    public void addLayerActicationListener(LayerActivationListener listener) {
        layerActivationListeners.add(listener);
    }

    private void setupLayers() {
        BaseMapFragment f;
        layers = new LayerInfo[1];
        layers[0] = BikeStationOverlay.layerInfo;
    }

    private void setupActivated() {
        activated = new Boolean[1];
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        boolean isBikeSelected = sp.getBoolean(layers[0].getSharedPreferenceKey(), false);
        activated[0] = isBikeSelected;
    }

    @Override
    protected int getCount() {
        return 1;
    }

    @Override
    protected MenuItem getViews(Context context, int position) {

        LayerInfo layer = layers[position];
        MenuItem menuItem = new MenuItem();

        menuItem.iconDrawableId = activated[position] ? layer.getSelectedDrawableId() : layer.getUnselectedDrawableId();
        ;

        // Adding a view so the color of the text match the color of the speed dial disc
        TextView label = new TextView(context);
        label.setText(layer.getLayerlabel());
        label.setTextColor(layer.getLayerColor());
        menuItem.labelView = label;

        return menuItem;
    }

    @Override
    protected boolean onMenuItemClick(int position) {
        if (position < activated.length) {
            if (activated[position]) {
                for(LayerActivationListener layerActivationListener: layerActivationListeners) {
                    layerActivationListener.onDeactivateLayer(layers[position]);
                }
            } else {
                for(LayerActivationListener layerActivationListener: layerActivationListeners) {
                    layerActivationListener.onActivateLayer(layers[position]);
                }
            }
            activated[position] = !activated[position];
            persistSelection(position);
            return true;
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
        sp.edit().putBoolean(layers[position].getSharedPreferenceKey(), activated[position]).apply();
    }

    @Override
    protected int getBackgroundColour(int position) {
        return layers[position].getLayerColor();
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
    }}
