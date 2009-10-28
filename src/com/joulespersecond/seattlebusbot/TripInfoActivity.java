package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.Resources;
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
	
	private static final int REMINDER_DAYS_DIALOG = 1;
	private static final int DELETE_DIALOG = 2;
	
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
	private String mTripName;
	private long mDepartTime;
	private int mReminderTime; // DB Value, not selection value
	private int mReminderDays;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.trip_info);

    	final Bundle bundle = getIntent().getExtras();
    	if (bundle == null) {
    		Log.e(TAG, "Information missing from intent: no extras");
    		finish();
    		return;
    	}
    	if (!initFromBundle(bundle)) {
			Log.e(TAG, "Information missing from intent");
			finish();
    		return;
    	}

    	boolean newTrip = !initFromDB();
		initForm(newTrip);
	}
		
	private boolean initFromBundle(Bundle bundle) {
		// Get everything from the bundle
		mTripId = bundle.getString(TRIP_ID);
		mStopId = bundle.getString(STOP_ID);
		if (mTripId == null || mStopId == null) {
			return false;
		}		
		mRouteId = bundle.getString(ROUTE_ID);
		mHeadsign = bundle.getString(HEADSIGN);
		mDepartTime = bundle.getLong(DEPARTURE_TIME);
		
		mStopName = bundle.getString(STOP_NAME);
		mRouteName = bundle.getString(ROUTE_NAME);	
		// If we get this, update it in the DB.
		if (mRouteName != null) {
			RoutesDbAdapter.addRoute(this, mRouteId, mRouteName, null, false);
		}
		return true;
	}
	private boolean initFromDB() {
		mDbAdapter = new TripsDbAdapter(this);
		mDbAdapter.open();
		
		// Look up the trip in the database.
		Cursor cursor = mDbAdapter.getTrip(mTripId, mStopId);
		if (cursor == null || cursor.getCount() < 1) {
			// Reminder defaults to 10 in the UI
			mReminderTime = 10;
			if (cursor != null) {
				cursor.close();
			}
			return false;
		}		
		mTripName = cursor.getString(TripsDbAdapter.TRIP_COL_NAME);
		mReminderTime = cursor.getInt(TripsDbAdapter.TRIP_COL_REMINDER);
		mReminderDays = cursor.getInt(TripsDbAdapter.TRIP_COL_DAYS);
			
		// If some values weren't set in the bundle, assign them the 
		// values in the db.
		if (mRouteId == null) {
			mRouteId = cursor.getString(TripsDbAdapter.TRIP_COL_ROUTEID);
		}
		if (mHeadsign == null) {
			mHeadsign = cursor.getString(TripsDbAdapter.TRIP_COL_HEADSIGN);
		}
		if (mDepartTime == 0) {
			mDepartTime = TripsDbAdapter.convertDBToTime(
					cursor.getInt(TripsDbAdapter.TRIP_COL_DEPARTURE));
		}

		// If we don't have the route name, look it up in the DB
		if (mRouteName == null) {
			mRouteName = RoutesDbAdapter.getRouteShortName(this, mRouteId);
		}
		if (mStopName == null) {
			mStopName = StopsDbAdapter.getStopName(this, mStopId);
		}
		cursor.close();
		return true;
	}
	
	private void initForm(boolean newTrip) {
	    Spinner s = (Spinner) findViewById(R.id.trip_info_reminder_time);
	    ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
	            this, R.array.reminder_time, android.R.layout.simple_spinner_item);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    s.setAdapter(adapter);
		
	    //
		// Static (header values)
	    //
		final TextView stopName = (TextView)findViewById(R.id.stop_name);
		stopName.setText(mStopName);
		
		final TextView routeName = (TextView)findViewById(R.id.route_name);
		String fmt = getResources().getString(R.string.trip_info_route);
		routeName.setText(String.format(fmt, mRouteName));
			
		final TextView headsign = (TextView)findViewById(R.id.headsign);
		headsign.setText(mHeadsign);
		
		final TextView departText = (TextView)findViewById(R.id.departure_time);
		departText.setText(getDepartureTime(this, mDepartTime));
		
		//
		// User values
		//
		final TextView tripName = (TextView)findViewById(R.id.name);
		tripName.setText(mTripName);
		
		final Spinner reminder = (Spinner)findViewById(R.id.trip_info_reminder_time);
		reminder.setSelection(reminderToSelection(mReminderTime));		
		
		final Button repeats = (Button)findViewById(R.id.trip_info_reminder_days);
		repeats.setText(getRepeatText(this, mReminderDays));
		
		//
		// Buttons
		//
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
				showDialog(DELETE_DIALOG);
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
	
	public void saveTrip() {
		// Things that need updating:
		// Any constant values (trip info not editable by user)
		// Trip name
		// Reminder time
		// Repeats
		//
		final Spinner reminderView = (Spinner)findViewById(R.id.trip_info_reminder_time);
		final TextView nameView = (TextView)findViewById(R.id.name);
		
		final int reminder = selectionToReminder(reminderView.getSelectedItemPosition());
		
		mDbAdapter.addTrip(mTripId, 
				mStopId, 
				mRouteId,
				mDepartTime,
				mHeadsign,
				nameView.getText().toString(), 
				reminder, 
				mReminderDays);
		
		Toast.makeText(this, R.string.trip_info_saved, Toast.LENGTH_SHORT).show();
		finish();
	}
	public void deleteTrip() {
		mDbAdapter.deleteTrip(mTripId, mStopId);
	}
	
	class DialogListener 
		implements DialogInterface.OnClickListener, OnMultiChoiceClickListener {
		private boolean[] mChecks;
		
		DialogListener(boolean[] checks) {
			mChecks = checks;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				// Save repeats
				mReminderDays = TripsDbAdapter.arrayToDays(mChecks);
				final Button repeats = (Button)findViewById(R.id.trip_info_reminder_days);
				repeats.setText(getRepeatText(TripInfoActivity.this, mReminderDays));
			}
			dialog.dismiss();			
		}
		// Multi-click
		public void onClick(DialogInterface dialog, int which, boolean checked) {
			mChecks[which] = checked;			
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		AlertDialog.Builder builder;
		
		switch (id) {
		case REMINDER_DAYS_DIALOG:
			builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.trip_info_reminder_repeat);
			final boolean[] days = TripsDbAdapter.daysToArray(mReminderDays);
			DialogListener listener = new DialogListener(days);

			MultiChoiceHelper.setMultiChoiceItems(builder,
						this,
						R.array.reminder_days,
						days,
						listener);

			dialog = builder
				.setPositiveButton(R.string.trip_info_save, listener)
				.setNegativeButton(R.string.trip_info_dismiss, listener)
				.create();
			break;
		case DELETE_DIALOG:
			builder = new AlertDialog.Builder(this);
			dialog = builder
				.setMessage(R.string.trip_info_delete_trip)
				.setTitle(R.string.trip_info_delete)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.ok, 
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								deleteTrip();
								finish();
							}
				})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
				})
				.create();
			break;
		default:
			dialog = null;
			break;
		}
		return dialog;
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
	
	static String getDepartureTime(Context ctx, long departure) {
		final String fmt = ctx.getResources().getString(R.string.trip_info_depart);
		return String.format(fmt, DateUtils.formatDateTime(ctx, 
				departure, 
				DateUtils.FORMAT_SHOW_TIME|
				DateUtils.FORMAT_NO_NOON|
				DateUtils.FORMAT_NO_MIDNIGHT));		
	}
	
	static String getRepeatText(Context ctx, int days) {
		final Resources res = ctx.getResources();
		
		if ((days & TripsDbAdapter.DAY_ALL) == TripsDbAdapter.DAY_ALL) {
			return res.getString(R.string.trip_info_repeat_everyday);
		}
		if (((days & TripsDbAdapter.DAY_WEEKDAY) == TripsDbAdapter.DAY_WEEKDAY) &&
			 (days & ~TripsDbAdapter.DAY_WEEKDAY) == 0) {
			return res.getString(R.string.trip_info_repeat_weekdays);
		}
		if (days == 0) {
			return res.getString(R.string.trip_info_repeat_norepeat);
		}
		// Otherwise, it's not normal -- format a string 
		final boolean[] array = TripsDbAdapter.daysToArray(days);
		final String fmt = new String(res.getString(R.string.trip_info_repeat_every));
		final String[] dayNames = res.getStringArray(R.array.reminder_days);
	
		StringBuffer buf = new StringBuffer();
		
		// Find the first day
		int rangeStart = 0;
		while (rangeStart < 7) {
			for (; rangeStart < 7 && array[rangeStart] != true; ++rangeStart) {}
			
			if (rangeStart == 7) {
				break;
			}
			
			int rangeEnd = rangeStart+1;
			for (; rangeEnd < 7 && array[rangeEnd] == true; ++rangeEnd) {}	

			if (buf.length() != 0) {
				// TODO: Move to string table
				buf.append(", ");
			}
			
			// Single day?
			if ((rangeEnd-rangeStart) == 1) {
				buf.append(dayNames[rangeStart]);
			}
			else {
				buf.append(dayNames[rangeStart]);
				// TODO: Move to string table
				buf.append(" - ");
				buf.append(dayNames[rangeEnd-1]);
			}
			rangeStart = rangeEnd;
		}
		
		return String.format(fmt, buf.toString());
	}
}
