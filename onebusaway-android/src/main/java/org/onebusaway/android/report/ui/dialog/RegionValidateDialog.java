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
package org.onebusaway.android.report.ui.dialog;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.ReportActivity;
import org.onebusaway.android.util.PreferenceUtils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * Show to validate if the current user is in expected region
 *
 * @author Cagri Cetin
 */
public class RegionValidateDialog extends BaseReportDialogFragment {

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        StringBuilder message = new StringBuilder();
        String regionName = Application.get().getCurrentRegion().getName();
        message.append(getResources().getString(R.string.region_dialog_message, regionName));

        AlertDialog dialog =  new AlertDialog.Builder(getActivity())
                .setMessage(message.toString())
                .setPositiveButton(R.string.rt_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        long regionId = Application.get().getCurrentRegion().getId();
                        PreferenceUtils.saveLong(ReportConstants.PREF_VALIDATED_REGION_ID, regionId);
                        ((ReportActivity) getActivity()).createIssueTypeListFragment();
                    }
                })
                .setNegativeButton(R.string.rt_no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        closeSuperActivity();
                        ((ReportActivity) getActivity()).createPreferencesActivity();
                    }
                }).create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
}
