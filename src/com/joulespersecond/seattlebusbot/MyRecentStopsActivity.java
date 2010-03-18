package com.joulespersecond.seattlebusbot;

import android.database.Cursor;

import com.joulespersecond.oba.provider.ObaContract;

public class MyRecentStopsActivity extends MyStopListActivity {
    @Override
    Cursor getCursor() {
        // TODO: No limit???
        return managedQuery(ObaContract.Stops.CONTENT_URI,
                PROJECTION,
                ObaContract.Stops.ACCESS_TIME + " IS NOT NULL OR " +
                ObaContract.Stops.USE_COUNT + " >0",
                null,
                ObaContract.Stops.ACCESS_TIME + " desc, " +
                ObaContract.Stops.USE_COUNT + " desc");
    }
    @Override
    int getLayoutId() {
        return R.layout.my_recent_stop_list;
    }
}
