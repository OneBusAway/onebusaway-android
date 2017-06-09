package org.onebusaway.android.map.bike;

import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;

import org.onebusaway.android.map.MapModeController.Callback;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import java.util.List;

/**
 * Created by carvalhorr on 6/4/17.
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
    public BikeStationLoader onCreateLoader(int id,
                                            Bundle args) {
        return new BikeStationLoader(mapFragment.getActivity());
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
