package org.onebusaway.android.io.request.weather;

import org.onebusaway.android.io.request.weather.models.ObaWeatherResponse;

public interface WeatherRequestListener {
    void onWeatherResponseReceived(ObaWeatherResponse response);
    void onWeatherRequestFailed();
}