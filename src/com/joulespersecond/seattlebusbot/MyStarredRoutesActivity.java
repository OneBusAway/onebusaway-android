package com.joulespersecond.seattlebusbot;

import android.database.Cursor;

import com.joulespersecond.oba.provider.ObaContract;

public class MyStarredRoutesActivity extends MyRouteListActivity {
    @Override
    Cursor getCursor() {
        return managedQuery(ObaContract.Routes.CONTENT_URI,
                PROJECTION,
                ObaContract.Routes.FAVORITE + "=1",
                null,
                ObaContract.Routes.USE_COUNT + " desc");
    }
    @Override
    int getLayoutId() {
        return R.layout.my_starred_route_list;
    }
    // TODO: Allow deleting from this list via the context menu.
}
