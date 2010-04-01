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

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;

import android.content.Intent;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class MySearchRoutesActivity extends MySearchActivity {
    //private static final String TAG = "MySearchRoutesActivity";

    public static final String TAB_NAME = "search";

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
        final String routeId = route.getId();
        final String routeName = route.getShortName();

        if (isShortcutMode()) {
            Intent intent = RouteInfoActivity.makeIntent(this, routeId);
            makeShortcut(routeName, intent);
        }
        else {
            RouteInfoActivity.start(this, routeId);
        }
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;
    private static final int CONTEXT_MENU_SHOW_URL = 3;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.short_name);
        menu.setHeaderTitle(getString(R.string.route_name, text.getText()));
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_create_shortcut);
        }
        else {
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

    private final class SearchResultsListAdapter extends Adapters.BaseArrayAdapter<ObaRoute> {
        public SearchResultsListAdapter(ObaResponse response) {
            super(MySearchRoutesActivity.this,
                    response.getData().getRoutes(),
                    R.layout.route_list_item);
        }
        @Override
        protected void setData(View view, int position) {
            TextView shortName = (TextView)view.findViewById(R.id.short_name);
            TextView longName = (TextView)view.findViewById(R.id.long_name);

            ObaRoute route = mArray.get(position);
            shortName.setText(route.getShortName());
            longName.setText(route.getLongNameOrDescription());
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
        ObaResponse response = ObaApi.getRoutesByLocation(this,
                UIHelp.getLocation(this), 0, routeId);
        // If there is no results from the user-centered query,
        // open a wider next in some "default" Seattle/Bellevue location
        //Log.d(TAG, "Server returns: " + response.getCode());
        if (response.getCode() == ObaApi.OBA_OK) {
            ObaArray<ObaRoute> routes = response.getData().getRoutes();
            if (routes.length() != 0) {
                return response;
            }
        }

        return ObaApi.getRoutesByLocation(this, UIHelp.DEFAULT_SEARCH_CENTER,
                UIHelp.DEFAULT_SEARCH_RADIUS,
                routeId);
    }
}
