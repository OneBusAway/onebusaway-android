package com.joulespersecond.seattlebusbot;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;

public class FindStopActivity extends FindActivity {
    private static final String TAG = "FindStopActivity";

    private static final String[] PROJECTION = {
        ObaContract.Stops._ID,
        ObaContract.Stops.UI_NAME,
        ObaContract.Stops.DIRECTION,
        ObaContract.Stops.LATITUDE,
        ObaContract.Stops.LONGITUDE,
        ObaContract.Stops.UI_NAME,
        ObaContract.Stops.FAVORITE
    };
    private static final int COL_ID = 0;
    private static final int COL_NAME = 1;
    private static final int COL_DIRECTION = 2;
    private static final int COL_LATITUDE = 3;
    private static final int COL_LONGITUDE = 4;
    private static final int COL_UI_NAME = 5;
    private static final int COL_FAVORITE = 6;

    private UIHelp.StopUserInfoMap mStopUserMap;

    private boolean isSearching() {
        ListAdapter adapter = getListView().getAdapter();
        return adapter instanceof SearchResultsListAdapter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mStopUserMap = new UIHelp.StopUserInfoMap(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String stopId;
        String stopName;
        String stopDir;
        String shortcutName;
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            stopId = c.getString(COL_ID);
            stopName = c.getString(COL_NAME);
            stopDir = c.getString(COL_DIRECTION);
            shortcutName = c.getString(COL_UI_NAME);
        }
        else if (adapter instanceof SearchResultsListAdapter) {
            ObaStop stop = (ObaStop)adapter.getItem(position - l.getHeaderViewsCount());
            stopId = stop.getId();
            stopName = stop.getName();
            stopDir = stop.getDirection();
            shortcutName = stopName;
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return;
        }

        if (isShortcutMode()) {
            Intent intent = StopInfoActivity.makeIntent(this, stopId, stopName, stopDir);
            makeShortcut(shortcutName, intent);
        }
        else {
            StopInfoActivity.start(this, stopId, stopName, stopDir);
        }
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;
    private static final int CONTEXT_MENU_DELETE = 3;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.stop_name);
        menu.setHeaderTitle(text.getText());
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_create_shortcut);
        }
        else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_get_stop_info);
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.find_context_showonmap);
        if (!isSearching()) {
            menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.find_context_remove_favorite);
        }
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
            showOnMap(getListView(), info.position);
            return true;
        case CONTEXT_MENU_DELETE:
            removeFavorite(getId(getListView(), info.position));
            requery();
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    private void showOnMap(ListView l, int position) {
        String stopId;
        double lat, lon;
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            stopId = c.getString(COL_ID);
            lat = c.getDouble(COL_LATITUDE);
            lon = c.getDouble(COL_LONGITUDE);
        }
        else if (adapter instanceof SearchResultsListAdapter) {
            ObaStop stop = (ObaStop)adapter.getItem(position - l.getHeaderViewsCount());
            stopId = stop.getId();
            lat = stop.getLatitude();
            lon = stop.getLongitude();
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return;
        }
        MapViewActivity.start(this, stopId, lat, lon);
    }

    private String getId(ListView l, int position) {
        ListAdapter adapter = l.getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(COL_ID);
        }
        else if (adapter instanceof SearchResultsListAdapter) {
            ObaStop stop = (ObaStop)adapter.getItem(position - l.getHeaderViewsCount());
            return stop.getId();
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return "";
        }
    }

    private final class SearchResultsListAdapter extends Adapters.BaseArrayAdapter<ObaStop> {
        public SearchResultsListAdapter(ObaResponse response) {
            super(FindStopActivity.this,
                    response.getData().getStops(),
                    R.layout.find_stop_listitem);
        }
        @Override
        protected void setData(View view, int position) {
            ObaStop stop = mArray.get(position);
            mStopUserMap.setView(view, stop.getId(), stop.getName());
            UIHelp.setStopDirection(view.findViewById(R.id.direction),
                    stop.getDirection(),
                    true);
        }
    }

    private static final String URL_STOPID = MapViewActivity.HELP_URL + "#finding_stop_ids";

    @Override
    protected int getLayoutId() {
        return R.layout.find_stop;
    }
    @Override
    protected int getTitleId() {
        return R.string.find_stop_title;
    }
    @Override
    protected int getMinSearchLength() {
        return 5;
    }

    @Override
    protected void clearFavorites() {
        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops.USE_COUNT, 0);
        cr.update(ObaContract.Stops.CONTENT_URI, values, null, null);
    }
    private void removeFavorite(String id) {
        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops.USE_COUNT, 0);
        cr.update(ObaContract.Stops.CONTENT_URI, values,
                ObaContract.Stops._ID+"=?", new String[] { id });
    }

    @Override
    protected void fillFavorites() {
        ContentResolver cr = getContentResolver();
        // TODO: No limit???
        Cursor c = cr.query(ObaContract.Stops.CONTENT_URI,
                PROJECTION,
                ObaContract.Stops.USE_COUNT + " >0",
                null,
                ObaContract.Stops.USE_COUNT + " desc");

        startManagingCursor(c);

        String[] from = new String[] {
                ObaContract.Stops.UI_NAME,
                ObaContract.Stops.DIRECTION,
                ObaContract.Stops.FAVORITE
        };
        int[] to = new int[] {
                R.id.stop_name,
                R.id.direction,
                R.id.stop_favorite
        };
        SimpleCursorAdapter simpleAdapter =
            new SimpleCursorAdapter(this, R.layout.find_stop_listitem, c, from, to);

        // We need to convert the direction text (N/NW/E/etc)
        // to user level text (North/Northwest/etc..)
        simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == COL_FAVORITE) {
                    View favorite = view.findViewById(R.id.stop_favorite);
                    favorite.setVisibility(
                            cursor.getInt(columnIndex) == 1 ? View.VISIBLE : View.GONE);
                    return true;
                }
                else if (columnIndex == COL_DIRECTION) {
                    UIHelp.setStopDirection(view.findViewById(R.id.direction),
                            cursor.getString(columnIndex),
                            true);
                    return true;
                }
                return false;
            }
        });
        setListAdapter(simpleAdapter);
    }

    @Override
    protected void setResultsAdapter(ObaResponse response) {
        setListAdapter(new SearchResultsListAdapter(response));
    }
    @Override
    protected void setNoFavoriteText() {
        final CharSequence first = getText(R.string.find_hint_nofavoritestops);
        final int firstLen = first.length();
        final CharSequence second = getText(R.string.find_hint_nofavoritestops_link);

        SpannableStringBuilder builder = new SpannableStringBuilder(first);
        builder.append(second);
        builder.setSpan(new URLSpan(URL_STOPID), firstLen, firstLen+second.length(), 0);

        TextView empty = (TextView) findViewById(android.R.id.empty);
        empty.setText(builder, TextView.BufferType.SPANNABLE);
    }

    @Override
    protected ObaResponse doFindInBackground(String param) {
        return ObaApi.getStopsByLocation(this, UIHelp.getLocation(this), 0, 0, 0, param, 0);
    }
}
