/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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

import com.google.android.gms.maps.model.Marker;

import org.onebusaway.android.extrapolation.data.TripState;
import org.onebusaway.android.io.elements.ObaTripStatus;

import kotlinx.coroutines.flow.StateFlow;

/**
 * Per-vehicle display state on the main map view. One instance per tracked
 * trip.
 * Trip-level data (history, schedule, extrapolation) lives on {@link TripState}
 * snapshots read from {@link #tripFlow}; this holds only display/animation state.
 */
class VehicleMarkerState {

    final StateFlow<TripState> tripFlow;
    ObaTripStatus status;
    boolean animating;
    boolean selected;
    /**
     * The trip anchor we last animated to. Reference-compared to detect new data.
     */
    ObaTripStatus lastAnimatedAnchor;

    VehicleIconParams iconParams;

    Marker vehicleMarker;
    Marker dataReceivedMarker;
    long dataReceivedFixTime;

    VehicleMarkerState(StateFlow<TripState> tripFlow, ObaTripStatus status) {
        this.tripFlow = tripFlow;
        this.status = status;
        this.lastAnimatedAnchor = tripFlow.getValue().getAnchor();
    }
}
