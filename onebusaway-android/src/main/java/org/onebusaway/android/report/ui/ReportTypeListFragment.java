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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.report.open311.Open311Manager;
import org.onebusaway.android.report.ui.adapter.ReportTypeListAdapter;
import org.onebusaway.android.report.ui.model.ReportTypeItem;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Cagri Cetin
 */
public class ReportTypeListFragment extends ListFragment implements AdapterView.OnItemClickListener {

    private String locationString;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.report_type_fragment, null, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        locationString = getActivity().getIntent().getStringExtra("mLocationClient");

        String reportTypes[];
        String reportDesc[];
        TypedArray reportIcons;

        if (isOpen311Active()) {
            reportTypes = getResources().getStringArray(R.array.report_types);
            reportDesc = getResources().getStringArray(R.array.report_types_desc);
            reportIcons = getResources().obtainTypedArray(R.array.report_types_icons);
        } else {
            reportTypes = getResources().getStringArray(R.array.report_types_without_open311);
            reportDesc = getResources().getStringArray(R.array.report_types_desc_without_open311);
            reportIcons = getResources().obtainTypedArray(R.array.report_types_icons_without_open311);
        }

        Boolean isEmailDefined = isEmailDefined();
        List<ReportTypeItem> reportTypeItems = new ArrayList<ReportTypeItem>();
        for (int i = 0; i < reportTypes.length; i++) {
            //Don't show the send app feedback section if email is not defined for region
            if (!isEmailDefined && getString(R.string.rt_app_feedback).equals(reportTypes[i]))
                continue;
            ReportTypeItem item = new ReportTypeItem(reportTypes[i], reportDesc[i], reportIcons.getResourceId(i, -1));
            reportTypeItems.add(item);
        }

        ReportTypeListAdapter adapter = new ReportTypeListAdapter(getActivity(), reportTypeItems);
        setListAdapter(adapter);
        getListView().setOnItemClickListener(this);
    }

    private Boolean isEmailDefined() {
        ObaRegion region = Application.get().getCurrentRegion();
        return !(region == null || region.getContactEmail() == null);
    }

    private void goToContactEmail(Context ctxt) {
        PackageManager pm = ctxt.getPackageManager();
        PackageInfo appInfo;
        try {
            appInfo = pm.getPackageInfo(ctxt.getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        ObaRegion region = Application.get().getCurrentRegion();
        if (region == null) {
            return;
        }

        final String body = ctxt.getString(R.string.bug_report_body,
                appInfo.versionName,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                locationString);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.putExtra(Intent.EXTRA_EMAIL,
                new String[]{region.getContactEmail()});
        send.putExtra(Intent.EXTRA_SUBJECT,
                ctxt.getString(R.string.bug_report_subject));
        send.putExtra(Intent.EXTRA_TEXT, body);
        send.setType("message/rfc822");
        try {
            ctxt.startActivity(Intent.createChooser(send,
                    ctxt.getString(R.string.bug_report_subject)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctxt, R.string.bug_report_error, Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        ReportTypeItem rti = (ReportTypeItem) getListView().getItemAtPosition(i);
        if (getString(R.string.rt_infrastructure_problem).equals(rti.getTitle())) {
            ((ReportActivity) getActivity()).createInfrastructureIssueActivity();
        } else if (getString(R.string.rt_stop_problem).equals(rti.getTitle())) {
            ((ReportActivity) getActivity()).createBusStopTutorialFragment();
        } else if (getString(R.string.rt_arrival_problem).equals(rti.getTitle())) {
            //Report bus stop issue
            ((ReportActivity) getActivity()).showProgress(Boolean.TRUE);
            ((ReportActivity) getActivity()).createBusStopTutorialFragment();
        } else if (getString(R.string.rt_app_feedback).equals(rti.getTitle())) {
            //Send App feedback
            goToContactEmail(getActivity());
        } else if (getString(R.string.rt_ideas).equals(rti.getTitle())) {
            //Direct to ideascale website
            goToIdeaScale();
        }
    }

    private void goToIdeaScale() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.ideascale_url)));
        startActivity(intent);
    }

    private Boolean isOpen311Active() {
        ObaRegion currentRegion = Application.get().getCurrentRegion();
        Boolean isOpen311Active = Boolean.FALSE;

        if (currentRegion != null) {
            String jurisdictionId = currentRegion.getOpen311JurisdictionId();
            isOpen311Active = Open311Manager.isOpen311Active(jurisdictionId);
        }
        return isOpen311Active;
    }
}
