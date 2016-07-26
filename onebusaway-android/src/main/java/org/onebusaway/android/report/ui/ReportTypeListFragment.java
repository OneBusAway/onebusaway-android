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

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.ui.MaterialListAdapter;
import org.onebusaway.android.ui.MaterialListItem;
import org.onebusaway.android.util.UIUtils;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.List;

import edu.usf.cutr.open311client.Open311Manager;

/**
 * @author Cagri Cetin
 */
public class ReportTypeListFragment extends ListFragment implements AdapterView.OnItemClickListener {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.report_type_fragment, null, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);

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
        List<MaterialListItem> materialListItems = new ArrayList<>();
        for (int i = 0; i < reportTypes.length; i++) {
            //Don't show the send app feedback section if email is not defined for region
            if (!isEmailDefined && getString(R.string.rt_app_feedback).equals(reportTypes[i]))
                continue;
            MaterialListItem item = new MaterialListItem(reportTypes[i], reportDesc[i], reportIcons.getResourceId(i, -1));
            materialListItems.add(item);
        }

        MaterialListAdapter adapter = new MaterialListAdapter(getActivity(), materialListItems);
        setListAdapter(adapter);
        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);
    }

    private Boolean isEmailDefined() {
        ObaRegion region = Application.get().getCurrentRegion();
        return !(region == null || region.getContactEmail() == null);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        MaterialListItem rti = (MaterialListItem) getListView().getItemAtPosition(i);

        if (getString(R.string.rt_customer_service).equals(rti.getTitle())) {
            goToCustomerServices();

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_problem),
                    getString(R.string.analytics_label_customer_service));
        } else if (getString(R.string.rt_infrastructure_problem).equals(rti.getTitle())) {
            ((ReportActivity) getActivity()).createInfrastructureIssueActivity(
                    getString(R.string.ri_selected_service_stop));

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_problem),
                    getString(R.string.analytics_label_stop_problem));
        } else if (getString(R.string.rt_stop_problem).equals(rti.getTitle())) {
            ((ReportActivity) getActivity()).createInfrastructureIssueActivity(
                    getString(R.string.ri_selected_service_stop));

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_problem),
                    getString(R.string.analytics_label_stop_problem));
        } else if (getString(R.string.rt_arrival_problem).equals(rti.getTitle())) {
            // Report bus stop issue
            ((ReportActivity) getActivity()).createInfrastructureIssueActivity(
                    getString(R.string.ri_selected_service_trip));

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_problem),
                    getString(R.string.analytics_label_trip_problem));
        } else if (getString(R.string.rt_app_feedback).equals(rti.getTitle())) {
            // Send App feedback
            String email = getString(R.string.ri_app_feedback_email);
            String locationString = getActivity().getIntent()
                    .getStringExtra(BaseReportActivity.LOCATION_STRING);

            UIUtils.sendEmail(getActivity(), email, locationString);

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_problem),
                    getString(R.string.analytics_label_app_feedback));
            if (locationString == null) {
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_problem),
                        getString(R.string.analytics_label_app_feedback_without_location));
            }
        } else if (getString(R.string.rt_ideas).equals(rti.getTitle())) {
            // Direct to ideascale website
            goToIdeaScale();

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_problem),
                    getString(R.string.analytics_label_idea_scale));
        }
    }

    private void goToCustomerServices() {
        ((ReportActivity) getActivity()).createCustomerServiceFragment();
    }

    private void goToIdeaScale() {
        UIUtils.goToUrl(getActivity(), getString(R.string.ideascale_url));
    }

    private Boolean isOpen311Active() {
        ObaRegion currentRegion = Application.get().getCurrentRegion();
        Boolean isOpen311Active = Boolean.FALSE;

        if (currentRegion != null) {
            isOpen311Active = Open311Manager.isOpen311Exist();
        }
        return isOpen311Active;
    }
}
