/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com) and individual contributors.
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
package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.elements.ObaAgency;
import com.joulespersecond.oba.elements.ObaAgencyWithCoverage;
import com.joulespersecond.oba.request.ObaAgenciesWithCoverageRequest;
import com.joulespersecond.oba.request.ObaAgenciesWithCoverageResponse;
import com.joulespersecond.seattlebusbot.util.UIHelp;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Arrays;

public class AgenciesFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ObaAgenciesWithCoverageResponse> {

    private Adapter mAdapter;

    private ObaAgenciesWithCoverageResponse mResponse;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Go to the URL
        ObaAgency agency = mResponse.getAgency(mAdapter.getItem(position).getId());
        if (!TextUtils.isEmpty(agency.getUrl())) {
            UIHelp.goToUrl(getActivity(), agency.getUrl());
        }
    }

    @Override
    public Loader<ObaAgenciesWithCoverageResponse> onCreateLoader(int id, Bundle args) {
        return new AgenciesLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<ObaAgenciesWithCoverageResponse> l,
            ObaAgenciesWithCoverageResponse result) {
        // Create our generic adapter
        mResponse = result;
        mAdapter = new Adapter(getActivity());
        setListAdapter(mAdapter);
        mAdapter.setData(Arrays.asList(result.getAgencies()));
    }

    @Override
    public void onLoaderReset(Loader<ObaAgenciesWithCoverageResponse> l) {
        setListAdapter(null);
        mAdapter = null;
    }

    //
    // Loader
    //
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

    //
    // Adapter
    //
    private class Adapter extends ArrayAdapter<ObaAgencyWithCoverage> {

        Adapter(Context context) {
            super(context, android.R.layout.simple_list_item_2);
        }

        @Override
        protected void initView(View view, ObaAgencyWithCoverage coverage) {
            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            TextView text2 = (TextView) view.findViewById(android.R.id.text2);

            ObaAgency agency = mResponse.getAgency(coverage.getId());
            text1.setText(agency.getName());
            text2.setText(agency.getUrl());
        }
    }
}
