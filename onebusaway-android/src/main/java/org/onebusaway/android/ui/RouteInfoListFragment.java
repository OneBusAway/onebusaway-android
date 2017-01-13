/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com)
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
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaStopGroup;
import org.onebusaway.android.io.elements.ObaStopGrouping;
import org.onebusaway.android.io.request.ObaRouteRequest;
import org.onebusaway.android.io.request.ObaRouteResponse;
import org.onebusaway.android.io.request.ObaStopsForRouteRequest;
import org.onebusaway.android.io.request.ObaStopsForRouteResponse;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIUtils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteInfoListFragment extends ListFragment {

    private static final String TAG = "RouteInfoListFragment";

    private static final int ROUTE_INFO_LOADER = 0;

    private static final int ROUTE_STOPS_LOADER = 1;

    private String mRouteId;

    private ObaRouteResponse mRouteInfo;

    private StopsForRouteInfo mStopsForRoute;

    private SimpleExpandableListAdapter mAdapter;

    private final RouteLoaderCallback mRouteCallback = new RouteLoaderCallback();

    private final StopsLoaderCallback mStopsCallback = new StopsLoaderCallback();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);
        registerForContextMenu(getListView());

        // Start out with a progress indicator.
        setListShown(false);

        // Initialize the expandable list
        ExpandableListView list = (ExpandableListView) getListView();
        list.setOnChildClickListener(mChildClick);

        // Get the route ID from the "uri" argument
        Uri uri = (Uri) getArguments().getParcelable(FragmentUtils.URI);
        if (uri == null) {
            Log.e(TAG, "No URI in arguments");
            return;
        }
        mRouteId = uri.getLastPathSegment();

        getLoaderManager().initLoader(ROUTE_INFO_LOADER, null, mRouteCallback);
        getLoaderManager().initLoader(ROUTE_STOPS_LOADER, null, mStopsCallback);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        return inflater.inflate(R.layout.route_info, null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.route_info_options, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean hasUrl = false;
        if (mRouteInfo != null) {
            hasUrl = !TextUtils.isEmpty(mRouteInfo.getUrl());
        }
        menu.findItem(R.id.goto_url).setEnabled(hasUrl).setVisible(hasUrl);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.show_on_map) {
            HomeActivity.start(getActivity(), mRouteId);
            return true;
        } else if (id == R.id.goto_url) {
            UIUtils.goToUrl(getActivity(), mRouteInfo.getUrl());
            return true;
        }
        return false;
    }

    private final ExpandableListView.OnChildClickListener mChildClick =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent,
                        View v,
                        int groupPosition,
                        int childPosition,
                        long id) {
                    showArrivals(v);
                    return true;
                }
            };

    private static final int CONTEXT_MENU_DEFAULT = 1;

    private static final int CONTEXT_MENU_SHOWONMAP = 2;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        ExpandableListView.ExpandableListContextMenuInfo info
                = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
        if (ExpandableListView.getPackedPositionType(info.packedPosition)
                != ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            return;
        }
        final TextView text = (TextView) info.targetView.findViewById(R.id.name);
        menu.setHeaderTitle(text.getText());
        menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.route_info_context_get_stop_info);
        menu.add(0, CONTEXT_MENU_SHOWONMAP, 0, R.string.route_info_context_showonmap);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        ExpandableListView.ExpandableListContextMenuInfo info
                = (ExpandableListView.ExpandableListContextMenuInfo) item
                .getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_MENU_DEFAULT:
                showArrivals(info.targetView);
                return true;
            case CONTEXT_MENU_SHOWONMAP:
                showOnMap(info.targetView);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);
    }

    private void showArrivals(View v) {
        final TextView text = (TextView) v.findViewById(R.id.stop_id);
        final String stopId = (String) text.getText();
        ObaStop stop = null;
        if (mStopsForRoute != null) {
            stop = mStopsForRoute.getStopMap().get(stopId);
        }
        ArrivalsListActivity.Builder b = new ArrivalsListActivity.Builder(getActivity(), stopId);
        if (stop != null) {
            b.setStopName(stop.getName());
            b.setStopDirection(stop.getDirection());
        }
        b.setUpMode(NavHelp.UP_MODE_BACK);
        b.start();
    }

    private void showOnMap(View v) {
        final TextView text = (TextView) v.findViewById(R.id.stop_id);
        final String stopId = (String) text.getText();
        // we need to find this route in the response because
        // we need to know it's lat/lon
        ObaStop stop = mStopsForRoute.getStopMap().get(stopId);
        if (stop == null) {
            return;
        }
        HomeActivity.start(getActivity(), stopId, stop.getLatitude(), stop.getLongitude());
    }

    //
    // Loader callbacks
    // This is simply because of our two loaders have different callback types,
    // so we can't easily implement them directly in the fragment.
    //
    private final class RouteLoaderCallback
            implements LoaderManager.LoaderCallbacks<ObaRouteResponse> {

        @Override
        public Loader<ObaRouteResponse> onCreateLoader(int id, Bundle args) {
            return new RouteInfoLoader(getActivity(), mRouteId);
        }

        @Override
        public void onLoadFinished(Loader<ObaRouteResponse> loader,
                ObaRouteResponse data) {
            setHeader(data, true);
        }

        @Override
        public void onLoaderReset(Loader<ObaRouteResponse> loader) {
            // Nothing to do right here...
        }
    }

    private final class StopsLoaderCallback
            implements LoaderManager.LoaderCallbacks<StopsForRouteInfo> {

        @Override
        public Loader<StopsForRouteInfo> onCreateLoader(int id, Bundle args) {
            return new StopsForRouteLoader(getActivity(), mRouteId);
        }

        @Override
        public void onLoadFinished(Loader<StopsForRouteInfo> loader,
                StopsForRouteInfo data) {
            setStopsForRoute(data);
        }

        @Override
        public void onLoaderReset(Loader<StopsForRouteInfo> loader) {
            // Nothing to do...
        }

    }


    //
    // Loader
    //
    private final static class RouteInfoLoader extends AsyncTaskLoader<ObaRouteResponse> {

        private final String mRouteId;

        RouteInfoLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }

        @Override
        public ObaRouteResponse loadInBackground() {
            return ObaRouteRequest.newRequest(getContext(), mRouteId).call();
        }
    }

    private final static class StopsForRouteLoader extends AsyncTaskLoader<StopsForRouteInfo> {

        private final String mRouteId;

        StopsForRouteLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }

        @Override
        public StopsForRouteInfo loadInBackground() {
            final ObaStopsForRouteResponse response =
                    new ObaStopsForRouteRequest.Builder(getContext(), mRouteId)
                            .setIncludeShapes(false)
                            .build()
                            .call();
            return new StopsForRouteInfo(getContext(), response);
        }
    }

    private final static class StopsForRouteInfo {

        private final int mResultCode;

        private final ArrayList<HashMap<String, String>> mStopGroups;

        private final ArrayList<ArrayList<HashMap<String, String>>> mStops;

        private final HashMap<String, ObaStop> mStopMap;

        public StopsForRouteInfo(Context cxt, ObaStopsForRouteResponse response) {
            mStopGroups = new ArrayList<HashMap<String, String>>();
            mStops = new ArrayList<ArrayList<HashMap<String, String>>>();
            mStopMap = new HashMap<String, ObaStop>();
            mResultCode = response.getCode();
            initMaps(cxt, response);
        }

        private static Map<String, ObaStop> getStopMap(List<ObaStop> stops) {
            final int len = stops.size();
            HashMap<String, ObaStop> result = new HashMap<String, ObaStop>(len);
            for (int i = 0; i < len; ++i) {
                ObaStop stop = stops.get(i);
                result.put(stop.getId(), stop);
            }
            return result;
        }

        private void initMaps(Context cxt, ObaStopsForRouteResponse response) {
            // Convert to weird array type. From the documentation of
            // SimpleExpandableListAdapter:
            // StopGroupings: A List of Maps. Each entry in the List corresponds
            // to one group in the list. The Maps contain the data for each
            // group,
            // and should include all the entries specified in "groupFrom".
            //
            // StopGroupings: A List of Maps. Each entry in the List corresponds
            // to one group in the list. The Maps contain the data for each
            // group,
            // and should include all the entries specified in "groupFrom".
            //
            // Stops:
            // A List of List of Maps. Each entry in the outer List corresponds
            // to a group (index by group position), each entry in the inner
            // List
            // corresponds to a child within the group (index by child
            // position),
            // and the Map corresponds to the data for a child (index by values
            // in the childFrom array). The Map contains the data for each
            // child,
            // and should include all the entries specified in "childFrom"
            if (response.getCode() == ObaApi.OBA_OK) {
                final List<ObaStop> stops = response.getStops();
                final Map<String, ObaStop> stopMap = getStopMap(stops);
                final ObaStopGrouping[] groupings = response.getStopGroupings();
                final int groupingsLen = groupings.length;

                for (int groupingIndex = 0; groupingIndex < groupingsLen; ++groupingIndex) {
                    final ObaStopGrouping grouping = groupings[groupingIndex];
                    final ObaStopGroup[] groups = grouping.getStopGroups();
                    final int groupsLen = groups.length;

                    for (int i = 0; i < groupsLen; ++i) {
                        final HashMap<String, String> groupMap = new HashMap<String, String>(1);
                        final ObaStopGroup group = groups[i];
                        // We can initialize the stop grouping values.
                        groupMap.put("name", UIUtils.formatDisplayText(group.getName()));
                        // Add this to the groupings map

                        // Create the sub list (the list of stops in the group)
                        final String[] stopIds = group.getStopIds();
                        final int stopIdLen = stopIds.length;

                        final ArrayList<HashMap<String, String>> childList
                                = new ArrayList<HashMap<String, String>>(
                                stopIdLen);

                        for (int j = 0; j < stopIdLen; ++j) {
                            final String stopId = stopIds[j];
                            final ObaStop stop = stopMap.get(stopId);
                            HashMap<String, String> groupStopMap = new HashMap<String, String>(2);
                            if (stop != null) {
                                groupStopMap.put("name", UIUtils.formatDisplayText(stop.getName()));
                                String dir = cxt.getString(UIUtils.getStopDirectionText(stop
                                        .getDirection()));
                                groupStopMap.put("direction", dir);
                                groupStopMap.put("id", stopId);
                                mStopMap.put(stopId, stop);
                            } else {
                                groupStopMap.put("name", "");
                                groupStopMap.put("direction", "");
                                groupStopMap.put("id", stopId);
                            }
                            childList.add(groupStopMap);
                        }

                        mStopGroups.add(groupMap);
                        mStops.add(childList);
                    }
                }
            }
        }

        public int getResultCode() {
            return mResultCode;
        }

        public ArrayList<HashMap<String, String>> getStopGroups() {
            return mStopGroups;
        }

        public ArrayList<ArrayList<HashMap<String, String>>> getStops() {
            return mStops;
        }

        public HashMap<String, ObaStop> getStopMap() {
            return mStopMap;
        }
    }

    //
    // Helper functions
    //
    private void setHeader(ObaRouteResponse routeInfo, boolean addToDb) {
        mRouteInfo = routeInfo;
        View view = getView();

        if (routeInfo.getCode() == ObaApi.OBA_OK) {
            TextView shortNameText = (TextView) view.findViewById(R.id.short_name);
            TextView longNameText = (TextView) view.findViewById(R.id.long_name);
            TextView agencyText = (TextView) view.findViewById(R.id.agency);
            String url = mRouteInfo.getUrl();

            String shortName = routeInfo.getShortName();
            String longName = routeInfo.getLongName();

            if (TextUtils.isEmpty(shortName)) {
                shortName = longName;
            }
            if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
                longName = routeInfo.getDescription();
            }

            shortNameText.setText(UIUtils.formatDisplayText(shortName));
            longNameText.setText(UIUtils.formatDisplayText(longName));
            agencyText.setText(mRouteInfo.getAgency().getName());

            if (addToDb) {
                ContentValues values = new ContentValues();
                values.put(ObaContract.Routes.SHORTNAME, shortName);
                values.put(ObaContract.Routes.LONGNAME, longName);
                values.put(ObaContract.Routes.URL, url);
                if (Application.get().getCurrentRegion() != null) {
                    values.put(ObaContract.Routes.REGION_ID,
                            Application.get().getCurrentRegion().getId());
                }
                ObaContract.Routes.insertOrUpdate(getActivity(), mRouteInfo.getId(), values, true);
            }
        } else {
            setEmptyText(UIUtils.getRouteErrorString(getActivity(), routeInfo.getCode()));
        }
    }

    private void setStopsForRoute(StopsForRouteInfo result) {
        mStopsForRoute = result;
        final int code = mStopsForRoute.getResultCode();
        if (code == ObaApi.OBA_OK) {
            setEmptyText("");
        } else {
            setEmptyText(UIUtils.getRouteErrorString(getActivity(), code));
        }
        mAdapter = new SimpleExpandableListAdapter(
                getActivity(),
                result.getStopGroups(),
                android.R.layout.simple_expandable_list_item_1,
                new String[]{"name"},
                new int[]{android.R.id.text1},
                result.getStops(),
                R.layout.route_info_listitem,
                new String[]{"name", "direction", "id"},
                new int[]{R.id.name, R.id.direction, R.id.stop_id}
        );
        setListAdapter(mAdapter);
    }

    public void setListAdapter(SimpleExpandableListAdapter adapter) {
        ExpandableListView list = (ExpandableListView) getListView();

        if (list != null) {
            list.setAdapter(adapter);
            setListShown(true);
        }
    }
}
