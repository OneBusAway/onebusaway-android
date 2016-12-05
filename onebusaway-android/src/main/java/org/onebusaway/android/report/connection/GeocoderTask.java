/*
* Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.connection;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;

import java.util.List;
import java.util.Locale;

public class GeocoderTask extends AsyncTask<Void, Integer, String> {

    private Callback mCallback;

    private Location mLocation;

    private Context mContext;

    public interface Callback {

        /**
         * Called when the GeocoderTask is complete
         *
         * @param address the address string from given location
         */
        void onGeocoderTaskCompleted(String address);
    }

    public GeocoderTask(Callback callback, Location location, Context context) {
        this.mCallback = callback;
        this.mLocation = location;
        this.mContext = context;
    }

    @Override
    protected String doInBackground(Void... voids) {
        String address = "";
        try {
            Geocoder geo = new Geocoder(mContext, Locale.getDefault());
            List<Address> addresses = geo.getFromLocation(mLocation.getLatitude(),
                    mLocation.getLongitude(), 1);
            if (!addresses.isEmpty() && addresses.size() > 0) {
                StringBuilder sb = new StringBuilder();
                int addressLine = addresses.get(0).getMaxAddressLineIndex();
                for (int i = 0; i < addressLine - 1; i++) {
                    sb.append(addresses.get(0).getAddressLine(i)).append(", ");
                }
                sb.append(addresses.get(0).getAddressLine(addressLine - 1)).append(".");
                address = sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return address;
    }

    @Override
    protected void onPostExecute(String s) {
        mCallback.onGeocoderTaskCompleted(s);
    }
}