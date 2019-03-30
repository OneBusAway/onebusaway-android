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

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaAgencyWithCoverage;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageRequest;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageResponse;
import org.onebusaway.android.report.ui.BaseReportActivity;
import org.onebusaway.android.util.ArrayAdapter;
import org.onebusaway.android.util.UIUtils;

import java.util.Arrays;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

public class CustomerServiceDialog extends DialogFragment implements
        LoaderManager.LoaderCallbacks<ObaAgenciesWithCoverageResponse> {

    private ListView mListView;

    private ObaAgenciesWithCoverageResponse mResponse;

    private Adapter mAdapter;

    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(getActivity());

        getLoaderManager().initLoader(0, null, this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.report_issue_customer_service, null, false);

        mListView = view.findViewById(R.id.list);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        return view;
    }

    @Override
    public Loader<ObaAgenciesWithCoverageResponse> onCreateLoader(int id, Bundle args) {
        return new AgenciesLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<ObaAgenciesWithCoverageResponse> loader, ObaAgenciesWithCoverageResponse data) {
        // Create our generic adapter
        mResponse = data;
        mAdapter = new Adapter(getActivity());
        mListView.setAdapter(mAdapter);
        //Process the list check phone & email
        mAdapter.setData(Arrays.asList(data.getAgencies()));
    }

    @Override
    public void onLoaderReset(Loader<ObaAgenciesWithCoverageResponse> loader) {
        mListView.setAdapter(null);
        mAdapter = null;
    }

    private class Adapter extends ArrayAdapter<ObaAgencyWithCoverage> {

        Adapter(Context context) {
            super(context, R.layout.report_issue_customer_service_item);
        }

        @Override
        protected void initView(View view, ObaAgencyWithCoverage coverage) {
            final ObaAgency agency = mResponse.getAgency(coverage.getId());

            TextView text1 = view.findViewById(R.id.ricsi_text);
            text1.setText(agency.getName());

            ImageButton phoneButton = view.findViewById(R.id.ricsi_phone);
            phoneButton.setColorFilter(getActivity().getResources().
                    getColor(R.color.navdrawer_icon_tint_selected));

            ImageButton webButton = view.findViewById(R.id.ricsi_web);
            webButton.setColorFilter(getActivity().getResources().
                    getColor(R.color.navdrawer_icon_tint_selected));

            ImageButton emailButton = view.findViewById(R.id.ricsi_email);
            emailButton.setColorFilter(getActivity().getResources().
                    getColor(R.color.navdrawer_icon_tint_selected));

            if (TextUtils.isEmpty(agency.getEmail())) {
                emailButton.setVisibility(View.INVISIBLE);
            } else {
                emailButton.setVisibility(View.VISIBLE);
                emailButton.setOnClickListener(view13 -> {
                    String locationString = getActivity().getIntent().
                            getStringExtra(BaseReportActivity.LOCATION_STRING);

                    UIUtils.sendEmail(getActivity(), agency.getEmail(), locationString);

                    ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                            agency.getName() + "_" + getString(R.string.analytics_customer_service),
                            getString(R.string.analytics_label_customer_service_email));
                    if (locationString == null) {
                        ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                                agency.getName() + "_" + getString(R.string.analytics_customer_service),
                                getString(R.string.analytics_label_customer_service_email_without_location));
                    }
                });
            }

            if (TextUtils.isEmpty(agency.getUrl())) {
                webButton.setVisibility(View.INVISIBLE);
            } else {
                webButton.setVisibility(View.VISIBLE);
                webButton.setOnClickListener(view1 -> {
                    UIUtils.goToUrl(getActivity(), agency.getUrl());
                    ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                            agency.getName() + "_" + getString(R.string.analytics_customer_service),
                            getString(R.string.analytics_label_customer_service_web));

                });
            }

            if (TextUtils.isEmpty(agency.getPhone())) {
                phoneButton.setVisibility(View.INVISIBLE);
            } else {
                phoneButton.setVisibility(View.VISIBLE);
                phoneButton.setOnClickListener(view12 -> {
                    UIUtils.goToPhoneDialer(getActivity(), "tel:" + agency.getPhone());
                    ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                            agency.getName() + "_" + getString(R.string.analytics_customer_service),
                            getString(R.string.analytics_label_customer_service_phone));
                });
            }
        }
    }

    private final static class AgenciesLoader
            extends AsyncTaskLoader<ObaAgenciesWithCoverageResponse> {

        AgenciesLoader(Context context) {
            super(context);
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }

        @Override
        public ObaAgenciesWithCoverageResponse loadInBackground() {
            return ObaAgenciesWithCoverageRequest.newRequest(getContext()).call();
        }
    }
}
