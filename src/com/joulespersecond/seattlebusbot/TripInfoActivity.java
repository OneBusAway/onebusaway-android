package com.joulespersecond.seattlebusbot;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.joulespersecond.oba.provider.ObaContract;

public class TripInfoActivity extends Activity {
    private static final String TAG = "TripInfoActivity";

    private static final int DELETE_DIALOG = 2;

    private static final String TRIP_ID = ".TripId";
    private static final String ROUTE_ID = ".RouteId";
    private static final String ROUTE_NAME = ".RouteName";
    private static final String STOP_ID = ".StopId";
    private static final String STOP_NAME = ".StopName";
    private static final String HEADSIGN = ".Headsign";
    private static final String DEPARTURE_TIME = ".Depart";
    // Save/restore values
    private static final String TRIP_NAME = ".TripName";
    private static final String REMINDER_TIME = ".ReminderTime";
    private static final String REMINDER_DAYS = ".ReminderDays";

    private static final String[] PROJECTION = {
        ObaContract.Trips.NAME,
        ObaContract.Trips.REMINDER,
        ObaContract.Trips.DAYS,
        ObaContract.Trips.ROUTE_ID,
        ObaContract.Trips.HEADSIGN,
        ObaContract.Trips.DEPARTURE
    };
    private static final int COL_NAME = 0;
    private static final int COL_REMINDER = 1;
    private static final int COL_DAYS = 2;
    private static final int COL_ROUTE_ID = 3;
    private static final int COL_HEADSIGN = 4;
    private static final int COL_DEPARTURE = 5;

    private Uri mTripUri;
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

    public static void start(Context context, String tripId, String stopId) {
        Intent myIntent = new Intent(context, TripInfoActivity.class);
        myIntent.setData(ObaContract.Trips.buildUri(tripId, stopId));
        context.startActivity(myIntent);
    }
    public static void start(Context context,
            String tripId, String stopId,
            String routeId, String routeName, String stopName,
            long departureTime, String headsign) {
        Intent myIntent = new Intent(context, TripInfoActivity.class);
        myIntent.setData(ObaContract.Trips.buildUri(tripId, stopId));
        myIntent.putExtra(ROUTE_ID, routeId);
        myIntent.putExtra(ROUTE_NAME, routeName);
        myIntent.putExtra(STOP_NAME, stopName);
        myIntent.putExtra(DEPARTURE_TIME, departureTime);
        myIntent.putExtra(HEADSIGN, headsign);
        context.startActivity(myIntent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trip_info);

        if (!initFromIntent(getIntent())) {
            Log.e(TAG, "Information missing from intent");
            finish();
            return;
        }

        boolean newTrip = !initFromDB();
        initForm(newTrip);
    }

