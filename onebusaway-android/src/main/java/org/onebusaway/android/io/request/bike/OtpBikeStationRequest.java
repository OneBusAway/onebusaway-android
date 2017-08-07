/*
* Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com)
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
package org.onebusaway.android.io.request.bike;

import android.content.Context;
import android.location.Location;
import android.net.Uri;

import org.onebusaway.android.io.request.RequestBase;

import java.util.concurrent.Callable;

/**
 * Request to retrieve the bike stations from OTP server.
 *
 * Created by carvalhorr on 7/13/17.
 */
public class OtpBikeStationRequest extends RequestBase implements Callable<OtpBikeStationResponse> {


    protected OtpBikeStationRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, Location lowerLeft, Location upperRight) {
            super(context, getUrl(lowerLeft, upperRight));
            setIsOtp(true);
        }

        public OtpBikeStationRequest build() {
            return new OtpBikeStationRequest(buildUri());
        }

        private static String getUrl(Location lowerLeft, Location upperRight){
            String otpBikeStationsUrl = "routers/default/bike_rental";
            if (lowerLeft != null && upperRight != null) {
                otpBikeStationsUrl += "?lowerLeft="
                        + lowerLeft.getLatitude()
                        + ","
                        + lowerLeft.getLongitude() + "&upperRight="
                        + upperRight.getLatitude()
                        + ","
                        + upperRight.getLongitude();
            }
            return otpBikeStationsUrl;
        }
    }

    /**
     * Helper method to constructiong new instances.
     *
     * @param context The package context.
     * @param lowerLeft lower left corner of the visible map area to make the request
     * @param upperRight upper right corner of the visible map area to make the request
     * @return
     */
    public static OtpBikeStationRequest newRequest(Context context, Location lowerLeft, Location upperRight) {
        return new OtpBikeStationRequest.Builder(context, lowerLeft, upperRight).build();
    }

    @Override
    public OtpBikeStationResponse call() {
        return call(OtpBikeStationResponse.class);
    }


    @Override
    public String toString() {
        return "OtpBikeStationRequest [mUri=" + mUri + "]";
    }

}
