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
import org.onebusaway.android.report.ui.ReportActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Show to validate if the current user is in expected region
 *
 * @author Cagri Cetin
 */
public class RegionValidateDialog extends DialogFragment {

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        StringBuilder message = new StringBuilder();
        String regionName = Application.get().getCurrentRegion().getName();
        message.append(getResources().getString(R.string.region_dialog_Message2, regionName)).append("?");

        return new AlertDialog.Builder(getActivity())
                // Set Dialog Icon
                .setIcon(R.drawable.ic_action_alert_error)
                .setTitle(R.string.region_dialog_title)
                .setMessage(message.toString())

                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ((ReportActivity) getActivity()).createIssueTypeListFragment();
                    }
                })

                .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        closeSuperActivity();
                        ((ReportActivity) getActivity()).createPreferencesActivity();
                    }
                }).create();
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface dialog,
                                 int keyCode, android.view.KeyEvent event) {
                if ((keyCode == android.view.KeyEvent.KEYCODE_BACK)) {
                    closeSuperActivity();
                }
                return false;
            }
        });
    }

    private void closeSuperActivity() {
        getActivity().finish();
    }
}
