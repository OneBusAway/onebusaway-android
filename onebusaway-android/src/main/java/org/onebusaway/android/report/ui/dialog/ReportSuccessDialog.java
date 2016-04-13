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
package org.onebusaway.android.report.ui.dialog;

import org.onebusaway.android.report.ui.InfrastructureIssueActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;

import edu.usf.cutr.open311client.constants.Open311Constants;

/**
 * Show if the report submission is successful
 *
 * @author Cagri Cetin
 */
public class ReportSuccessDialog extends BaseReportDialogFragment {

    public static final String TAG = "ReportSuccessDialog";

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setMessage(Open311Constants.M_REPORT_SUCCESS)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        closeSuperActivity();
                    }
                }).create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void closeSuperActivity() {
        ((InfrastructureIssueActivity) getActivity()).finishActivityWithResult();
    }
}
