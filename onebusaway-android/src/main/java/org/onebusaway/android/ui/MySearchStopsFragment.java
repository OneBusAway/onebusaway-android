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
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaStopsForLocationRequest;
import org.onebusaway.android.io.request.ObaStopsForLocationResponse;
import org.onebusaway.android.util.ArrayAdapter;
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
import android.widget.ListView;
import android.widget.TextView;

import java.util.Arrays;

public class MySearchStopsFragment extends MySearchFragmentBase
        implements LoaderManager.LoaderCallbacks<ObaStopsForLocationResponse> {

    //private static final String TAG = "MySearchStopsFragment";
    private static final String QUERY_TEXT = "query_text";

    public static final String TAB_NAME = "search";

    private UIUtils.StopUserInfoMap mStopUserMap;

    private MyAdapter mAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mStopUserMap = new UIUtils.StopUserInfoMap(getActivity());
        super.onActivityCreated(savedInstanceState);

        mAdapter = new MyAdapter();
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
        return inflater.inflate(R.layout.my_search_stop_list, null);
    }

    @Override
    public void onDestroy() {
        if (mStopUserMap != null) {
            mStopUserMap.close();
        }
        super.onDestroy();
    }

    @Override
    public Loader<ObaStopsForLocationResponse> onCreateLoader(int id, Bundle args) {
        String query = args.getString(QUERY_TEXT);
        return new MyLoader(getActivity(), query, getSearchCenter());
    }

    @Override
    public void onLoadFinished(Loader<ObaStopsForLocationResponse> loader,
            ObaStopsForLocationResponse response) {
        UIUtils.showProgress(this, false);
        //Log.d(TAG, "Loader finished");
        final int code = response.getCode();
        if (code == ObaApi.OBA_OK) {
            setEmptyText(getString(R.string.find_hint_noresults));
            mAdapter.setData(Arrays.asList(response.getStops()));
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
    public void onLoaderReset(Loader<ObaStopsForLocationResponse> loader) {
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
        return R.string.search_stop_hint;
    }

    @Override
    protected int getMinSearchLength() {
        return 5;
    }

    @Override
    protected CharSequence getHintText() {
        return getText(R.string.find_hint_nofavoritestops);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ObaStop stop = (ObaStop) l.getAdapter().getItem(position - l.getHeaderViewsCount());
        ArrivalsListActivity.Builder b = new ArrivalsListActivity.Builder(getActivity(),
                stop.getId());
        b.setStopName(stop.getName());
        b.setStopDirection(stop.getDirection());

        if (isShortcutMode()) {
            final ShortcutInfoCompat shortcut = UIUtils.createStopShortcut(getContext(),
                    stop.getName(),
                    b);
            Activity activity = getActivity();
            activity.setResult(Activity.RESULT_OK, shortcut.getIntent());
            activity.finish();
        } else {
            b.setUpMode(NavHelp.UP_MODE_BACK);
            b.start();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final TextView text = (TextView) info.targetView.findViewById(R.id.stop_name);
        menu.setHeaderTitle(UIUtils.formatDisplayText(text.getText().toString()));
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_create_shortcut);
        } else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_get_stop_info);
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.my_context_showonmap);
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
                showOnMap(getListView(), info.position);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void showOnMap(ListView l, int position) {
        ObaStop stop = (ObaStop) l.getAdapter().getItem(position - l.getHeaderViewsCount());
        final String stopId = stop.getId();
        final double lat = stop.getLatitude();
        final double lon = stop.getLongitude();

        HomeActivity.start(getActivity(), stopId, lat, lon);
    }

    //
    // Adapter
    //
    private final class MyAdapter extends ArrayAdapter<ObaStop> {

        public MyAdapter() {
            super(getActivity(), R.layout.stop_list_item);
        }

        @Override
        protected void initView(View view, ObaStop stop) {
            mStopUserMap.setView(view, stop.getId(), stop.getName());
            UIUtils.setStopDirection(view.findViewById(R.id.direction),
                    stop.getDirection(),
                    true);
        }
    }

    //
    // Loader
    //
    private static final class MyLoader extends AsyncTaskLoader<ObaStopsForLocationResponse> {

        private final String mQueryText;

        private final Location mCenter;

        public MyLoader(Context context, String query, Location center) {
            super(context);
            mQueryText = query;
            mCenter = center;
        }

        @Override
        public ObaStopsForLocationResponse loadInBackground() {
            return new ObaStopsForLocationRequest.Builder(getContext(), mCenter)
                    .setQuery(mQueryText)
                    .build()
                    .call();
        }
    }
}
