/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com)
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
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.request.ObaRoutesForLocationRequest;
import org.onebusaway.android.io.request.ObaRoutesForLocationResponse;
import org.onebusaway.android.util.ArrayAdapter;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Arrays;

public class MySearchRoutesFragment extends MySearchFragmentBase
        implements LoaderManager.LoaderCallbacks<ObaRoutesForLocationResponse> {

    //private static final String TAG = "MySearchRoutesActivity";
    private static final String QUERY_TEXT = "query_text";

    public static final String TAB_NAME = "search";

    private MyAdapter mAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAdapter = new MyAdapter(getActivity());
        setListAdapter(mAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup root,
            Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        return inflater.inflate(R.layout.my_search_route_list, null);
    }

    @Override
    public Loader<ObaRoutesForLocationResponse> onCreateLoader(int id, Bundle args) {
        String query = args.getString(QUERY_TEXT);
        return new MyLoader(getActivity(), query, getSearchCenter());
    }

    @Override
    public void onLoadFinished(Loader<ObaRoutesForLocationResponse> loader,
            ObaRoutesForLocationResponse response) {
        UIUtils.showProgress(this, false);
        //Log.d(TAG, "Loader finished");
        final int code = response.getCode();
        if (code == ObaApi.OBA_OK) {
            setEmptyText(getString(R.string.find_hint_noresults));
            mAdapter.setData(Arrays.asList(response.getRoutesForLocation()));
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
    public void onLoaderReset(Loader<ObaRoutesForLocationResponse> loader) {
        mAdapter.clear();
    }

    //
    // Base class
    //
    @Override
    protected void doSearch(String text) {
        UIUtils.showProgress(this, true);
        Bundle args = new Bundle();
        args.putString(QUERY_TEXT, text);
        Loader<?> loader = getLoaderManager().restartLoader(0, args, this);
        loader.onContentChanged();
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
    protected CharSequence getHintText() {
        return getString(R.string.find_hint_nofavoriteroutes);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        ObaRoute route = (ObaRoute) adapter.getItem(position - l.getHeaderViewsCount());
        final String routeId = route.getId();
        final String routeName = UIUtils.getRouteDisplayName(route);

        if (isShortcutMode()) {
            final ShortcutInfoCompat shortcut = UIUtils.createRouteShortcut(getContext(), routeId, routeName);
            Activity activity = getActivity();
            activity.setResult(Activity.RESULT_OK, shortcut.getIntent());
            activity.finish();
        } else {
            RouteInfoActivity.start(getActivity(), routeId);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final TextView text = (TextView) info.targetView.findViewById(R.id.short_name);
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
        menu.add(0, CONTEXT_MENU_CREATE_SHORTCUT, 0,
                R.string.my_context_create_shortcut);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_MENU_DEFAULT:
                // Fake a click
                onListItemClick(getListView(), info.targetView, info.position, info.id);
                return true;
            case CONTEXT_MENU_SHOW_ON_MAP:
                HomeActivity.start(getActivity(), getId(getListView(), info.position));
                return true;
            case CONTEXT_MENU_SHOW_URL:
                UIUtils.goToUrl(getActivity(), getUrl(getListView(), info.position));
                return true;
            case CONTEXT_MENU_CREATE_SHORTCUT:
                String id = QueryUtils.RouteList.getId(getListView(), info.position);
                String shortName = QueryUtils.RouteList.getShortName(getListView(), info.position);
                UIUtils.createRouteShortcut(getContext(), id, shortName);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private String getId(ListView l, int position) {
        ListAdapter adapter = l.getAdapter();
        ObaRoute route = (ObaRoute) adapter.getItem(position - l.getHeaderViewsCount());
        return route.getId();
    }

    private String getUrl(ListView l, int position) {
        ListAdapter adapter = l.getAdapter();
        ObaRoute route = (ObaRoute) adapter.getItem(position - l.getHeaderViewsCount());
        return route.getUrl();
    }

    //
    // Adapter
    //
    private static final class MyAdapter extends ArrayAdapter<ObaRoute> {

        public MyAdapter(Context context) {
            super(context, R.layout.route_list_item);
        }

        @Override
        protected void initView(View view, ObaRoute route) {
            UIUtils.setRouteView(view, route);
        }
    }

    //
    // Loader
    //
    private static final class MyLoader extends AsyncTaskLoader<ObaRoutesForLocationResponse> {

        private final String mQueryText;

        private final Location mCenter;

        public MyLoader(Context context, String query, Location center) {
            super(context);
            mQueryText = query;
            mCenter = center;
        }

        @Override
        public ObaRoutesForLocationResponse loadInBackground() {
            ObaRoutesForLocationResponse response =
                    new ObaRoutesForLocationRequest.Builder(getContext(), mCenter)
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
            return response;
        }
    }
}
