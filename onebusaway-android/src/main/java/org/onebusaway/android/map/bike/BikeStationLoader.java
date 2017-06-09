package org.onebusaway.android.map.bike;

import android.content.Context;
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
 * Created by carvalhorr on 6/5/17.
 */

public class BikeStationLoader extends AsyncTaskLoader<List<BikeRentalStation>> {

    public BikeStationLoader(Context context) {
        super(context);
    }

    @Override
    public List<BikeRentalStation> loadInBackground() {
        BikeRentalStationList list = null;
        try {
            System.out.println("Custom OTP: " + Application.get().getCustomOtpApiUrl());
            System.out.println("Base OTP: " + Application.get().getCurrentRegion().toString());
            String otpBaseUrl = Application.get().getCustomOtpApiUrl();
            if (otpBaseUrl == null || otpBaseUrl == "") {
                otpBaseUrl = Application.get().getCurrentRegion().getOtpBaseUrl();
            }
            URL otpBikeStationsUrl = new URL( otpBaseUrl + "routers/default/bike_rental/");

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

    public void update() {
        onContentChanged();
    }

}
