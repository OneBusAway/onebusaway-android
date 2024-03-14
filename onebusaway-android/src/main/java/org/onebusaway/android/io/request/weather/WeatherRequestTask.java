package org.onebusaway.android.io.request.weather;

import android.os.AsyncTask;
import android.util.Log;

import org.onebusaway.android.io.request.weather.models.ObaWeatherResponse;

public class WeatherRequestTask extends AsyncTask<ObaWeatherRequest, Void, ObaWeatherResponse> {
    private static final String TAG = "Weather Request";
    private WeatherRequestListener mListener;

    public WeatherRequestTask(WeatherRequestListener listener) {
        mListener = listener;
    }

    @Override
    protected ObaWeatherResponse doInBackground(ObaWeatherRequest... requests) {
        try {
            return requests[0].call();
        } catch (Exception e) {
            Log.e(TAG, "Error executing weather request", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(ObaWeatherResponse response) {
        if (response != null) {
            mListener.onWeatherResponseReceived(response);
        } else {
            mListener.onWeatherRequestFailed();
        }
    }
}