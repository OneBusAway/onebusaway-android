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

import com.joulespersecond.oba.provider.ObaContract;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.view.MenuItem;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

abstract class MyRouteListFragmentBase extends MyListFragmentBase
            implements QueryUtils.RouteList.Columns {
    // private static final String TAG = "MyRouteListActivity";

    @Override
    protected SimpleCursorAdapter newAdapter() {
        return QueryUtils.RouteList.newAdapter(getActivity());
    }

    @Override
    protected Uri getContentUri() {
        return ObaContract.Routes.CONTENT_URI;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {

        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String routeId = c.getString(COL_ID);
        final String routeName = c.getString(COL_SHORTNAME);

        if (isShortcutMode()) {
            final Intent shortcut = UIHelp.makeShortcut(getActivity(), routeName,
                    RouteInfoActivity.makeIntent(getActivity(), routeId));

            Activity activity = getActivity();
            activity.setResult(Activity.RESULT_OK, shortcut);
            activity.finish();

        } else if (isSearchMode()) {
            Activity activity = getActivity();

            Intent resultData = new Intent();
            resultData.putExtra(MyTabActivityBase.RESULT_ROUTE_ID, routeId);
            activity.setResult(Activity.RESULT_OK, resultData);
            activity.finish();

        } else {
            RouteInfoActivity.start(getActivity(), routeId);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView
                .findViewById(R.id.short_name);
        menu.setHeaderTitle(getString(R.string.route_name, text.getText()));
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0,
                    R.string.my_context_create_shortcut);
        } else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0,
                    R.string.my_context_get_route_info);
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.my_context_showonmap);
        final String url = QueryUtils.RouteList.getUrl(getListView(), info.position);
        if (!TextUtils.isEmpty(url)) {
            menu.add(0, CONTEXT_MENU_SHOW_URL, 0,
                    R.string.my_context_show_schedule);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item
                .getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DEFAULT:
            // Fake a click
            onListItemClick(getListView(), info.targetView, info.position,
                    info.id);
            return true;
        case CONTEXT_MENU_SHOW_ON_MAP:
            HomeActivity.start(getActivity(),
                    QueryUtils.RouteList.getId(getListView(), info.position));
            return true;
        case CONTEXT_MENU_SHOW_URL:
            UIHelp.goToUrl(getActivity(),
                    QueryUtils.RouteList.getUrl(getListView(), info.position));
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    abstract protected int getEmptyText();
}
