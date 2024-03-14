package org.onebusaway.android.io.test;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertNotNull;

import android.content.ContentResolver;
import android.net.Uri;

import org.junit.Test;
import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.request.ObaRegionsRequest;
import org.onebusaway.android.io.request.ObaRegionsResponse;
import org.onebusaway.android.io.request.weather.ObaWeatherRequest;
import org.onebusaway.android.io.request.weather.models.ObaWeatherResponse;


public class RegionWeatherTest {
    @Test
    public void testRequest() {

        final Uri.Builder builder = new Uri.Builder();
        builder.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE);
        builder.authority(getTargetContext().getPackageName());
        builder.path(String.valueOf(R.raw.regions_v3));

        ObaRegionsRequest regionsRequest = ObaRegionsRequest.newRequest(getTargetContext(), builder.build());
        ObaRegionsResponse regionsResponse = regionsRequest.call();

        final ObaRegion[] list = regionsResponse.getRegions();

        for (ObaRegion region : list) {
            ObaWeatherRequest weatherRequest= ObaWeatherRequest.newRequest(region.getId());
            ObaWeatherResponse weatherResponse = weatherRequest.call();
            assertNotNull(weatherResponse);
        }

    }

}