    private boolean initFromIntent(Intent intent) {
        final Bundle bundle = intent.getExtras();
        final Uri data = intent.getData();
        if (data != null) {
            List<String> segments = data.getPathSegments();
            mTripId = segments.get(1);
            mStopId = segments.get(2);
            mTripUri = data;
        }
        else if (bundle != null) {
            // Backward compatibility
            mTripId = bundle.getString(TRIP_ID);
            mStopId = bundle.getString(STOP_ID);
            mTripUri = ObaContract.Trips.buildUri(mTripId, mStopId);
        }
        if (mTripId == null || mStopId == null) {
            return false;
        }
        if (bundle != null) {
            mRouteId = bundle.getString(ROUTE_ID);
            mHeadsign = bundle.getString(HEADSIGN);
            mDepartTime = bundle.getLong(DEPARTURE_TIME);

            mStopName = bundle.getString(STOP_NAME);
            mRouteName = bundle.getString(ROUTE_NAME);
            // If we get this, update it in the DB.
            if (mRouteName != null) {
                ContentValues values = new ContentValues();
                values.put(ObaContract.Routes.SHORTNAME, mRouteName);
                ObaContract.Routes.insertOrUpdate(this, mRouteId, values, false);
            }
        }
        return true;
    }
    private boolean initFromDB() {
        // Look up the trip in the database.
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(mTripUri, PROJECTION, null, null, null);
        if (cursor == null || cursor.getCount() < 1) {
            // Reminder defaults to 10 in the UI
            mReminderTime = 10;
            if (cursor != null) {
                cursor.close();
            }
            return false;
        }
        cursor.moveToFirst();
        mTripName = cursor.getString(COL_NAME);
        mReminderTime = cursor.getInt(COL_REMINDER);
        mReminderDays = cursor.getInt(COL_DAYS);

        // If some values weren't set in the bundle, assign them the
        // values in the db.
        if (mRouteId == null) {
            mRouteId = cursor.getString(COL_ROUTE_ID);
        }
        if (mHeadsign == null) {
            mHeadsign = cursor.getString(COL_HEADSIGN);
        }
        if (mDepartTime == 0) {
            mDepartTime = ObaContract.Trips.convertDBToTime(
                    cursor.getInt(COL_DEPARTURE));
        }

        // If we don't have the route name, look it up in the DB
        if (mRouteName == null) {
            mRouteName = TripService.getRouteShortName(this, mRouteId);
        }
        if (mStopName == null) {
            mStopName = UIHelp.stringForQuery(this,
                    Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, mStopId),
                    ObaContract.Stops.NAME);
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
        routeName.setText(getString(R.string.trip_info_route,  mRouteName));

        final TextView headsign = (TextView)findViewById(R.id.headsign);
        headsign.setText(mHeadsign);

        final TextView departText = (TextView)findViewById(R.id.departure_time);
        departText.setText(getDepartureTime(this, mDepartTime));

        setUserValues();

        //
        // Buttons
        //
        final Button repeats = (Button)findViewById(R.id.trip_info_reminder_days);
        repeats.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showReminderDaysDialog();
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
    private void setUserValues() {
        final TextView tripName = (TextView)findViewById(R.id.name);
        tripName.setText(mTripName);

        final Spinner reminder = (Spinner)findViewById(R.id.trip_info_reminder_time);
        reminder.setSelection(reminderToSelection(mReminderTime));

        final Button repeats = (Button)findViewById(R.id.trip_info_reminder_days);
        repeats.setText(getRepeatText(this, mReminderDays));
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        final Spinner reminderView = (Spinner)findViewById(R.id.trip_info_reminder_time);
        final TextView nameView = (TextView)findViewById(R.id.name);

        final int reminder = selectionToReminder(reminderView.getSelectedItemPosition());
        outState.putString(TRIP_NAME, nameView.getText().toString());
        outState.putInt(REMINDER_TIME, reminder);
        outState.putInt(REMINDER_DAYS, mReminderDays);
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        String name = savedInstanceState.getString(TRIP_NAME);
        if (name != null) {
            mTripName = name;
        }

        mReminderTime = savedInstanceState.getInt(REMINDER_TIME, mReminderTime);
        mReminderDays = savedInstanceState.getInt(REMINDER_DAYS, mReminderDays);
        setUserValues();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.trip_info_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.show_route) {
            RouteInfoActivity.start(this, mRouteId);
            return true;
        }
        else if (item.getItemId() == R.id.show_stop) {
            StopInfoActivity.start(this, mStopId, mStopName);
            return true;
        }
        return false;
    }
    //
    // Buttons (1.6 only)
    //
    /*
    public final void onRepeatClick(View v) {
        showDialog(REMINDER_DAYS_DIALOG);
    }
    public final void onSaveClick(View v) {
        saveTrip();
    }
    public final void onCancelClick(View v) {
        finish();
    }
    public final void onDeleteClick(View v) {
        showDialog(DELETE_DIALOG);
    }
    */

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

        ContentValues values = new ContentValues();
        values.put(ObaContract.Trips.ROUTE_ID, mRouteId);
        values.put(ObaContract.Trips.DEPARTURE,
                ObaContract.Trips.convertTimeToDB(mDepartTime));
        values.put(ObaContract.Trips.HEADSIGN, mHeadsign);
        values.put(ObaContract.Trips.NAME, nameView.getText().toString());
        values.put(ObaContract.Trips.REMINDER, reminder);
        values.put(ObaContract.Trips.DAYS, mReminderDays);

