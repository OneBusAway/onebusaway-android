package org.onebusaway.android.map.bike;

import android.content.Context;
import android.location.Location;
import android.support.v4.content.AsyncTaskLoader;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;
import org.opentripplanner.routing.bike_rental.BikeRentalStationList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Class responsible for loading the list of bike stations and floating bikes from OpenTripPlanner.
 * OpenTripPlanner accept parameters lowerLeft and upperRight.
 * Google Maps work with southwest and northeast. loweLeft is equivalent to southeast and upperRight is equivalent to northeast.
 * <p>
 * This class external interface accept the parameters as found in Google Maps. Internally it maps it to OTP parameters.
 *
 * Created by carvalhorr on 6/5/17.
 */

public class BikeStationLoader extends AsyncTaskLoader<List<BikeRentalStation>> {

    private Location lowerLeft, upperRight;

    /**
     *
     * @param context
     * @param southWest southwest corner on the map in lat long
     * @param northEast northeast corner on the map in lat long
     */
    public BikeStationLoader(Context context, Location southWest, Location northEast) {
        super(context);
        updateCoordinates(southWest, northEast);
    }

    @Override
    public List<BikeRentalStation> loadInBackground() {
        BikeRentalStationList list = null;
        try {
            String otpBaseUrl = Application.get().getCustomOtpApiUrl();
            if (otpBaseUrl == null || otpBaseUrl == "") {
                otpBaseUrl = Application.get().getCurrentRegion().getOtpBaseUrl();
            }

            URL otpBikeStationsUrl = new URL(otpBaseUrl + "routers/default/bike_rental?lowerLeft="
                    + lowerLeft.getLatitude()
                    + ","
                    + lowerLeft.getLongitude()
                    + "&upperRight="
                    + upperRight.getLatitude()
                    + ","
                    + upperRight.getLongitude());

            HttpURLConnection connection = (HttpURLConnection) otpBikeStationsUrl.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("GET");

            connection.getResponseCode();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            ObaApi.SerializationHandler handler = ObaApi.getSerializer(BikeRentalStationList.class);
            list = handler.deserialize(in, BikeRentalStationList.class);
            in.close();
            connection.disconnect();
        } catch (MalformedURLException e) {
            //TODO Check the right return type here
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return list.stations;
    }

    @Override
    public void deliverResult(List<BikeRentalStation> data) {
        //mResponse = data;
        super.deliverResult(data);
    }

    @Override
    public void onStartLoading() {
        forceLoad();
    }

    public void update(Location southWest, Location northEast) {
        updateCoordinates(southWest, northEast);
        onContentChanged();
    }

    private void updateCoordinates(Location southWest, Location northEast) {
        this.lowerLeft = southWest;
        this.upperRight = northEast;
    }

}
