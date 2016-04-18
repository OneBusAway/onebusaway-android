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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaAgencyWithCoverage;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageRequest;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageResponse;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class AgenciesFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ObaAgenciesWithCoverageResponse> {

    private ListAdapter mAdapter;

    private ObaAgenciesWithCoverageResponse mResponse;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Go to the URL
        MaterialListItem item = (MaterialListItem) mAdapter.getItem(position);
        ObaAgency agency = mResponse.getAgency(item.getId());
        if (!TextUtils.isEmpty(agency.getUrl())) {
            UIUtils.goToUrl(getActivity(), agency.getUrl());
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
        List<MaterialListItem> materialListItems = createListItems(result);
        mAdapter = new MaterialListAdapter(getContext(), materialListItems);
        setListAdapter(mAdapter);
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

    private List<MaterialListItem> createListItems(ObaAgenciesWithCoverageResponse result) {
        List<MaterialListItem> materialListItems = new ArrayList<>();
        for (int i = 0; i < result.getAgencies().length; i++) {
            ObaAgencyWithCoverage obaAgencyWithCoverage = result.getAgencies()[i];
            ObaAgency agency = result.getAgency(obaAgencyWithCoverage.getId());
            MaterialListItem item = new MaterialListItem(agency.getName(), agency.getUrl(),
                    obaAgencyWithCoverage.getId(), R.drawable.ic_maps_directions_bus);
            materialListItems.add(item);
        }
        return materialListItems;
    }
}
