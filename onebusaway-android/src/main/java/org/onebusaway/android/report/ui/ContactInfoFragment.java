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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.onebusaway.android.R;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.open311.models.Open311User;
import org.onebusaway.android.util.PreferenceHelp;

/**
 * Show contact information for Open311 endpoint
 * @author Cagri Cetin
 */
public class ContactInfoFragment extends BaseReportFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.report_issue_contact_info, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            setUpViews();
        }
    }

    private void setUpViews() {
        Open311User open311User = getOpen311User();
        if (open311User.getName() != null)
            ((EditText) findViewById(R.id.rici_name_editText)).setText(open311User.getName());
        if (open311User.getLastName() != null)
            ((EditText) findViewById(R.id.rici_lastname_editText)).setText(open311User.getLastName());
        if (open311User.getEmail() != null)
            ((EditText) findViewById(R.id.rici_email_editText)).setText(open311User.getEmail());
        if (open311User.getPhone() != null)
            ((EditText) findViewById(R.id.rici_phone_editText)).setText(open311User.getPhone());

        findViewById(R.id.rici_button_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveOpen311User();
            }
        });
    }

    private void saveOpen311User() {
        String name = ((EditText) findViewById(R.id.rici_name_editText)).getText().toString();
        String lastName = ((EditText) findViewById(R.id.rici_lastname_editText)).getText().toString();
        String email = ((EditText) findViewById(R.id.rici_email_editText)).getText().toString();
        String phone = ((EditText) findViewById(R.id.rici_phone_editText)).getText().toString();

        PreferenceHelp.saveString(ReportConstants.PREF_NAME, name);
        PreferenceHelp.saveString(ReportConstants.PREF_LASTNAME, lastName);
        PreferenceHelp.saveString(ReportConstants.PREF_EMAIL, email);
        PreferenceHelp.saveString(ReportConstants.PREF_PHONE, phone);

        getActivity().onBackPressed();
    }

}
