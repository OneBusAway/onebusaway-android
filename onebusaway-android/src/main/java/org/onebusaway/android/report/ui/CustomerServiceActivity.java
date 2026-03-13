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
package org.onebusaway.android.report.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.PlausibleAnalytics;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaAgencyWithCoverage;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageRequest;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageResponse;
import org.onebusaway.android.util.ArrayAdapter;
import org.onebusaway.android.util.UIUtils;

import java.util.Arrays;

/**
 * Migrated from CustomerServiceDialog to an Activity to be more user-friendly.
 */
public class CustomerServiceActivity extends BaseReportActivity
        implements LoaderManager.LoaderCallbacks<ObaAgenciesWithCoverageResponse> {

    private static final int AGENCIES_LOADER_ID = 0;

    private ListView mListView;
    private ObaAgenciesWithCoverageResponse mResponse;
    private Adapter mAdapter;
    private FirebaseAnalytics mFirebaseAnalytics;

    // entry point for starting this activity, optionally with a location string to include in email reports
    public static void start(Context context, Intent sourceIntent) {
        Intent intent = new Intent(context, CustomerServiceActivity.class);

        if (sourceIntent != null && sourceIntent.hasExtra(LOCATION_STRING)) {
            intent.putExtra(
                    LOCATION_STRING,
                    sourceIntent.getStringExtra(LOCATION_STRING)
            );
        }

        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.report_issue_customer_service);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setTitle(R.string.rt_customer_service);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mListView = findViewById(R.id.list);

        LoaderManager.getInstance(this).initLoader(AGENCIES_LOADER_ID, null, this);
    }

    @Override
    public Loader<ObaAgenciesWithCoverageResponse> onCreateLoader(int id, Bundle args) {
        return new AgenciesLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<ObaAgenciesWithCoverageResponse> loader,
                               ObaAgenciesWithCoverageResponse data) {
        if (data == null || data.getAgencies() == null) {
            createToastMessage(getString(R.string.generic_comm_error));
            return;
        }

        mResponse = data;
        mAdapter = new Adapter(this);
        mListView.setAdapter(mAdapter);
        mAdapter.setData(Arrays.asList(data.getAgencies()));
    }

    @Override
    public void onLoaderReset(Loader<ObaAgenciesWithCoverageResponse> loader) {
        if (mListView != null) {
            mListView.setAdapter(null);
        }
        mAdapter = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Adapter for displaying agencies with contact options
     */
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
            phoneButton.setColorFilter(
                    getResources().getColor(R.color.navdrawer_icon_tint_selected)
            );

            ImageButton webButton = view.findViewById(R.id.ricsi_web);
            webButton.setColorFilter(
                    getResources().getColor(R.color.navdrawer_icon_tint_selected)
            );

            ImageButton emailButton = view.findViewById(R.id.ricsi_email);
            emailButton.setColorFilter(
                    getResources().getColor(R.color.navdrawer_icon_tint_selected)
            );

            if (TextUtils.isEmpty(agency.getEmail())) {
                emailButton.setVisibility(View.INVISIBLE);
            } else {
                emailButton.setVisibility(View.VISIBLE);
                emailButton.setOnClickListener(v -> {
                    String locationString = getIntent().getStringExtra(LOCATION_STRING);

                    UIUtils.sendEmail(CustomerServiceActivity.this, agency.getEmail(), locationString);

                    ObaAnalytics.reportUiEvent(
                            mFirebaseAnalytics,
                            Application.get().getPlausibleInstance(),
                            PlausibleAnalytics.REPORT_MORE_EVENT_URL,
                            agency.getName() + "_" + getString(R.string.analytics_customer_service),
                            getString(R.string.analytics_label_customer_service_email)
                    );

                    if (locationString == null) {
                        ObaAnalytics.reportUiEvent(
                                mFirebaseAnalytics,
                                Application.get().getPlausibleInstance(),
                                PlausibleAnalytics.REPORT_MORE_EVENT_URL,
                                agency.getName() + "_" + getString(R.string.analytics_customer_service),
                                getString(R.string.analytics_label_customer_service_email_without_location)
                        );
                    }
                });
            }

            if (TextUtils.isEmpty(agency.getUrl())) {
                webButton.setVisibility(View.INVISIBLE);
            } else {
                webButton.setVisibility(View.VISIBLE);
                webButton.setOnClickListener(v -> {
                    UIUtils.goToUrl(CustomerServiceActivity.this, agency.getUrl());
                    ObaAnalytics.reportUiEvent(
                            mFirebaseAnalytics,
                            Application.get().getPlausibleInstance(),
                            PlausibleAnalytics.REPORT_MORE_EVENT_URL,
                            agency.getName() + "_" + getString(R.string.analytics_customer_service),
                            getString(R.string.analytics_label_customer_service_web)
                    );
                });
            }

            if (TextUtils.isEmpty(agency.getPhone())) {
                phoneButton.setVisibility(View.INVISIBLE);
            } else {
                phoneButton.setVisibility(View.VISIBLE);
                phoneButton.setOnClickListener(v -> {
                    UIUtils.goToPhoneDialer(CustomerServiceActivity.this, "tel:" + agency.getPhone());
                    ObaAnalytics.reportUiEvent(
                            mFirebaseAnalytics,
                            Application.get().getPlausibleInstance(),
                            PlausibleAnalytics.REPORT_MORE_EVENT_URL,
                            agency.getName() + "_" + getString(R.string.analytics_customer_service),
                            getString(R.string.analytics_label_customer_service_phone)
                    );
                });
            }
        }
    }

    /**
     * Loader for fetching agencies with coverage in the background
     */
    private static final class AgenciesLoader
            extends AsyncTaskLoader<ObaAgenciesWithCoverageResponse> {

        AgenciesLoader(Context context) {
            super(context);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public ObaAgenciesWithCoverageResponse loadInBackground() {
            return ObaAgenciesWithCoverageRequest.newRequest(getContext()).call();
        }
    }
}
