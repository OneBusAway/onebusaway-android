package com.joulespersecond.seattlebusbot;

import android.content.Intent;
import android.database.Cursor;
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

public class FindStopActivity extends FindActivity {
    private static final String TAG = "FindStopActivity";
    
    private StopsDbAdapter mDbAdapter;
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String stopId;
        String stopName;
        String stopDir;
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            stopId = c.getString(StopsDbAdapter.STOP_COL_STOPID);
            stopName = c.getString(StopsDbAdapter.STOP_COL_NAME);
            stopDir = c.getString(StopsDbAdapter.STOP_COL_DIRECTION);
        }
        else if (adapter instanceof SearchResultsListAdapter) {
            ObaStop stop = (ObaStop)adapter.getItem(position - l.getHeaderViewsCount());
            stopId = stop.getId();
            stopName = stop.getName();
            stopDir = stop.getDirection();
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return;
        }

        if (isShortcutMode()) {
            Intent intent = StopInfoActivity.makeIntent(this, stopId, stopName, stopDir);
            makeShortcut(stopName, intent);
        }
        else {
            StopInfoActivity.start(this, stopId, stopName, stopDir);          
        }
    }
    
    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.name);
        menu.setHeaderTitle(text.getText());
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_create_shortcut);
        }
        else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_get_stop_info);            
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.find_context_showonmap);
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
            stopId = c.getString(StopsDbAdapter.STOP_COL_STOPID);
            lat = c.getDouble(StopsDbAdapter.STOP_COL_LATITUDE);
            lon = c.getDouble(StopsDbAdapter.STOP_COL_LONGITUDE);
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

    private final class SearchResultsListAdapter extends Adapters.BaseArrayAdapter<ObaStop> {       
        public SearchResultsListAdapter(ObaResponse response) {
            super(FindStopActivity.this,
                    response.getData().getStops(),
                    R.layout.find_stop_listitem);
        }
        @Override
        protected void setData(View view, int position) {
            TextView route = (TextView)view.findViewById(R.id.name);
            TextView direction = (TextView)view.findViewById(R.id.direction);

            ObaStop stop = mArray.get(position);
            route.setText(stop.getName());
            direction.setText(UIHelp.getStopDirectionText(stop.getDirection()));
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
        StopsDbAdapter.clearFavorites(this);
    }
    @Override
    protected void openDB() {
        mDbAdapter = new StopsDbAdapter(this);
        mDbAdapter.open();
        
    }
    @Override
    protected void closeDB() {
        mDbAdapter.close();        
    }

    @Override
    protected void fillFavorites() {
        Cursor c = mDbAdapter.getFavoriteStops();
        startManagingCursor(c);   
        
        String[] from = new String[] { 
                DbHelper.KEY_NAME,
                DbHelper.KEY_DIRECTION 
        };
        int[] to = new int[] {
                R.id.name,
                R.id.direction
        };
        SimpleCursorAdapter simpleAdapter = 
            new SimpleCursorAdapter(this, R.layout.find_stop_listitem, c, from, to);
        
        // We need to convert the direction text (N/NW/E/etc)
        // to user level text (North/Northwest/etc..)
        simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == StopsDbAdapter.STOP_COL_DIRECTION) {
                    TextView direction = (TextView)view.findViewById(R.id.direction);
                    direction.setText(
                            UIHelp.getStopDirectionText(cursor.getString(columnIndex)));
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
        return ObaApi.getStopsByLocation(UIHelp.getLocation(this), 0, 0, 0, param, 0);
    }
}
