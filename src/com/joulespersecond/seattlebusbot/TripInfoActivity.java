package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class TripInfoActivity extends Activity {
	private static final String TAG = "TripInfoActivity";
	
	private static final int REMINDER_DAYS_DIALOG = 1;
	
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
	private int mReminderDays = 0;
	
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
		
	    Spinner s = (Spinner) findViewById(R.id.trip_info_reminder_time);
	    ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
	            this, R.array.reminder_time, android.R.layout.simple_spinner_item);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    s.setAdapter(adapter);
		
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
		
		final Button repeats = (Button)findViewById(R.id.trip_info_reminder_days);
		repeats.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showDialog(REMINDER_DAYS_DIALOG);
			}
		});
		
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
				finish();
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
			View v = findViewById(R.id.trip_info_buttons);
			v.requestLayout();
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
		final TextView nameView = (TextView)findViewById(R.id.name);
		
		final int reminder = selectionToReminder(reminderView.getSelectedItemPosition());
		int repeats = 0;
		
		mDbAdapter.addTrip(mTripId, 
				mStopId, 
				mRouteId,
				mDepartTime,
				mHeadsign,
				nameView.getText().toString(), 
				reminder, 
				repeats);
		
		Toast.makeText(this, R.string.trip_info_saved, Toast.LENGTH_SHORT).show();
		finish();
	}
	private void deleteTrip() {
		// TODO: Popup confirmation, then toast
	}
	
	private boolean populateFromDB() {
		final TextView tripName = (TextView)findViewById(R.id.name);
		final Spinner reminder = (Spinner)findViewById(R.id.trip_info_reminder_time);
		
		// Look up the trip in the database.
		Cursor cursor = mDbAdapter.getTrip(mTripId, mStopId);
		if (cursor == null) {
			return true;
		}
		if (cursor.getCount() >= 1) {			
			final String name = cursor.getString(TripsDbAdapter.TRIP_COL_NAME);
			final int time = cursor.getInt(TripsDbAdapter.TRIP_COL_REMINDER);
			mReminderDays = cursor.getInt(TripsDbAdapter.TRIP_COL_DAYS);
			
			reminder.setSelection(reminderToSelection(time));

			if (name != null) {
				tripName.setText(name);
			}
			else {
				tripName.setText("");
			}
			return false;
		}
		else {
			// By default, the reminder is for 10 minutes, weekly
			reminder.setSelection(reminderToSelection(10));
		}
		cursor.close(); 
		return true;
	}
	
	private final DialogInterface.OnClickListener mDialogListener = 
		new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which == DialogInterface.BUTTON_POSITIVE) {
					// Save repeats
				}
				dialog.dismiss();
			}
	};
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		
		switch (id) {
		case REMINDER_DAYS_DIALOG:
			// NOTE: The custom adapter/layout is to work around
			// a bug where using setMultiChoiceItems with a light theme
			// causes white-on-white text.
			dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.trip_info_reminder_repeat)
				.setAdapter(new ArrayAdapter<String>(this,
							R.layout.select_dialog_multichoice,
							android.R.id.text1,
							getResources().getStringArray(R.array.reminder_days)),
						null)
				.setPositiveButton(R.string.trip_info_save, mDialogListener)
				.setNegativeButton(R.string.trip_info_dismiss, mDialogListener)
				.create();
			break;
		default:
			dialog = null;
			break;
		}
		return dialog;
	}
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case REMINDER_DAYS_DIALOG:
			AlertDialog alert = (AlertDialog)dialog;
			ListView list = alert.getListView();
			ArrayAdapter<String> adapter = (ArrayAdapter<String>)list.getAdapter();
			
			break;
		}

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
}
