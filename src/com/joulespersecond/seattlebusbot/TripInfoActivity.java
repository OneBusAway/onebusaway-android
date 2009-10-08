package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class TripInfoActivity extends Activity {
	private static final String TAG = "TripInfoActivity";
	
	public static final String TRIP_ID = ".TripId";
	public static final String ROUTE_ID = ".RouteId";
	public static final String ROUTE_NAME = ".RouteName"; 
	public static final String STOP_ID = ".StopId";
	public static final String STOP_NAME = ".StopName";
	public static final String HEADSIGN = ".Headsign";
	public static final String DEPARTURE_TIME = ".Depart";
	
	private TripsDbAdapter mDbAdapter;
	private String mTripId;
	private String mRouteId;
	private String mRouteName;
	private String mStopId;
	private String mStopName;
	private String mHeadsign;
	private long mDepartTime;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trip_info);

    	final Bundle bundle = getIntent().getExtras();
    	if (bundle == null) {
    		Log.e(TAG, "Information missing from intent: no extras");
    		return;
    	}
		// Get everything from the bundle
		mTripId = bundle.getString(TRIP_ID);
		mStopId = bundle.getString(STOP_ID);
		mRouteId = bundle.getString(ROUTE_ID);
		mHeadsign = bundle.getString(HEADSIGN);
		mDepartTime = bundle.getLong(DEPARTURE_TIME);
		
		mStopName = bundle.getString(STOP_NAME);
		mRouteName = bundle.getString(ROUTE_NAME);
		if (mTripId == null || 
				mRouteId == null ||
				mStopId == null ||
				mHeadsign == null ||
				mDepartTime == 0) {
			Log.e(TAG, "Information missing from intent");
			return;
		}
		
		// If we have the route name, ensure that it's in the DB.
		if (mRouteName != null) {
			RoutesDbAdapter.addRoute(this, mRouteId, mRouteName, null, false);
		}
		// TODO: If mRouteName or mStopName are not available,
		// look them up in the routes and stops table
		
		// Populate the spinners: 
		setSpinner(R.id.trip_info_reminder_time, R.array.reminder_time);
		setSpinner(R.id.trip_info_reminder_repeats, R.array.reminder_repeat);
		
		// Populate from bundle:
		//  stop name
		//  route name
		//  headsign
		//  departure time
		final TextView stopName = (TextView)findViewById(R.id.stop_name);
		stopName.setText(mStopName);
		
		final TextView routeName = (TextView)findViewById(R.id.route_name);
		String fmt = getResources().getString(R.string.trip_info_route);
		routeName.setText(String.format(fmt, mRouteName));
			
		final TextView headsign = (TextView)findViewById(R.id.headsign);
		headsign.setText(mHeadsign);
		
		final TextView departText = (TextView)findViewById(R.id.departure_time);
		fmt = getResources().getString(R.string.trip_info_depart);
		departText.setText(String.format(fmt, DateUtils.formatDateTime(this, 
				mDepartTime, 
				DateUtils.FORMAT_SHOW_TIME|
				DateUtils.FORMAT_NO_NOON|
				DateUtils.FORMAT_NO_MIDNIGHT)));	
		
		mDbAdapter = new TripsDbAdapter(this);
		mDbAdapter.open();
		
		final boolean newTrip = populateFromDB();
		
		// Listen to the buttons:
		final Button save = (Button)findViewById(R.id.trip_info_save);
		save.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				saveTrip();
			}
		});
		final Button discard = (Button)findViewById(R.id.trip_info_cancel);
		discard.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				discardChanges();
			}
		});	

		final Button delete = (Button)findViewById(R.id.trip_info_delete);
		delete.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				deleteTrip();
			}
		});	
		if (newTrip) {
			// If this is a new trip, then hide the 'delete' button
			delete.setVisibility(View.GONE);
		}
	}
	@Override
	protected void onDestroy() {
		mDbAdapter.close();
		super.onDestroy();
	}
	
	private void saveTrip() {
		// Things that need updating:
		// Any constant values (trip info not editable by user)
		// Trip name
		// Reminder time
		// Repeats
		//
		final Spinner reminderView = (Spinner)findViewById(R.id.trip_info_reminder_time);
		final Spinner repeatsView  = (Spinner)findViewById(R.id.trip_info_reminder_repeats);
		final TextView nameView = (TextView)findViewById(R.id.name);
		
		final int reminder = selectionToReminder(reminderView.getSelectedItemPosition());
		int repeats = selectionToRepeat(repeatsView.getSelectedItemPosition());
		
		mDbAdapter.addTrip(mTripId, 
				mStopId, 
				mRouteId,
				mDepartTime,
				mHeadsign,
				nameView.getText().toString(), 
				reminder, 
				repeats);
		finish();
		
		Toast.makeText(this, R.string.trip_info_saved, Toast.LENGTH_SHORT).show();
	}
	private void discardChanges() {
		finish();
	}
	private void deleteTrip() {
		// TODO: Popup confirmation, then toast
	}
	
	private boolean populateFromDB() {
		final TextView tripName = (TextView)findViewById(R.id.name);
		final Spinner reminder = (Spinner)findViewById(R.id.trip_info_reminder_time);
		final Spinner repeats = (Spinner)findViewById(R.id.trip_info_reminder_repeats);
		
		// Look up the trip in the database.
		Cursor cursor = mDbAdapter.getTrip(mTripId, mStopId);
		if (cursor == null) {
			return true;
		}
		if (cursor.getCount() >= 1) {
			// If found, then populate:
			//   reminder_time
			//   reminder_repeats
			//   trip name
				
			final String name = cursor.getString(TripsDbAdapter.TRIP_COL_NAME);
			final int time = cursor.getInt(TripsDbAdapter.TRIP_COL_REMINDER);
			final int repeat = cursor.getInt(TripsDbAdapter.TRIP_COL_REPEAT);
			
			reminder.setSelection(reminderToSelection(time));
			repeats.setSelection(repeatToSelection(repeat));

			if (name != null) {
				tripName.setText(name);
			}
			else {
				tripName.setText("");
			}
			return false;
		}
		cursor.close(); 
		return true;
	}

	private void setSpinner(int spinnerId, int arrayId) {
	    Spinner s = (Spinner) findViewById(spinnerId);
	    ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
	            this, arrayId, android.R.layout.simple_spinner_item);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    s.setAdapter(adapter);
	}
	
	// This converts what's in the database to what can be displayed in the spinner.
	private static int reminderToSelection(int reminder) {
		switch (reminder) {
		case 0:		return 0;
		case 1: 	return 1;
		case 5:		return 2;
		case 10:	return 3;
		case 15:	return 4;
		case 20:	return 5;
		case 25:	return 6;
		case 30:	return 7;
		case 45:	return 8;
		case 60:	return 9;
		default:
			Log.e(TAG, "Invalid reminder value in DB: " + reminder);
			return 0;
		}
	}
	private static int selectionToReminder(int selection) {
		switch (selection) {
		case 0:		return 0;
		case 1: 	return 1;
		case 2:		return 5;
		case 3:		return 10;
		case 4:		return 15;
		case 5:		return 20;
		case 6:		return 25;
		case 7:		return 30;
		case 8:		return 45;
		case 9:		return 60;
		default:
			Log.e(TAG, "Invalid selection: " + selection);
			return 0;
		}		
	}
	private static int repeatToSelection(int repeat) {
		switch (repeat) {
		case TripsDbAdapter.REPEAT_ONETIME:	return 0;
		case TripsDbAdapter.REPEAT_DAILY:	return 1;
		case TripsDbAdapter.REPEAT_WEEKDAY:	return 2;
		}
		return 0;
	}
	private static int selectionToRepeat(int selection) {
		switch (selection) {
		case 0:	return TripsDbAdapter.REPEAT_ONETIME;
		case 1:	return TripsDbAdapter.REPEAT_DAILY;
		case 2:	return TripsDbAdapter.REPEAT_WEEKDAY;
		}
		return TripsDbAdapter.REPEAT_ONETIME;
	}
}
