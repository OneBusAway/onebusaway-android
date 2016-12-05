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

import android.os.AsyncTask;

import java.util.List;

import edu.usf.cutr.open311client.Open311;
import edu.usf.cutr.open311client.Open311Manager;
import edu.usf.cutr.open311client.models.ServiceListRequest;
import edu.usf.cutr.open311client.models.ServiceListResponse;

/**
 * Async task for getting Open311 services
 *
 * @author Cagri Cetin
 */
public class ServiceListTask extends AsyncTask<Void, Integer, ServiceListResponse> {

    private ServiceListRequest mServiceListRequest;

    private List<Open311> open311List;

    private Open311 mOpen311;

    private Callback callback;

    public interface Callback {
        /**
         * Called when the Open311 ServicesTask is complete
         *
         * @param services contains the information of the Services
         */
        void onServicesTaskCompleted(ServiceListResponse services, Open311 open311);
    }


    public ServiceListTask(ServiceListRequest serviceListRequest, List<Open311> open311List,
                           Callback callback) {
        this.mServiceListRequest = serviceListRequest;
        this.open311List = open311List;
        this.callback = callback;
    }

    @Override
    protected ServiceListResponse doInBackground(Void... params) {
        for (int i = 0; i < open311List.size(); i++) {
            this.mOpen311 = open311List.get(i);
            mServiceListRequest.setJurisdictionId(mOpen311.getJurisdiction());
            ServiceListResponse slr = mOpen311.getServiceList(mServiceListRequest);
            if (i + 1 == open311List.size()) {
                // if this is the last open311 endpoint return this one
                return slr;
            } else if (slr != null && slr.isSuccess() &&
                    Open311Manager.isAreaManagedByOpen311(slr.getServiceList())) {
                // if this area maintained by this open311 then return
                return slr;
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(ServiceListResponse services) {
        callback.onServicesTaskCompleted(services, mOpen311);
    }
}
