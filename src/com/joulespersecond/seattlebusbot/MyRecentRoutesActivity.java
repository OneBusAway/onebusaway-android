package com.joulespersecond.seattlebusbot;

import android.database.Cursor;

import com.joulespersecond.oba.provider.ObaContract;

public class MyRecentRoutesActivity extends MyRouteListActivity {
    @Override
    Cursor getCursor() {
        // TODO: No limit???
        return managedQuery(ObaContract.Routes.CONTENT_URI,
                PROJECTION,
                ObaContract.Routes.ACCESS_TIME + " IS NOT NULL OR " +
                ObaContract.Routes.USE_COUNT + " >0",
                null,
                ObaContract.Routes.ACCESS_TIME + " desc, " +
                ObaContract.Routes.USE_COUNT + " desc");
    }
    @Override
    int getLayoutId() {
        return R.layout.my_recent_route_list;
    }
}
