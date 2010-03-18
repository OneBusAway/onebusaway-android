package com.joulespersecond.seattlebusbot;

import android.database.Cursor;

import com.joulespersecond.oba.provider.ObaContract;

public class MyStarredStopsActivity extends MyStopListActivity {
    @Override
    Cursor getCursor() {
        return managedQuery(ObaContract.Stops.CONTENT_URI,
                PROJECTION,
                ObaContract.Stops.FAVORITE + "=1",
                null,
                ObaContract.Stops.USE_COUNT + " desc");
    }
    @Override
    int getLayoutId() {
        return R.layout.my_starred_stop_list;
    }
    // TODO: Allow deleting from this list via the context menu.
}
