/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.request.ObaResponse;
import com.joulespersecond.oba.request.ObaRoutesForLocationRequest;
import com.joulespersecond.oba.request.ObaRoutesForLocationResponse;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import java.util.Arrays;

public class MySearchRoutesActivity extends MySearchActivity {
    private static final String RETURN_ROUTE = ".ReturnRoute";
    private static final String LOCATION_LAT = ".LocationLat";
    private static final String LOCATION_LON = ".LocationLon";
    public static final String RESULT_ROUTE_ID = ".RouteId";

    private static final String TAG = "MySearchRoutesActivity";

    public static final String TAB_NAME = "search";
    protected boolean mMapMode = false;
    protected GeoPoint mLocation = null;

    public static final Intent makeIntent(Context context, GeoPoint location) {
        Intent intent = new Intent(context, MySearchRoutesActivity.class);
        intent.putExtra(RETURN_ROUTE, true);
        if (location != null) {
            intent.putExtra(LOCATION_LAT, location.getLatitudeE6());
            intent.putExtra(LOCATION_LON, location.getLongitudeE6());
        }
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mMapMode = intent.getBooleanExtra(RETURN_ROUTE, false);
        if (mMapMode) {
            int lat = intent.getIntExtra(LOCATION_LAT, 0);
            int lon = intent.getIntExtra(LOCATION_LON, 0);
            if (lat != 0 && lon != 0) {
                mLocation = new GeoPoint(lat, lon);
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
        final String routeId = route.getId();
        final String routeName = UIHelp.getRouteDisplayName(route);

        if (isShortcutMode()) {
            Intent intent = RouteInfoActivity.makeIntent(this, routeId);
            makeShortcut(routeName, intent);
        } else if (mMapMode) {
            Intent resultData = new Intent();
            resultData.putExtra(RESULT_ROUTE_ID, routeId);
            setResult(RESULT_OK, resultData);
            finish();
        } else {
            RouteInfoActivity.start(this, routeId);
        }
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;
    private static final int CONTEXT_MENU_SHOW_URL = 3;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.short_name);
        menu.setHeaderTitle(getString(R.string.route_name, text.getText()));
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_create_shortcut);
        } else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_get_route_info);
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.my_context_showonmap);
        final String url = getUrl(getListView(), info.position);
        if (url != null) {
            menu.add(0, CONTEXT_MENU_SHOW_URL, 0, R.string.my_context_show_schedule);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_MENU_DEFAULT:
                // Fake a click
                onListItemClick(getListView(), info.targetView, info.position, info.id);
                return true;
            case CONTEXT_MENU_SHOW_ON_MAP:
                MapViewActivity.start(this, getId(getListView(), info.position));
                return true;
            case CONTEXT_MENU_SHOW_URL:
                UIHelp.goToUrl(this, getUrl(getListView(), info.position));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private String getId(ListView l, int position) {
        ListAdapter adapter = l.getAdapter();
        ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
        return route.getId();
    }

    private String getUrl(ListView l, int position) {
        ListAdapter adapter = l.getAdapter();
        ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
        return route.getUrl();
    }

    private final class SearchResultsListAdapter extends Adapters.BaseArrayAdapter2<ObaRoute> {
        public SearchResultsListAdapter(ObaResponse response) {
            super(MySearchRoutesActivity.this, Arrays
                    .asList(((ObaRoutesForLocationResponse)response).getRoutes()),
                    R.layout.route_list_item);
        }

        @Override
        protected void setData(View view, int position) {
            TextView shortNameText = (TextView)view.findViewById(R.id.short_name);
            TextView longNameText = (TextView)view.findViewById(R.id.long_name);

            ObaRoute route = mArray.get(position);
            String shortName = route.getShortName();
            String longName = MyTextUtils.toTitleCase(route.getLongName());

            if (TextUtils.isEmpty(shortName)) {
                shortName = longName;
            }
            if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
                longName = MyTextUtils.toTitleCase(route.getDescription());
            }

            shortNameText.setText(shortName);
            longNameText.setText(longName);
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.my_search_route_list;
    }

    @Override
    protected int getEditBoxHintText() {
        return R.string.search_route_hint;
    }

    @Override
    protected int getMinSearchLength() {
        return 1;
    }

    @Override
    protected void setResultsAdapter(ObaResponse response) {
        setListAdapter(new SearchResultsListAdapter(response));
    }

    @Override
    protected void setHintText() {
        mEmptyText.setText(R.string.find_hint_nofavoriteroutes);
    }

    @Override
    protected ObaResponse doFindInBackground(String routeId) {
        GeoPoint location = mLocation;
        if (location == null)
            location = UIHelp.getLocation(this);
        ObaRoutesForLocationResponse response =
            new ObaRoutesForLocationRequest.Builder(this, location)
                .setQuery(routeId)
                .build()
                .call();
        // If there is no results from the user-centered query,
        // open a wider next in some "default" Seattle/Bellevue location
        Log.d(TAG, "Server returns: " + response.getCode());
        if (response.getCode() == ObaApi.OBA_OK) {
            ObaRoute[] routes = response.getRoutes();
            if (routes.length != 0) {
                return response;
            }
        }

        return new ObaRoutesForLocationRequest.Builder(this, UIHelp.DEFAULT_SEARCH_CENTER)
                .setRadius(UIHelp.DEFAULT_SEARCH_RADIUS)
                .setQuery(routeId)
                .build()
                .call();
    }
}
