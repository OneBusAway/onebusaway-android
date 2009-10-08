package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;

public class TripListActivity extends ListActivity {
	//private static final String TAG = "TripListActivity";
	
	private TripsDbAdapter mDbAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.trip_list);
		
		mDbAdapter = new TripsDbAdapter(this);
		mDbAdapter.open();
		fillTrips();
	}
	
	private void fillTrips() {
		Cursor c = mDbAdapter.getTrips();
		startManagingCursor(c);
		
		String[] from = new String[] { 
				DbHelper.KEY_NAME,
				DbHelper.KEY_HEADSIGN,
				DbHelper.KEY_DEPARTURE,
				DbHelper.KEY_REMINDER,
				DbHelper.KEY_REPEAT
		};
		int[] to = new int[] {
				R.id.name,
				R.id.headsign,
				R.id.departure_time,
				R.id.reminder_time,
				R.id.reminder_repeats
		};
		SimpleCursorAdapter simpleAdapter = 
			new SimpleCursorAdapter(this, R.layout.trip_list_listitem, c, from, to);
		
		// TODO: Convert the departure time, reminder time, and repeats value
		// into something the user can understand.
		
		// TODO: Also, we'll also need the Route Name and Stop Name, but we 
		// will need to adjust the query to look them up in the stops/routes tables,
		// if they exist there.
		/*
		simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (columnIndex == StopsDbAdapter.FAVORITE_COL_DIRECTION) {
					TextView direction = (TextView)view.findViewById(R.id.direction);
					direction.setText(
							StopInfoActivity.getStopDirectionText(cursor.getString(columnIndex)));
					return true;
				} 
				return false;
			}
		});
		*/
		setListAdapter(simpleAdapter);
	}
	@Override
	protected void onDestroy() {
		mDbAdapter.close();
		super.onDestroy();
	}
}
