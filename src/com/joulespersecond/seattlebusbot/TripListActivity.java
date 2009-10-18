package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class TripListActivity extends ListActivity {
	//private static final String TAG = "TripListActivity";
	
	public TripsDbAdapter mDbAdapter;
	public RoutesDbAdapter mRoutesDbAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.trip_list);
		
		mDbAdapter = new TripsDbAdapter(this);
		mDbAdapter.open();
		mRoutesDbAdapter = new RoutesDbAdapter(this);
		mRoutesDbAdapter.open();
		
		fillTrips();
	}
	
	private void fillTrips() {
		Cursor c = mDbAdapter.getTrips();
		startManagingCursor(c);
		
		String[] from = new String[] { 
				DbHelper.KEY_NAME,
				DbHelper.KEY_HEADSIGN,
				DbHelper.KEY_DEPARTURE,
				DbHelper.KEY_ROUTE
		};
		int[] to = new int[] {
				R.id.name,
				R.id.headsign,
				R.id.departure_time,
				R.id.route_name
		};
		SimpleCursorAdapter simpleAdapter = 
			new SimpleCursorAdapter(this, R.layout.trip_list_listitem, c, from, to);
		
		simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (columnIndex == TripsDbAdapter.TRIP_COL_DEPARTURE) {
					TextView text = (TextView)view;
					text.setText(TripInfoActivity.getDepartureTime(
							TripListActivity.this,
							cursor.getLong(columnIndex)));
					return true;
				} 
				else if (columnIndex == TripsDbAdapter.TRIP_COL_ROUTEID) {
					// 
					// Translate the Route ID into the Route Name by looking
					// it up in the Routes table.
					//
					TextView text = (TextView)view;
					final String routeId = cursor.getString(columnIndex);
									
					Cursor route = mRoutesDbAdapter.getRoute(routeId);
					if (route != null && cursor.getCount() >= 1) {
						
						String fmt = getResources().getString(R.string.trip_info_route);
						text.setText(String.format(fmt, 
								route.getString(RoutesDbAdapter.ROUTE_COL_SHORTNAME)));
					}
					if (route != null) {
						route.close();
					}
					return true;
				}
				return false;
			}
		});
		setListAdapter(simpleAdapter);
	}
	@Override
	protected void onDestroy() {
		mDbAdapter.close();
		mRoutesDbAdapter.close();
		super.onDestroy();
	}
	
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
		// Get the cursor and fetch the stop ID from that.
		SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
		final Cursor c = cursorAdapter.getCursor();
		c.moveToPosition(position - l.getHeaderViewsCount());
		final String tripId = c.getString(TripsDbAdapter.TRIP_COL_TRIPID);
		final String stopId = c.getString(TripsDbAdapter.TRIP_COL_STOPID);

		Intent myIntent = new Intent(this, TripInfoActivity.class);
		myIntent.putExtra(TripInfoActivity.TRIP_ID, tripId);
		myIntent.putExtra(TripInfoActivity.STOP_ID, stopId);
		startActivity(myIntent);
    }
}
