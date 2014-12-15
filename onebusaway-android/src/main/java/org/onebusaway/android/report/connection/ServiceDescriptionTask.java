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
import org.onebusaway.android.report.open311.models.Service;
import org.onebusaway.android.report.open311.models.ServiceDescription;
import org.onebusaway.android.report.open311.models.ServiceListRequest;

/**
 * Async task for getting service description of the given Open311 service
 * @author Cagri Cetin
 */
public class ServiceDescriptionTask extends AsyncTask<Void, Integer, ServiceDescription> {

    private Location location;

    private Service service;

    private Callback callback;

    public interface Callback{
        /**
         * Called when the Open311 ServiceDescriptionTask is complete
         * @param serviceDescription contains the detailed information of the given service
         */
        void onServiceDescriptionTaskCompleted(ServiceDescription serviceDescription);
    }

    public ServiceDescriptionTask(Location location, Service service, Callback callback) {
        this.location = location;
        this.service = service;
        this.callback = callback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected ServiceDescription doInBackground(Void... params) {
        String jurisdictionId = Application.get().getCurrentRegion().getOpen311JurisdictionId();
        Open311 open311 = Open311Manager.getOpen311ByJurisdiction(jurisdictionId);
        ServiceListRequest slr = new ServiceListRequest(location.getLatitude(), location.getLatitude());
        slr.setServiceCode(service.getService_code());
        return open311.getServiceDescription(slr);
    }

    @Override
    protected void onPostExecute(ServiceDescription serviceDescription) {
        callback.onServiceDescriptionTaskCompleted(serviceDescription);
    }
}
