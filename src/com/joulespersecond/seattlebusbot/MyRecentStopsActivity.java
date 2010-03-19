package com.joulespersecond.seattlebusbot;

import android.database.Cursor;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;

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

    private static final int CONTEXT_MENU_DELETE = 10;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.my_context_remove_recent);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DELETE:
            ObaContract.Stops.markAsUnused(this,
                    Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI,
                            getId(getListView(), info.position)));
            requery();
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_recent_stop_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_recent) {
            ObaContract.Stops.markAsUnused(this, ObaContract.Stops.CONTENT_URI);
            requery();
            return true;
        }
        return false;
    }
}
