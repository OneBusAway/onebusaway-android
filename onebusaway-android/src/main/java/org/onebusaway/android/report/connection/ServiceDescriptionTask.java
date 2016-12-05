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
import edu.usf.cutr.open311client.models.ServiceDescription;
import edu.usf.cutr.open311client.models.ServiceDescriptionRequest;

/**
 * Async task for getting service description of the given Open311 service
 *
 * @author Cagri Cetin
 */
public class ServiceDescriptionTask extends AsyncTask<Void, Integer, ServiceDescription> {

    private ServiceDescriptionRequest mServiceDescriptionRequest;

    private Open311 mOpen311;

    private Callback callback;

    public interface Callback {
        /**
         * Called when the Open311 ServiceDescriptionTask is complete
         *
         * @param serviceDescription contains the detailed information of the given service
         */
        void onServiceDescriptionTaskCompleted(ServiceDescription serviceDescription);
    }

    public ServiceDescriptionTask(ServiceDescriptionRequest serviceDescriptionRequest,
                                  Open311 open311, Callback callback) {
        this.callback = callback;
        this.mServiceDescriptionRequest = serviceDescriptionRequest;
        this.mOpen311 = open311;
    }

    @Override
    protected ServiceDescription doInBackground(Void... params) {
        return mOpen311.getServiceDescription(mServiceDescriptionRequest);
    }

    @Override
    protected void onPostExecute(ServiceDescription serviceDescription) {
        callback.onServiceDescriptionTaskCompleted(serviceDescription);
    }
}
