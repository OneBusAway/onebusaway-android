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
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaStopsForLocationRequest;
import com.joulespersecond.oba.request.ObaStopsForLocationResponse;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItem;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Arrays;

public class MySearchStopsFragment extends MySearchFragmentBase
            implements LoaderManager.LoaderCallbacks<ObaStopsForLocationResponse> {
    private static final String TAG = "MySearchStopsFragment";
    private static final String QUERY_TEXT = "query_text";

    public static final String TAB_NAME = "search";

    private UIHelp.StopUserInfoMap mStopUserMap;
    private MyAdapter mAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mStopUserMap = new UIHelp.StopUserInfoMap(getActivity());
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
        Log.d(TAG, "Create loader");
        String query = args.getString(QUERY_TEXT);
        return new MyLoader(getActivity(), query, getSearchCenter());
    }

    @Override
    public void onLoadFinished(Loader<ObaStopsForLocationResponse> loader,
                        ObaStopsForLocationResponse response) {
        ((FragmentActivity)getActivity()).setProgressBarIndeterminateVisibility(Boolean.FALSE);
        Log.d(TAG, "Loader finished");
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
        ((FragmentActivity)getActivity()).setProgressBarIndeterminateVisibility(Boolean.TRUE);
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

    private static final String URL_STOPID = HomeActivity.HELP_URL + "#finding_stop_ids";

    @Override
    protected CharSequence getHintText() {
        final CharSequence first = getText(R.string.find_hint_nofavoritestops);
        final int firstLen = first.length();
        final CharSequence second = getText(R.string.find_hint_nofavoritestops_link);

        SpannableStringBuilder builder = new SpannableStringBuilder(first);
        builder.append(second);
        builder.setSpan(new URLSpan(URL_STOPID), firstLen, firstLen+second.length(), 0);
        return builder;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ObaStop stop = (ObaStop)l.getAdapter().getItem(position - l.getHeaderViewsCount());
        final String stopId = stop.getId();
        final String stopName = stop.getName();
        final String stopDir = stop.getDirection();
        final String shortcutName = stopName;

        if (isShortcutMode()) {
            Intent intent = ArrivalsListActivity.makeIntent(getActivity(),
                    stopId, stopName, stopDir);
            makeShortcut(shortcutName, intent);
        } else {
            ArrivalsListActivity.start(getActivity(), stopId, stopName, stopDir);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.stop_name);
        menu.setHeaderTitle(text.getText());
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_create_shortcut);
        } else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_get_stop_info);
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.my_context_showonmap);
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
            showOnMap(getListView(), info.position);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    private void showOnMap(ListView l, int position) {
        ObaStop stop = (ObaStop)l.getAdapter().getItem(position - l.getHeaderViewsCount());
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
            UIHelp.setStopDirection(view.findViewById(R.id.direction),
                    stop.getDirection(),
                    true);
        }
    }

    //
    // Loader
    //
    private static final class MyLoader extends AsyncTaskLoader<ObaStopsForLocationResponse> {
        private final String mQueryText;
        private final GeoPoint mCenter;

        public MyLoader(Context context, String query, GeoPoint center) {
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
