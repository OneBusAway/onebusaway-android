/*
* Copyright (C) Sean J. Barbeau (sjbarbeau@gmail.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.onebusaway.android.map.bike;

import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;

import org.onebusaway.android.map.MapModeController.Callback;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import java.util.List;

/**
 * Callbacks to respond to BikeStationLoader callbacks.
 */

public class BikeLoaderCallbacks implements LoaderManager.LoaderCallbacks<List<BikeRentalStation>>,
        Loader.OnLoadCompleteListener<List<BikeRentalStation>> {

    public static final int BIKE_STATION_LOADER_ID = 6523;

    private static final String TAG = "BikeLoaderCallback";
    private Callback mapFragment;

    public BikeLoaderCallbacks(Callback mapFragment) {
        this.mapFragment = mapFragment;
    }

    @Override
    public BikeStationLoader onCreateLoader(int id, Bundle args) {
        return new BikeStationLoader(mapFragment.getActivity(),
                mapFragment.getSouthWest(),
                mapFragment.getNorthEast());
    }

    @Override
    public void onLoadFinished(Loader<List<BikeRentalStation>> loader,
                               List<BikeRentalStation> response) {
        if (response != null) {
            mapFragment.showBikeStations(response);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<BikeRentalStation>> loader) {
        mapFragment.getMapView().removeRouteOverlay();
        mapFragment.getMapView().removeVehicleOverlay();
    }

    @Override
    public void onLoadComplete(Loader<List<BikeRentalStation>> loader,
                               List<BikeRentalStation> response) {
        onLoadFinished(loader, response);
    }
}
