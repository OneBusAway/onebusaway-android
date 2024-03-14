package org.onebusaway.android.io.request.weather;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.RequestBase;
import org.onebusaway.android.io.request.weather.models.ObaWeatherResponse;

import java.util.concurrent.Callable;


public final class ObaWeatherRequest extends RequestBase implements Callable<ObaWeatherResponse> {

    private ObaWeatherRequest(Uri uri) {
        super(uri);
    }

    public static class Builder {

        private static Uri URI = null;

        public Builder(long regionId) {
            String weatherAPIURL = Application.get().getResources().getString(R.string.weather_api_url);

            // Replacing param regionID with our current region id.
            weatherAPIURL = weatherAPIURL.replace("regionID",String.valueOf(regionId));
            URI = Uri.parse(weatherAPIURL);
        }

        public ObaWeatherRequest build() {
            return new ObaWeatherRequest(URI);
        }
    }

    public static ObaWeatherRequest newRequest(long regionId) {
        return new Builder(regionId).build();
    }

    @Override
    public ObaWeatherResponse call() {
        return call(ObaWeatherResponse.class);
    }

    @NonNull
    @Override
    public String toString() {
        return "ObaWeatherRequest [mUri=" + mUri + "]";
    }
}
