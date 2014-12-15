/*
* Copyright (C) 2015 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.ui.adapter;

import edu.usf.cutr.open311client.models.Service;

public class ServiceSpinnerItem implements SpinnerItem {

    private Service mService;

    public ServiceSpinnerItem(Service service) {
        mService = service;
    }

    private boolean isHint = false;

    public Service getService() {
        return mService;
    }

    public void setHint(boolean isHint) {
        this.isHint = isHint;
    }

    @Override
    public boolean isSection() {
        return false;
    }

    @Override
    public boolean isHint() {
        return isHint;
    }
}
