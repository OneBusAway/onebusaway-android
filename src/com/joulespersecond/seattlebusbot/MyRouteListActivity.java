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
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

abstract class MyRouteListActivity extends MyBaseListActivity implements QueryUtils.RouteList.Columns {
    // private static final String TAG = "MyRouteListActivity";

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String routeId = c.getString(COL_ID);
        final String routeName = c.getString(COL_SHORTNAME);

        if (mShortcutMode) {
            final Intent shortcut = UIHelp.makeShortcut(this, routeName,
                    RouteInfoActivity.makeIntent(this, routeId));

            if (isChild()) {
                // Is there a way to do this more generically?
                final Activity parent = getParent();
                if (parent instanceof MyRoutesActivity) {
                    MyRoutesActivity myRoutes = (MyRoutesActivity)parent;
                    myRoutes.returnResult(shortcut);
                }
            } else {
                setResult(RESULT_OK, shortcut);
                finish();
            }
        } else if (mSearchMode) {
            final Activity parent = getParent();
            MyRoutesActivity myRoutes = (MyRoutesActivity)parent;

            Intent resultData = new Intent();
            resultData.putExtra(MyTabActivityBase.RESULT_ROUTE_ID, routeId);
            myRoutes.returnResult(resultData);
        } else {
            RouteInfoActivity.start(this, routeId);
        }
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;
    private static final int CONTEXT_MENU_SHOW_URL = 3;

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView
                .findViewById(R.id.short_name);
        menu.setHeaderTitle(getString(R.string.route_name, text.getText()));
        if (mShortcutMode) {
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
            MapViewActivity.start(this, QueryUtils.RouteList.getId(getListView(), info.position));
            return true;
        case CONTEXT_MENU_SHOW_URL:
            UIHelp.goToUrl(this, QueryUtils.RouteList.getUrl(getListView(), info.position));
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    protected Uri getObserverUri() {
        return ObaContract.Routes.CONTENT_URI;
    }
}
