/*
* Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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

import android.location.Location;
import android.os.AsyncTask;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.report.open311.Open311;
import org.onebusaway.android.report.open311.Open311Manager;
import org.onebusaway.android.report.open311.models.ServiceListRequest;
import org.onebusaway.android.report.open311.models.ServiceListResponse;

/**
 * Async task for getting Open311 services
 * @author Cagri Cetin
 */
public class ServicesTask extends AsyncTask<Void, Integer, ServiceListResponse> {

    private Location location;

    private Callback callback;

    public interface Callback{
        /**
         *  Called when the Open311 ServicesTask is complete
         * @param services contains the information of the Services
         */
        void onServicesTaskCompleted(ServiceListResponse services);
    }


    public ServicesTask(Location location, Callback callback) {
        this.location = location;
        this.callback = callback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected ServiceListResponse doInBackground(Void... params) {
        String jurisdictionId = Application.get().getCurrentRegion().getOpen311JurisdictionId();
        Open311 open311 = Open311Manager.getOpen311ByJurisdiction(jurisdictionId);

        if (location == null) {
            return open311.getServiceList();
        } else {
            return open311.getServiceList(new ServiceListRequest(location.getLatitude(), location.getLongitude()));
        }
    }

    @Override
    protected void onPostExecute(ServiceListResponse services) {
        callback.onServicesTaskCompleted(services);
    }
}
