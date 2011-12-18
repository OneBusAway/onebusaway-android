package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.provider.ObaContract;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;


public class MyRecentStopsFragment extends ListFragment
            implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final String TAB_NAME = "recent";

    private SimpleCursorAdapter mAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set empty text
        setEmptyText(getString(R.string.my_no_recent_stops));

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);
        registerForContextMenu(getListView());

        // Create our generic adapter
        mAdapter = QueryUtils.StopList.newAdapter(getActivity());
        setListAdapter(mAdapter);

        // Prepare the loader
        getLoaderManager().initLoader(0, null, this);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return QueryUtils.newRecentQuery(getActivity(),
                ObaContract.Stops.CONTENT_URI,
                QueryUtils.StopList.Columns.PROJECTION,
                ObaContract.Stops.ACCESS_TIME,
                ObaContract.Stops.USE_COUNT);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    //
    // MyRecentStopsActivity
    //

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
            ObaContract.Stops.markAsUnused(getActivity(),
                    Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI,
                            QueryUtils.StopList.getId(getListView(), info.position)));
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.my_recent_stop_options, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_recent) {
            ObaContract.Stops.markAsUnused(getActivity(), ObaContract.Stops.CONTENT_URI);
            return true;
        }
        return false;
    }
}
