package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class FindRouteActivity extends ListActivity {
	private RoutesDbAdapter mDbAdapter;
	private View mListHeader;
	private boolean mShortcutMode = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.find_route);
		
		Intent myIntent = getIntent();
		if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
			mShortcutMode = true;
		}
		
		mDbAdapter = new RoutesDbAdapter(this);
		mDbAdapter.open();
		
		// Inflate the header
		ListView listView = getListView();
		LayoutInflater inflater = getLayoutInflater();
		mListHeader = inflater.inflate(R.layout.find_route_header, null);
		listView.addHeaderView(mListHeader);
		
		fillFavorites();
	}
	@Override
	protected void onDestroy() {
		mDbAdapter.close();
		super.onDestroy();
	}
	
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	String routeId;
    	String routeName;
    	// Get the adapter (this may or may not be a SimpleCursorAdapter)
    	HeaderViewListAdapter hdrAdapter = (HeaderViewListAdapter)l.getAdapter();
    	ListAdapter adapter = hdrAdapter.getWrappedAdapter();
    	if (adapter instanceof SimpleCursorAdapter) {
    		// Get the cursor and fetch the stop ID from that.
    		SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
    		Cursor c = cursorAdapter.getCursor();
    		c.moveToPosition(position - l.getHeaderViewsCount());
    		routeId = c.getString(RoutesDbAdapter.FAVORITE_COL_ROUTEID);
    		routeName = c.getString(RoutesDbAdapter.FAVORITE_COL_SHORTNAME);
    	}
    	else {
    		// Simple adapter, search results
    		return;
    	}
		
    	if (mShortcutMode) {
    		makeShortcut(routeId, routeName);
    	}
    	else {
        	Intent myIntent = new Intent(this, RouteInfoActivity.class);
    		myIntent.putExtra(RouteInfoActivity.ROUTE_ID, routeId);
    		startActivity(myIntent);    		
    	}
    }
	
	private void fillFavorites() {
		Cursor c = mDbAdapter.getFavoriteRoutes();
		startManagingCursor(c);
		
		String[] from = new String[] { 
				DbHelper.KEY_SHORTNAME,
				DbHelper.KEY_LONGNAME 
		};
		int[] to = new int[] {
				R.id.short_name,
				R.id.long_name
		};
		SimpleCursorAdapter simpleAdapter = 
			new SimpleCursorAdapter(this, R.layout.find_route_listitem, c, from, to);
		setListAdapter(simpleAdapter);
	}
	
	private void makeShortcut(String routeId, String routeName) {
		Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
		shortcutIntent.setClass(this, RouteInfoActivity.class);
		shortcutIntent.putExtra(RouteInfoActivity.ROUTE_ID, routeId);
		
		// Set up the container intent
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, routeName);
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(
                this,  R.drawable.icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher
        setResult(RESULT_OK, intent);
        finish();		
	}
}
