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
package org.onebusaway.android.map.googlemapsv2;

/**
 * Interface to hold information about a layer that can be activated on the map.
 *
 * This information is used to show layer option when the Floating Action Button layers is clicked.
 * The options are displayed in a speed dial.
 */

public interface LayerInfo {

    /**
     * @return Label of the layer
     */
    String getLayerlabel();

    /**
     * @return drawable id for label to be used as background. It should be the same background
     * color as the layer color.
     */
    int getLabelBackgroundDrawableId();

    /**
     * @return Icon drawable to display in the speed dial option.
     */
    int getIconDrawableId();

    /**
     * Color of the speed dial option background and label text.
     *
     * @return color
     */
    int getLayerColor();

    /**
     * Key to store the activation in shared preferences.
     *
     * @return unique shared preferences key
     */
    String getSharedPreferenceKey();
}
