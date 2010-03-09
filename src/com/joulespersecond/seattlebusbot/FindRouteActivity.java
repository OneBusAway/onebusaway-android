package com.joulespersecond.seattlebusbot;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
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
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.provider.ObaContract;

public class FindRouteActivity extends FindActivity {
    private static final String TAG = "FindRouteActivity";
    
    private static final String[] PROJECTION = {
        ObaContract.Routes._ID,
        ObaContract.Routes.SHORTNAME,
        ObaContract.Routes.LONGNAME
    };
    private static final int COL_ID = 0;
    private static final int COL_SHORTNAME = 1;
    //private static final int COL_LONGNAME = 2;
    
    private boolean isSearching() {
        ListAdapter adapter = getListView().getAdapter();
        return adapter instanceof SearchResultsListAdapter;
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String routeId;
        String routeName;
        // Get the adapter (this may or may not be a SimpleCursorAdapter)
        ListAdapter adapter = l.getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            routeId = c.getString(COL_ID);
            routeName = c.getString(COL_SHORTNAME);
        }
        else if (adapter instanceof SearchResultsListAdapter) {
            ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
            routeId = route.getId();
            routeName = route.getShortName();
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return;
        }
        
        if (isShortcutMode()) {
            Intent intent = RouteInfoActivity.makeIntent(this, routeId);
            makeShortcut(routeName, intent);
        }
        else {
            RouteInfoActivity.start(this, routeId);        
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
        final TextView text = (TextView)info.targetView.findViewById(R.id.short_name);
        menu.setHeaderTitle(getString(R.string.route_name, text.getText()));
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_create_shortcut);
        }
        else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.find_context_get_route_info);            
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
            MapViewActivity.start(this, getId(getListView(), info.position));
            return true;
        case CONTEXT_MENU_DELETE:
            removeFavorite(getId(getListView(), info.position));
            requery();
            return true;
        default:
            return super.onContextItemSelected(item);
        }
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
            ObaRoute route = (ObaRoute)adapter.getItem(position - l.getHeaderViewsCount());
            return route.getId();
        }
        else {
            Log.e(TAG, "Unknown adapter. Giving up!");
            return "";
        }        
    }
    
    private final class SearchResultsListAdapter extends Adapters.BaseArrayAdapter<ObaRoute> {
        public SearchResultsListAdapter(ObaResponse response) {
            super(FindRouteActivity.this, 
                    response.getData().getRoutes(), 
                    R.layout.find_route_listitem);
        }
        @Override
        protected void setData(View view, int position) {
            TextView shortName = (TextView)view.findViewById(R.id.short_name);
            TextView longName = (TextView)view.findViewById(R.id.long_name);

            ObaRoute route = mArray.get(position);
            shortName.setText(route.getShortName());
            longName.setText(route.getLongNameOrDescription());
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.find_route;
    }
    @Override
    protected int getTitleId() {
        return R.string.find_route_title;
    }
    @Override
    protected int getMinSearchLength() {
        return 1;
    }
    
    @Override
    protected void clearFavorites() {
        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(ObaContract.Routes.USE_COUNT, 0);
        cr.update(ObaContract.Routes.CONTENT_URI, values, null, null);
    }
    private void removeFavorite(String id) {
        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(ObaContract.Routes.USE_COUNT, 0);
        cr.update(ObaContract.Routes.CONTENT_URI, values, 
                ObaContract.Routes._ID+"=?", new String[] { id });        
    }
    
    @Override
    protected void fillFavorites() {
        ContentResolver cr = getContentResolver();
        // TODO: No limit???
        Cursor c = cr.query(ObaContract.Routes.CONTENT_URI, 
                PROJECTION, 
                ObaContract.Routes.USE_COUNT + " >0", 
                null, 
                ObaContract.Routes.USE_COUNT + " desc");
        
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
            new SimpleCursorAdapter(this, R.layout.find_route_listitem, c, from, to);
        setListAdapter(simpleAdapter);      
    }

    @Override
    protected void setResultsAdapter(ObaResponse response) {
        setListAdapter(new SearchResultsListAdapter(response));  
    }
    @Override
    protected void setNoFavoriteText() {
        TextView empty = (TextView) findViewById(android.R.id.empty);
        empty.setText(R.string.find_hint_nofavoriteroutes);
    }

    @Override
    protected ObaResponse doFindInBackground(String routeId) {
        ObaResponse response = ObaApi.getRoutesByLocation(this,
                UIHelp.getLocation(FindRouteActivity.this), 0, routeId);
        // If there is no results from the user-centered query,
        // open a wider next in some "default" Seattle/Bellevue location
        Log.d(TAG, "Server returns: " + response.getCode());
        if (response.getCode() == ObaApi.OBA_OK) {
            ObaArray<ObaRoute> routes = response.getData().getRoutes();
            if (routes.length() != 0) {
                return response;
            }
        }

        return ObaApi.getRoutesByLocation(this, UIHelp.DEFAULT_SEARCH_CENTER,
                UIHelp.DEFAULT_SEARCH_RADIUS,
                routeId); 
    }
}
