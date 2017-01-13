/*
 * Copyright (C) 2011-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.tripservice.TripService;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIUtils;

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
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class TripInfoActivity extends AppCompatActivity {

    private static final String TAG = "TripInfoActivity";

    private static final String ROUTE_ID = ".RouteId";

    private static final String ROUTE_NAME = ".RouteName";

    private static final String STOP_NAME = ".StopName";

    private static final String HEADSIGN = ".Headsign";

    private static final String DEPARTURE_TIME = ".Depart";

    // Save/restore values
    private static final String TRIP_NAME = ".TripName";

    private static final String REMINDER_TIME = ".ReminderTime";

    private static final String REMINDER_DAYS = ".ReminderDays";

    public static void start(Context context, String tripId, String stopId) {
        Intent myIntent = new Intent(context, TripInfoActivity.class);
        myIntent.setData(ObaContract.Trips.buildUri(tripId, stopId));
        context.startActivity(myIntent);
    }

    public static void start(Context context,
            String tripId,
            String stopId,
            String routeId,
            String routeName,
            String stopName,
            long departureTime,
            String headsign) {
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
        UIUtils.setupActionBar(this);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            TripInfoFragment content = new TripInfoFragment();
            content.setArguments(FragmentUtils.getIntentArgs(getIntent()));

            fm.beginTransaction().add(android.R.id.content, content).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            //UIHelp.goHome(this);
            // Since this screen isn't part of a defined heirarchy, we always
            // go up from here.
            finish();
            return true;
        }
        return false;
    }

    TripInfoFragment getTripInfoFragment() {
        FragmentManager fm = getSupportFragmentManager();
        return (TripInfoFragment) fm.findFragmentById(android.R.id.content);
    }

    public static final class TripInfoFragment extends Fragment
            implements LoaderManager.LoaderCallbacks<Cursor> {

        private static final String TAG_DELETE_DIALOG = ".DeleteDialog";

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

        private boolean mNewTrip = true;


        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            setHasOptionsMenu(true);

            if (savedInstanceState != null) {
                initFromBundle(savedInstanceState);
                initForm();
            } else if (initFromBundle(getArguments())) {
                getLoaderManager().initLoader(0, null, this);
            } else {
                Log.e(TAG, "Information missing from intent");
                return;
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater,
                ViewGroup root, Bundle savedInstanceState) {
            if (root == null) {
                // Currently in a layout without a container, so no
                // reason to create our view.
                return null;
            }
            return inflater.inflate(R.layout.trip_info, null);
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getActivity(), mTripUri,
                    PROJECTION, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mNewTrip = !initFromCursor(data);
            initForm();
            getActivity().supportInvalidateOptionsMenu();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }

        @Override
        public void onStart() {
            ObaAnalytics.reportActivityStart(getActivity());
            super.onStart();
        }

        private boolean initFromBundle(Bundle bundle) {
            final Uri data = bundle.getParcelable(FragmentUtils.URI);
            if (data == null) {
                return false;
            }
            List<String> segments = data.getPathSegments();
            mTripId = segments.get(1);
            mStopId = segments.get(2);
            mTripUri = data;

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
                ContentValues values = new ContentValues();
                values.put(ObaContract.Routes.SHORTNAME, mRouteName);
                ObaContract.Routes
                        .insertOrUpdate(getActivity(), mRouteId, values, false);
            }
            String name = bundle.getString(TRIP_NAME);
            if (name != null) {
                mTripName = name;
            }

            mReminderTime = bundle.getInt(REMINDER_TIME, mReminderTime);
            mReminderDays = bundle.getInt(REMINDER_DAYS, mReminderDays);
            return true;
        }

        private boolean initFromCursor(Cursor cursor) {
            if (cursor == null || cursor.getCount() < 1) {
                // Reminder defaults to 10 in the UI
                mReminderTime = 10;
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
                mDepartTime = ObaContract.Trips.convertDBToTime(cursor
                        .getInt(COL_DEPARTURE));
            }

            // If we don't have the route name, look it up in the DB
            if (mRouteName == null) {
                mRouteName = TripService.getRouteShortName(getActivity(), mRouteId);
            }
            if (mStopName == null) {
                mStopName = UIUtils.stringForQuery(getActivity(), Uri.withAppendedPath(
                                ObaContract.Stops.CONTENT_URI, mStopId),
                        ObaContract.Stops.NAME
                );
            }
            return true;
        }

        private void initForm() {
            View view = getView();
            final Spinner reminder = (Spinner) view.findViewById(R.id.trip_info_reminder_time);
            ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
                    getActivity(), R.array.reminder_time, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            reminder.setAdapter(adapter);

            //
            // Static (header values)
            //
            final TextView stopName = (TextView) view.findViewById(R.id.stop_name);
            stopName.setText(UIUtils.formatDisplayText(mStopName));

            final TextView routeName = (TextView) view.findViewById(R.id.route_name);
            routeName.setText(
                    UIUtils.formatDisplayText(getString(R.string.trip_info_route, mRouteName)));

            final TextView headsign = (TextView) view.findViewById(R.id.headsign);
            headsign.setText(UIUtils.formatDisplayText(mHeadsign));

            final TextView departText = (TextView) view.findViewById(R.id.departure_time);
            departText.setText(getDepartureTime(getActivity(), mDepartTime));

            final TextView tripName = (TextView) view.findViewById(R.id.name);
            tripName.setText(mTripName);

            reminder.setSelection(reminderToSelection(mReminderTime));

            final Button repeats = (Button) view.findViewById(R.id.trip_info_reminder_days);
            repeats.setText(getRepeatText(getActivity(), mReminderDays));
            //
            // Buttons
            //
            repeats.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showReminderDaysDialog();
                }
            });
        }

        void finish() {
            // TODO: We want to be a better citizen, we should not finish our parents
            // activity in the case we have some form of dual pane mode for larger screens.
            // But for now, the retains original, pre-fragment functionality.
            getActivity().finish();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);

            outState.putParcelable(FragmentUtils.URI, mTripUri);
            outState.putString(ROUTE_ID, mRouteId);
            outState.putString(ROUTE_NAME, mRouteName);
            outState.putString(STOP_NAME, mStopName);
            outState.putString(HEADSIGN, mHeadsign);
            outState.putLong(DEPARTURE_TIME, mDepartTime);

            View view = getView();
            Spinner reminderView = (Spinner) view.findViewById(R.id.trip_info_reminder_time);
            TextView nameView = (TextView) view.findViewById(R.id.name);

            final int reminder = selectionToReminder(reminderView
                    .getSelectedItemPosition());
            outState.putString(TRIP_NAME, nameView.getText().toString());
            outState.putInt(REMINDER_TIME, reminder);
            outState.putInt(REMINDER_DAYS, mReminderDays);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.trip_info_options, menu);
        }

        @Override
        public void onPrepareOptionsMenu(Menu menu) {
            menu.findItem(R.id.trip_info_delete).setVisible(!mNewTrip);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.trip_info_save) {
                saveTrip();
            } else if (id == R.id.trip_info_delete) {
                Bundle args = new Bundle();
                args.putParcelable("uri", mTripUri);
                DeleteDialog dialog = new DeleteDialog();
                dialog.setArguments(args);
                dialog.show(getActivity().getSupportFragmentManager(), TAG_DELETE_DIALOG);
            } else if (id == R.id.show_route) {
                RouteInfoActivity.start(getActivity(), mRouteId);
                return true;
            } else if (id == R.id.show_stop) {
                new ArrivalsListActivity.Builder(getActivity(), mStopId)
                        .setStopName(mStopName)
                        .start();
                return true;
            }
            return false;
        }

        public void saveTrip() {
            // Things that need updating:
            // Any constant values (trip info not editable by user)
            // Trip name
            // Reminder time
            // Repeats
            //
            View view = getView();
            final Spinner reminderView = (Spinner) view.findViewById(R.id.trip_info_reminder_time);
            final TextView nameView = (TextView) view.findViewById(R.id.name);

            final int reminder = selectionToReminder(reminderView
                    .getSelectedItemPosition());

            ContentValues values = new ContentValues();
            values.put(ObaContract.Trips.ROUTE_ID, mRouteId);
            values.put(ObaContract.Trips.DEPARTURE, ObaContract.Trips
                    .convertTimeToDB(mDepartTime));
            values.put(ObaContract.Trips.HEADSIGN, mHeadsign);
            values.put(ObaContract.Trips.NAME, nameView.getText().toString());
            values.put(ObaContract.Trips.REMINDER, reminder);
            values.put(ObaContract.Trips.DAYS, mReminderDays);

            // Insert or update?
            ContentResolver cr = getActivity().getContentResolver();
            Cursor c = cr.query(mTripUri, new String[]{ObaContract.Trips._ID},
                    null, null, null);
            if (c != null && c.getCount() > 0) {
                // Update
                cr.update(mTripUri, values, null, null);
            } else {
                values.put(ObaContract.Trips._ID, mTripId);
                values.put(ObaContract.Trips.STOP_ID, mStopId);
                cr.insert(ObaContract.Trips.CONTENT_URI, values);
            }
            if (c != null) {
                c.close();
            }
            TripService.scheduleAll(getActivity());

            Toast.makeText(getActivity(), R.string.trip_info_saved, Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        void showReminderDaysDialog() {
            final boolean[] checks = ObaContract.Trips.daysToArray(mReminderDays);
            Bundle args = new Bundle();
            args.putBooleanArray(ReminderDaysDialog.CHECKS, checks);
            ReminderDaysDialog frag = new ReminderDaysDialog();
            frag.setArguments(args);
            frag.show(getActivity().getSupportFragmentManager(), ".ReminderDaysDialog");
        }

        public static class ReminderDaysDialog extends DialogFragment
                implements DialogInterface.OnMultiChoiceClickListener,
                DialogInterface.OnClickListener {

            static final String CHECKS = ".checks";

            private boolean[] mChecks;

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                Bundle args = getArguments();
                mChecks = args.getBooleanArray(CHECKS);
                if (savedInstanceState != null) {
                    mChecks = args.getBooleanArray(CHECKS);
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                return builder.setTitle(R.string.trip_info_reminder_repeat)
                        .setMultiChoiceItems(R.array.reminder_days, mChecks, this)
                        .setPositiveButton(R.string.trip_info_save, this)
                        .setNegativeButton(R.string.trip_info_dismiss, null)
                        .create();
            }

            @Override
            public void onSaveInstanceState(Bundle outState) {
                outState.putBooleanArray(CHECKS, mChecks);
            }

            @Override
            public void onClick(DialogInterface dialog, int which) {
                TripInfoActivity act = (TripInfoActivity) getActivity();
                // Get the fragment we want...
                TripInfoFragment frag = act.getTripInfoFragment();
                frag.setReminderDays(mChecks);
                dialog.dismiss();
            }

            @Override
            public void onClick(DialogInterface arg0, int which, boolean isChecked) {
                mChecks[which] = isChecked;
            }
        }

        private void setReminderDays(boolean[] checks) {
            View view = getView();
            mReminderDays = ObaContract.Trips.arrayToDays(checks);
            final Button repeats = (Button) view.findViewById(R.id.trip_info_reminder_days);
            repeats.setText(getRepeatText(getActivity(), mReminderDays));
        }

        public static class DeleteDialog extends DialogFragment {

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                Bundle args = getArguments();
                final Uri tripUri = args.getParcelable("uri");

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder
                        .setMessage(R.string.trip_info_delete_trip)
                        .setTitle(R.string.trip_info_delete)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        ContentResolver cr = getActivity().getContentResolver();
                                        cr.delete(tripUri, null, null);
                                        TripService.scheduleAll(getActivity());
                                        getActivity().finish();
                                    }
                                }
                        )
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                }
                        );
                return builder.create();
            }
        }

        // This converts what's in the database to what can be displayed in the spinner.
        private static int reminderToSelection(int reminder) {
            switch (reminder) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                case 3:
                    return 2;
                case 5:
                    return 3;
                case 10:
                    return 4;
                case 15:
                    return 5;
                case 20:
                    return 6;
                case 25:
                    return 7;
                case 30:
                    return 8;
                default:
                    Log.e(TAG, "Invalid reminder value in DB: " + reminder);
                    return 0;
            }
        }

        private static int selectionToReminder(int selection) {
            switch (selection) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                case 2:
                    return 3;
                case 3:
                    return 5;
                case 4:
                    return 10;
                case 5:
                    return 15;
                case 6:
                    return 20;
                case 7:
                    return 25;
                case 8:
                    return 30;
                default:
                    Log.e(TAG, "Invalid selection: " + selection);
                    return 0;
            }
        }
    }

    static String getDepartureTime(Context ctx, long departure) {
        return ctx.getString(R.string.trip_info_depart,
                DateUtils.formatDateTime(ctx,
                        departure,
                        DateUtils.FORMAT_SHOW_TIME |
                                DateUtils.FORMAT_NO_NOON |
                                DateUtils.FORMAT_NO_MIDNIGHT
                )
        );
    }

    static String getRepeatText(Context ctx, int days) {
        final Resources res = ctx.getResources();

        if ((days & ObaContract.Trips.DAY_ALL) == ObaContract.Trips.DAY_ALL) {
            return res.getString(R.string.trip_info_repeat_everyday);
        }
        if (((days & ObaContract.Trips.DAY_WEEKDAY) == ObaContract.Trips.DAY_WEEKDAY)
                && (days & ~ObaContract.Trips.DAY_WEEKDAY) == 0) {
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
            for (; rangeStart < 7 && !array[rangeStart]; ++rangeStart) {
            }

            if (rangeStart == 7) {
                break;
            }

            int rangeEnd = rangeStart + 1;
            for (; rangeEnd < 7 && array[rangeEnd]; ++rangeEnd) {
            }

            if (buf.length() != 0) {
                // TODO: Move to string table
                buf.append(", ");
            }

            // Single day?
            if ((rangeEnd - rangeStart) == 1) {
                buf.append(dayNames[rangeStart]);
            } else {
                buf.append(dayNames[rangeStart]);
                // TODO: Move to string table
                buf.append(" - ");
                buf.append(dayNames[rangeEnd - 1]);
            }
            rangeStart = rangeEnd;
        }

        return res.getString(R.string.trip_info_repeat_every, buf.toString());
    }
}
