/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
 * and individual contributors.
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaElement;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaRoutesForLocationRequest;
import org.onebusaway.android.io.request.ObaRoutesForLocationResponse;
import org.onebusaway.android.io.request.ObaStopsForLocationRequest;
import org.onebusaway.android.io.request.ObaStopsForLocationResponse;
import org.onebusaway.android.util.ArrayAdapter;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This implements the response that returns both stops and routes in one list.
 *
 * @author paulw
 */
final class SearchResponse {

    private final int mCode;

    private final List<ObaElement> mResults;

    SearchResponse(int code, List<ObaElement> r) {
        mCode = code;
        mResults = r;
    }

    int getCode() {
        return mCode;
    }

    List<ObaElement> getResults() {
        return mResults;
    }
}

public class SearchResultsFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<SearchResponse> {

    //private static final String TAG = "SearchResultsFragment";
    public static final String QUERY_TEXT = "query_text";

    private MyAdapter mAdapter;

    /**
     * GoogleApiClient being used for Location Services
     */
    GoogleApiClient mGoogleApiClient;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        if (api.isGooglePlayServicesAvailable(getActivity())
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(getActivity());
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAdapter = new MyAdapter(getActivity());
        setListAdapter(mAdapter);

        search();
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);
        // Make sure GoogleApiClient is connected, if available
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onStop() {
        // Tear down GoogleApiClient
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    private void search() {
        UIUtils.showProgress(this, true);
        Loader<?> loader = getLoaderManager().restartLoader(0, getArguments(), this);
        //loader.onContentChanged();
        loader.forceLoad();
    }

    @Override
    public Loader<SearchResponse> onCreateLoader(int id, Bundle args) {
        String query = args.getString(QUERY_TEXT);
        Location location = Application.getLastKnownLocation(getActivity(), mGoogleApiClient);
        if (location == null) {
            location = LocationUtils.getDefaultSearchCenter();
        }
        return new MyLoader(getActivity(), query, location);
    }

    @Override
    public void onLoadFinished(Loader<SearchResponse> loader,
            SearchResponse response) {
        UIUtils.showProgress(this, false);
        //Log.d(TAG, "Loader finished");
        final int code = response.getCode();
        if (code == ObaApi.OBA_OK) {
            setEmptyText(getString(R.string.find_hint_noresults));
            mAdapter.setData(response.getResults());
        } else if (code != 0) {
            // If we get anything other than a '0' error, that means
            // the server actually returned something to us,
            // (even if it was an error) so we shouldn't show
            // a 'communication' error. Just fake no results.
            setEmptyText(getString(R.string.find_hint_noresults));
        } else {
            setEmptyText(getString(R.string.generic_comm_error));
        }
    }

    @Override
    public void onLoaderReset(Loader<SearchResponse> loader) {
        mAdapter.clear();
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ListAdapter adapter = l.getAdapter();
        ObaElement e = (ObaElement) adapter.getItem(position - l.getHeaderViewsCount());
        if (e instanceof ObaRoute) {
            clickRoute((ObaRoute) e);
        } else if (e instanceof ObaStop) {
            clickStop((ObaStop) e);
        }
    }

    private void clickRoute(ObaRoute route) {
        final String routeId = route.getId();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(UIUtils.getRouteDescription(route));

        builder.setItems(R.array.search_route_options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        // Show on list
                        RouteInfoActivity.start(getActivity(), routeId);
                        break;
                    case 1:
                        // Show on map
                        HomeActivity.start(getActivity(), routeId);
                        break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    private void clickStop(final ObaStop stop) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(UIUtils.formatDisplayText(stop.getName()));

        builder.setItems(R.array.search_stop_options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        ArrivalsListActivity.start(getActivity(), stop.getId());
                        break;
                    case 1:
                        HomeActivity.start(getActivity(),
                                stop.getId(),
                                stop.getLatitude(),
                                stop.getLongitude());
                        break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();

    }

    //
    // Adapter
    //
    private static final class MyAdapter extends ArrayAdapter<ObaElement> {

        public MyAdapter(Context context) {
            super(context, R.layout.route_list_item);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            LayoutInflater inflater = getLayoutInflater();

            ObaElement elem = getItem(position);
            // The layout we use depends on the element...
            // so unfortunately we can't actually re-use views...
            if (elem instanceof ObaRoute) {
                view = inflater.inflate(R.layout.route_list_item, parent, false);
                UIUtils.setRouteView(view, (ObaRoute) elem);
            } else if (elem instanceof ObaStop) {
                view = inflater.inflate(R.layout.stop_list_item, parent, false);
                initStop(view, (ObaStop) elem);
            } else {
                view = null;
            }
            return view;
        }

        private void initStop(View view, ObaStop stop) {
            TextView nameView = (TextView) view.findViewById(R.id.stop_name);
            nameView.setText(UIUtils.formatDisplayText(stop.getName()));

            UIUtils.setStopDirection(view.findViewById(R.id.direction),
                    stop.getDirection(),
                    true);
        }

        @Override
        protected void initView(View view, ObaElement t) {
            throw new AssertionError("Should never be called");
        }
    }

    //
    // Loader
    //
    private static final class MyLoader extends AsyncTaskLoader<SearchResponse> {

        private final String mQueryText;

        private final Location mCenter;

        public MyLoader(Context context, String query, Location center) {
            super(context);
            mQueryText = query;
            mCenter = center;
        }

        private ObaRoutesForLocationResponse getRoutes() {
            ObaRoutesForLocationResponse response =
                    new ObaRoutesForLocationRequest.Builder(getContext(), mCenter)
                            .setRadius(LocationUtils.DEFAULT_SEARCH_RADIUS)
                            .setQuery(mQueryText)
                            .build()
                            .call();
            // If there is no results from the user-centered query,
            // open a wider next in some "default" location
            //Log.d(TAG, "Server returns: " + response.getCode());
            if (response.getCode() == ObaApi.OBA_OK) {
                ObaRoute[] routes = response.getRoutesForLocation();
                if (routes.length != 0) {
                    return response;
                }
            }
            Location center = LocationUtils.getDefaultSearchCenter();
            if (center != null) {
                return new ObaRoutesForLocationRequest.Builder(getContext(), center)
                        .setRadius(LocationUtils.DEFAULT_SEARCH_RADIUS)
                        .setQuery(mQueryText)
                        .build()
                        .call();
            }
            // I suppose we just return what was there...
            return response;

        }

        private ObaStopsForLocationResponse getStops() {
            return new ObaStopsForLocationRequest.Builder(getContext(), mCenter)
                    .setRadius(LocationUtils.DEFAULT_SEARCH_RADIUS)
                    .setQuery(mQueryText)
                    .build()
                    .call();
        }

        @Override
        public SearchResponse loadInBackground() {
            ArrayList<ObaElement> results = new ArrayList<ObaElement>();

            ObaRoutesForLocationResponse routes = getRoutes();
            ObaStopsForLocationResponse stops = getStops();

            int routeCode = routes.getCode();
            int stopCode = stops.getCode();
            int code = ObaApi.OBA_OK;

            // if neither of them are OK, return one of them.
            if (routeCode != ObaApi.OBA_OK && stopCode != ObaApi.OBA_OK) {
                code = routeCode;
            }

            if (code == ObaApi.OBA_OK) {
                results.addAll(Arrays.asList(routes.getRoutesForLocation()));
                results.addAll(Arrays.asList(stops.getStops()));
            }

            return new SearchResponse(code, results);
        }
    }
}
