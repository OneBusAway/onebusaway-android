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
package org.onebusaway.android.report.ui;

import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.util.PreferenceHelp;

import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Toast;

import edu.usf.cutr.open311client.models.Open311User;

/**
 * @author Cagri Cetin
 */
public class BaseReportFragment extends Fragment {

    /**
     * findViewById wrapper on Fragment
     *
     * @param id view id
     * @return view
     */
    protected View findViewById(int id) {
        return getActivity().findViewById(id);
    }

    protected Open311User getOpen311User() {
        return new Open311User(PreferenceHelp.getString(ReportConstants.PREF_NAME), PreferenceHelp.getString(ReportConstants.PREF_LASTNAME),
                PreferenceHelp.getString(ReportConstants.PREF_EMAIL), PreferenceHelp.getString(ReportConstants.PREF_PHONE));
    }

    protected void createToastMessage(String message) {
        Toast.makeText(getActivity().getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }
}