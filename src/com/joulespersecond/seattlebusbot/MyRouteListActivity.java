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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.joulespersecond.oba.provider.ObaContract;

abstract class MyRouteListActivity extends MyBaseListActivity {
    //private static final String TAG = "MyRouteListActivity";

    protected boolean mShortcutMode;

    protected static final String[] PROJECTION = {
        ObaContract.Routes._ID,
        ObaContract.Routes.SHORTNAME,
        ObaContract.Routes.LONGNAME
    };
    private static final int COL_ID = 0;
    private static final int COL_SHORTNAME = 1;
    //private static final int COL_LONGNAME = 2;

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String routeId = c.getString(COL_ID);
        final String routeName = c.getString(COL_SHORTNAME);

        if (mShortcutMode) {
            final Intent shortcut =
                UIHelp.makeShortcut(this, routeName,
                        RouteInfoActivity.makeIntent(this, routeId));

            if (isChild()) {
                // Is there a way to do this more generically?
                final Activity parent = getParent();
                if (parent instanceof MyRoutesActivity) {
                    MyRoutesActivity myRoutes = (MyRoutesActivity)parent;
                    myRoutes.returnShortcut(shortcut);
                }
            }
            else {
                setResult(RESULT_OK, shortcut);
                finish();
            }
        }
        else {
            RouteInfoActivity.start(this, routeId);
        }
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.short_name);
        menu.setHeaderTitle(getString(R.string.route_name, text.getText()));
        if (mShortcutMode) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_create_shortcut);
        }
        else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_get_route_info);
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
            MapViewActivity.start(this, getId(getListView(), info.position));
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    protected String getId(ListView l, int position) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        return c.getString(COL_ID);
    }

    @Override
    protected void initList(Cursor c) {
        startManagingCursor(c);

        final String[] from = {
                ObaContract.Routes.SHORTNAME,
                ObaContract.Routes.LONGNAME
        };
        final int[] to = {
                R.id.short_name,
                R.id.long_name
        };
        SimpleCursorAdapter simpleAdapter =
            new SimpleCursorAdapter(this, R.layout.route_list_item, c, from, to);
        setListAdapter(simpleAdapter);
    }

    Uri getObserverUri() {
        return ObaContract.Routes.CONTENT_URI;
    }
}
