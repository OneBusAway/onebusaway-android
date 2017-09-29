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

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

/**
 * Interface that each Overlay needs to impement in order to be notified when a marker is clicked
 * or the map is clicked.
 */

public interface MarkerListeners {

    /**
     * Triggered when the user taps on a marker
     *
     * @param marker the marker the user tapped on
     * @return true if the listener has consumed the event (i.e., the default behavior should not
     * occur); false otherwise (i.e., the default behavior should occur). The default behavior is
     * for the camera to move to the marker and an info window to appear.  See
     * https://developers.google.com/android/reference/com/google/android/gms/maps/GoogleMap.OnMarkerClickListener
     */
    boolean markerClicked(Marker marker);

    /**
     * Triggered when the user taps on the map to hide a marker
     *
     * @param latLng the location on the map where the user tapped
     */
    void removeMarkerClicked(LatLng latLng);
}
