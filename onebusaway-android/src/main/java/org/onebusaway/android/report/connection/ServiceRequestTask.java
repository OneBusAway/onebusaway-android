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

import edu.usf.cutr.open311client.Open311;
import edu.usf.cutr.open311client.models.ServiceRequest;
import edu.usf.cutr.open311client.models.ServiceRequestResponse;

/**
 * Async task used to submit Open311 issue requests
 *
 * @author Cagri Cetin
 */
public class ServiceRequestTask extends AsyncTask<Void, Integer, ServiceRequestResponse> {

    private Open311 open311;

    private ServiceRequest serviceRequest;

    private Callback callback;

    public interface Callback {
        /**
         * Called when the Open311 ServiceRequestTask is complete
         * @param response the object model contains the information from the endpoint
         */
        void onServiceRequestTaskCompleted(ServiceRequestResponse response);
    }

    public ServiceRequestTask(Open311 open311, ServiceRequest serviceRequest, Callback callback) {
        this.serviceRequest = serviceRequest;
        this.callback = callback;
        this.open311 = open311;
    }

    @Override
    protected ServiceRequestResponse doInBackground(Void... params) {
        return open311.postServiceRequest(serviceRequest);
    }

    @Override
    protected void onPostExecute(ServiceRequestResponse response) {
        callback.onServiceRequestTaskCompleted(response);
    }
}