        // Insert or update?
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(mTripUri,
                new String[] { ObaContract.Trips._ID },
                null, null, null);
        if (c != null && c.getCount() > 0) {
            // Update
            cr.update(mTripUri, values, null, null);
        }
        else {
            values.put(ObaContract.Trips._ID, mTripId);
            values.put(ObaContract.Trips.STOP_ID, mStopId);
            cr.insert(ObaContract.Trips.CONTENT_URI, values);
        }
        if (c != null) {
            c.close();
        }
        TripService.scheduleAll(this);

        Toast.makeText(this, R.string.trip_info_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
    private void deleteTrip() {
        ContentResolver cr = getContentResolver();
        cr.delete(mTripUri, null, null);
        TripService.scheduleAll(this);
    }

    private static final int REMINDER_DAYS_DIALOG = 1;

    void showReminderDaysDialog() {
        final boolean[] checks = ObaContract.Trips.daysToArray(mReminderDays);

        new MultiChoiceActivity.Builder(this)
            .setTitle(R.string.trip_info_reminder_repeat)
            .setItems(R.array.reminder_days, checks)
            .setPositiveButton(R.string.trip_info_save)
            .setNegativeButton(R.string.trip_info_dismiss)
            .startForResult(REMINDER_DAYS_DIALOG);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REMINDER_DAYS_DIALOG:
            if (resultCode == Activity.RESULT_OK) {
                setReminderDaysFromIntent(data);
            }
            break;
        default:
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setReminderDaysFromIntent(Intent data) {
        final boolean checks[] =
            data.getBooleanArrayExtra(MultiChoiceActivity.CHECKED_ITEMS);
        if (checks == null) {
            return;
        }
        mReminderDays = ObaContract.Trips.arrayToDays(checks);
        final Button repeats = (Button)findViewById(R.id.trip_info_reminder_days);
        repeats.setText(getRepeatText(TripInfoActivity.this, mReminderDays));
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        AlertDialog.Builder builder;

        switch (id) {
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
        case 0:     return 0;
        case 1:     return 1;
        case 3:     return 2;
        case 5:     return 3;
        case 10:    return 4;
        case 15:    return 5;
        case 20:    return 6;
        case 25:    return 7;
        case 30:    return 8;
        default:
            Log.e(TAG, "Invalid reminder value in DB: " + reminder);
            return 0;
        }
    }
    private static int selectionToReminder(int selection) {
        switch (selection) {
        case 0:     return 0;
        case 1:     return 1;
        case 2:     return 3;
        case 3:     return 5;
        case 4:     return 10;
        case 5:     return 15;
        case 6:     return 20;
        case 7:     return 25;
        case 8:     return 30;
        default:
            Log.e(TAG, "Invalid selection: " + selection);
            return 0;
        }
    }

    static String getDepartureTime(Context ctx, long departure) {
        return ctx.getString(R.string.trip_info_depart,
                DateUtils.formatDateTime(ctx,
                    departure,
                    DateUtils.FORMAT_SHOW_TIME|
                    DateUtils.FORMAT_NO_NOON|
                    DateUtils.FORMAT_NO_MIDNIGHT));
    }

    static String getRepeatText(Context ctx, int days) {
        final Resources res = ctx.getResources();

        if ((days & ObaContract.Trips.DAY_ALL) == ObaContract.Trips.DAY_ALL) {
            return res.getString(R.string.trip_info_repeat_everyday);
        }
        if (((days & ObaContract.Trips.DAY_WEEKDAY) == ObaContract.Trips.DAY_WEEKDAY) &&
             (days & ~ObaContract.Trips.DAY_WEEKDAY) == 0) {
            return res.getString(R.string.trip_info_repeat_weekdays);
        }
        if (days == 0) {
            return res.getString(R.string.trip_info_repeat_norepeat);
        }
        // Otherwise, it's not normal -- format a string
        final boolean[] array = ObaContract.Trips.daysToArray(days);
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

        return res.getString(R.string.trip_info_repeat_every, buf.toString());
    }
}
