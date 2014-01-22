package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.elements.ObaAgency;
import com.joulespersecond.oba.elements.ObaAgencyWithCoverage;
import com.joulespersecond.oba.request.ObaAgenciesWithCoverageRequest;
import com.joulespersecond.oba.request.ObaAgenciesWithCoverageResponse;

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
