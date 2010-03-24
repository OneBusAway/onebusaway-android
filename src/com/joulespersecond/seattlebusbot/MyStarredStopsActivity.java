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

    private static final int CONTEXT_MENU_DELETE = 10;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.my_context_remove_star);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DELETE:
            final String id = getId(getListView(), info.position);
            final Uri uri = Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, id);
            ObaContract.Stops.markAsFavorite(this, uri, false);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_starred_stop_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_starred) {
            // TODO: We should probably have a confirmation..
            ObaContract.Stops.markAsFavorite(this, ObaContract.Stops.CONTENT_URI, false);
            return true;
        }
        return false;
    }
}
