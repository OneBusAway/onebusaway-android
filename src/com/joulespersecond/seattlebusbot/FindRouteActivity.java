package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;

public class FindRouteActivity extends ListActivity {
	private RoutesDbAdapter mDbAdapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.find_route);
		
		mDbAdapter = new RoutesDbAdapter(this);
		mDbAdapter.open();
		// TODO: Inflate the header
		
		fillFavorites();
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
}
