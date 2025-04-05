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

import com.onesignal.Continue;
import com.onesignal.OneSignal;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.reminders.DeleteRequestListener;
import org.onebusaway.android.io.request.reminders.ObaReminderDeleteRequest;
import org.onebusaway.android.io.request.reminders.ObaReminderRequest;
import org.onebusaway.android.io.request.reminders.ReminderRequestListener;
import org.onebusaway.android.io.request.reminders.model.ReminderResponse;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.ReminderUtils;
import org.onebusaway.android.util.UIUtils;

import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import static org.onebusaway.android.util.ReminderUtils.getRouteShortName;


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

    private static final String STOP_SEQUENCE = ".StopSequence";

    private static final String SERVICE_DATE = ".ServiceDate";

    private static final String VEHICLE_ID = ".VehicleID";

    private static final String TRIP_ID = ".TripID";

    private static final String ALARM_DELETE_PATH = ".DeleteAlarmPath";


    public static void start(Context context, String tripId, String stopId) {
        Intent myIntent = new Intent(context, TripInfoActivity.class);
        myIntent.setData(ObaContract.Trips.buildUri(tripId, stopId));
        context.startActivity(myIntent);
    }

    public static void start(Context context, String tripId, String stopId, String routeId, String routeName, String stopName, long departureTime, String headsign, int stopSequence, long serviceDate, String vehicleID) {
        Intent myIntent = new Intent(context, TripInfoActivity.class);
        myIntent.setData(ObaContract.Trips.buildUri(tripId, stopId));
        myIntent.putExtra(ROUTE_ID, routeId);
        myIntent.putExtra(ROUTE_NAME, routeName);
        myIntent.putExtra(STOP_NAME, stopName);
        myIntent.putExtra(DEPARTURE_TIME, departureTime);
        myIntent.putExtra(HEADSIGN, headsign);
        myIntent.putExtra(STOP_SEQUENCE, stopSequence);
        myIntent.putExtra(SERVICE_DATE, serviceDate);
        myIntent.putExtra(VEHICLE_ID, vehicleID);

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


    public static final class TripInfoFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

        private static final String TAG_DELETE_DIALOG = ".DeleteDialog";

        private static final String[] PROJECTION = {ObaContract.Trips.NAME, ObaContract.Trips.REMINDER, ObaContract.Trips.ROUTE_ID, ObaContract.Trips.HEADSIGN, ObaContract.Trips.DEPARTURE, ObaContract.Trips.ALARM_DELETE_PATH, ObaContract.Trips.STOP_SEQUENCE, ObaContract.Trips.SERVICE_DATE, ObaContract.Trips.TRIP_ID, ObaContract.Trips.VEHICLE_ID};

        private static final int COL_NAME = 0;

        private static final int COL_REMINDER = 1;

        private static final int COL_ROUTE_ID = 2;

        private static final int COL_HEADSIGN = 3;

        private static final int COL_DEPARTURE = 4;

        private static final int COL_ALARM_DELETE_PATH = 5;

        private static final int COL_STOP_SEQUENCE = 6;

        private static final int COL_SERVICE_DATE = 7;

        private static final int COL_TRIP_ID = 8;

        private static final int COL_VEHICLE_ID = 9;


        private Uri mTripUri;

        private String mTripId;

        private String mRouteId;

        private String mRouteName;

        private String mStopId;

        private String mStopName;

        private String mHeadsign;

        private String mTripName;

        private String mAlarmDeletePath;

        private long mDepartTime;

        private int mReminderTime; // DB Value, not selection value

        private int mStopSequence;

        private long mServiceDate;

        private boolean mNewTrip = true;

        private String mVehicleID;

        private View progressView;


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
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
            if (root == null) {
                // Currently in a layout without a container, so no
                // reason to create our view.
                return null;
            }
            return inflater.inflate(R.layout.trip_info, null);
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getActivity(), mTripUri, PROJECTION, null, null, null);
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

            mStopSequence = bundle.getInt(STOP_SEQUENCE);
            mServiceDate = bundle.getLong(SERVICE_DATE);

            mVehicleID = bundle.getString(VEHICLE_ID);
            mAlarmDeletePath = ReminderUtils.getAlarmDeletePath(getContext(), mTripUri);

            // If we get this, update it in the DB.
            if (mRouteName != null) {
                ContentValues values = new ContentValues();
                values.put(ObaContract.Routes.SHORTNAME, mRouteName);
                ObaContract.Routes.insertOrUpdate(getActivity(), mRouteId, values, false);
            }

            String name = bundle.getString(TRIP_NAME);
            if (name != null) {
                mTripName = name;
            }

            mReminderTime = bundle.getInt(REMINDER_TIME, mReminderTime);
            return true;
        }

        private boolean initFromCursor(Cursor cursor) {
            if (cursor == null || cursor.getCount() < 1) {
                // Reminder defaults to 10 in the UI
                mReminderTime = PreferenceUtils.getInt(getString(R.string.preference_key_default_reminder_time), 10);
                return false;
            }
            cursor.moveToFirst();
            mTripName = cursor.getString(COL_NAME);
            mReminderTime = cursor.getInt(COL_REMINDER);

            // If some values weren't set in the bundle, assign them the
            // values in the db.
            if (mRouteId == null) {
                mRouteId = cursor.getString(COL_ROUTE_ID);
            }
            if (mHeadsign == null) {
                mHeadsign = cursor.getString(COL_HEADSIGN);
            }
            if (mDepartTime == 0) {
                mDepartTime = ObaContract.Trips.convertDBToTime(cursor.getInt(COL_DEPARTURE));
            }

            if (mServiceDate == 0) {
                mServiceDate = cursor.getLong(COL_SERVICE_DATE);
            }

            if (mStopSequence == 0) {
                mStopSequence = cursor.getInt(COL_STOP_SEQUENCE);
            }

            if (mTripId == null) {
                mTripId = cursor.getString(COL_TRIP_ID);
            }

            if (mVehicleID == null) {
                mVehicleID = cursor.getString(COL_VEHICLE_ID);
            }

            if (mAlarmDeletePath == null) {
                mAlarmDeletePath = cursor.getString(COL_ALARM_DELETE_PATH);
            }

            // If we don't have the route name, look it up in the DB
            if (mRouteName == null) {
                mRouteName = getRouteShortName(getActivity(), mRouteId);
            }
            if (mStopName == null) {
                mStopName = UIUtils.stringForQuery(getActivity(), Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, mStopId), ObaContract.Stops.NAME);
            }
            logReminderDetails(mReminderTime, "DB");
            return true;
        }

        private void initForm() {
            View view = getView();
            final Spinner reminder = view.findViewById(R.id.trip_info_reminder_time);
            progressView = view.findViewById(R.id.progress);

            String[] reminderTimes = ReminderUtils.getReminderTimes(mDepartTime);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getActivity(),
                    R.layout.spinner_item_reminder,           // custom layout with 24sp text
                    R.id.text_spinner_item,                   // TextView ID from the layout
                    reminderTimes
            );
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_reminder); // optional, for dropdown styling
            reminder.setAdapter(adapter);

            //
            // Static (header values)
            //
            final TextView stopName = view.findViewById(R.id.stop_name);
            stopName.setText(UIUtils.formatDisplayText(mStopName));

            final TextView routeName = view.findViewById(R.id.route_name);
            routeName.setText(UIUtils.formatDisplayText(getString(R.string.trip_info_route, mRouteName)));

            final TextView headsign = view.findViewById(R.id.headsign);
            headsign.setText(UIUtils.formatDisplayText(mHeadsign));

            final TextView departText = view.findViewById(R.id.departure_time);
            departText.setText(getDepartureTime(getActivity(), mDepartTime));

            final TextView tripName = view.findViewById(R.id.name);
            tripName.setText(mTripName);

            int selectionIdx = Math.min(reminderTimes.length - 1, reminderToSelection(mReminderTime));
            reminder.setSelection(selectionIdx);
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
            outState.putLong(SERVICE_DATE, mServiceDate);
            outState.putLong(STOP_SEQUENCE, mStopSequence);
            outState.putString(ALARM_DELETE_PATH, mAlarmDeletePath);
            outState.putString(TRIP_ID, mTripId);
            outState.putString(VEHICLE_ID, mVehicleID);

            View view = getView();
            Spinner reminderView = view.findViewById(R.id.trip_info_reminder_time);
            TextView nameView = view.findViewById(R.id.name);

            final int reminder = selectionToReminder(reminderView.getSelectedItemPosition());
            outState.putString(TRIP_NAME, nameView.getText().toString());
            outState.putInt(REMINDER_TIME, reminder);
        }

        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
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
                onSave();
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
                new ArrivalsListActivity.Builder(getActivity(), mStopId).setStopName(mStopName).start();
                return true;
            }
            return false;
        }

        public void onSave() {
            // If update delete alarm before creating a new one
            if (ReminderUtils.isAlarmExist(getContext(), mTripUri)) {
                requestDeleteAlarm(getContext(), mTripUri);
            }
            OneSignal.getNotifications().requestPermission(false, Continue.none());
            View view = getView();
            final Spinner reminderView = view.findViewById(R.id.trip_info_reminder_time);
            final int reminder = selectionToReminder(reminderView.getSelectedItemPosition());

            PreferenceUtils.saveInt(getString(R.string.preference_key_default_reminder_time), reminder);

            // Convert reminder time minutes to seconds
            int reminderTime = reminder * 60;
            createAlarmRequest(reminderTime);
        }


        private void saveTrip(int reminder, String alarmDeletePath) {
            View view = getView();
            final TextView nameView = view.findViewById(R.id.name);

            ContentValues values = new ContentValues();
            values.put(ObaContract.Trips.ROUTE_ID, mRouteId);
            values.put(ObaContract.Trips.DEPARTURE, ObaContract.Trips.convertTimeToDB(mDepartTime));
            values.put(ObaContract.Trips.HEADSIGN, mHeadsign);
            values.put(ObaContract.Trips.NAME, nameView.getText().toString());
            // Convert seconds to minutes
            values.put(ObaContract.Trips.REMINDER, reminder / 60);
            values.put(ObaContract.Trips.ALARM_DELETE_PATH, alarmDeletePath);
            values.put(ObaContract.Trips.TRIP_ID, mTripId);
            values.put(ObaContract.Trips.SERVICE_DATE, mServiceDate);
            values.put(ObaContract.Trips.STOP_SEQUENCE, mStopSequence);
            values.put(ObaContract.Trips.VEHICLE_ID, mVehicleID);

            ContentResolver cr = getActivity().getContentResolver();
            Cursor c = cr.query(mTripUri, new String[]{ObaContract.Trips._ID}, null, null, null);

            values.put(ObaContract.Trips._ID, mTripId);
            values.put(ObaContract.Trips.STOP_ID, mStopId);
            cr.insert(ObaContract.Trips.CONTENT_URI, values);

            if (c != null) {
                c.close();
            }
        }

        private void createAlarmRequest(int reminderTime) {
            logReminderDetails(reminderTime, "Request");

            progressView.setVisibility(View.VISIBLE);

            String userPushID = Application.getUserPushNotificationID();

            ObaReminderRequest request = createObaReminderRequest(reminderTime, userPushID);

            if (request != null) {
                new Thread(request::call).start();
            } else {
                Toast.makeText(getContext(), R.string.failed_to_set_reminder, Toast.LENGTH_SHORT).show();
            }
        }

        private ObaReminderRequest createObaReminderRequest(int reminderTime, String userPushID) {
            return new ObaReminderRequest.Builder(getContext())
                    .setStopID(mStopId)
                    .setServiceDate(mServiceDate)
                    .setStopSequence(mStopSequence)
                    .setTripID(mTripId)
                    .setUserPushId(userPushID)
                    .setSecondsBefore(reminderTime)
                    .setVehicleID(mVehicleID)
                    .setListener(createReminderRequestListener(reminderTime)).build();
        }

        private ReminderRequestListener createReminderRequestListener(int reminderTime) {
            return new ReminderRequestListener() {
                @Override
                public void onReminderResponseReceived(ReminderResponse response) {
                    handleReminderResponse(reminderTime, response);
                }

                @Override
                public void onReminderResponseFailed() {
                    handleReminderFailure();
                }
            };
        }

        private void handleReminderResponse(int reminderTime, ReminderResponse response) {
            Objects.requireNonNull(getActivity()).runOnUiThread(() -> {
                if (response != null && response.getUrl() != null) {
                    saveTrip(reminderTime, response.getUrl());
                    progressView.setVisibility(View.GONE);
                    Toast.makeText(getActivity(), R.string.trip_info_saved, Toast.LENGTH_SHORT).show();
                    finish();
                    Log.d(TAG, "Reminder set successfully");
                } else {
                    Log.d(TAG, "Response or url is null");
                }
            });
        }

        private void handleReminderFailure() {
            progressView.setVisibility(View.GONE);
            Toast.makeText(getContext(), R.string.failed_to_set_reminder, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Failed to set reminder");
        }

        /**
         * Sends a request to delete a reminder alarm for the specified trip URI and
         * physically removes the reminder from the content provider. Success or failure
         */
        public static void requestDeleteAlarm(Context context, Uri tripUri) {
            String alarmDeletePath = ReminderUtils.getAlarmDeletePath(context, tripUri);
            ObaReminderDeleteRequest deleteRequest = new ObaReminderDeleteRequest();
            deleteRequest.sendDeleteRequest(alarmDeletePath, new DeleteRequestListener() {
                @Override
                public void onDeleteSuccess() {
                    Log.d(TAG, "Delete request successful");

                }

                @Override
                public void onDeleteFailed() {
                    Log.d(TAG, "Delete request failed");
                }
            });

            // Delete it physically
            ContentResolver cr = context.getContentResolver();
            cr.delete(tripUri, null, null);
        }


        public static class DeleteDialog extends DialogFragment {

            @NonNull
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                Bundle args = getArguments();
                final Uri tripUri = args.getParcelable("uri");

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.trip_info_delete_trip).setTitle(R.string.trip_info_delete).setIcon(R.drawable.baseline_delete_forever_48).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (tripUri != null) {
                        requestDeleteAlarm(getContext(), tripUri);
                    }
                    getActivity().finish();
                }).setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());
                return builder.create();
            }

        }

        // This converts what's in the database to what can be displayed in the spinner.
        private static int reminderToSelection(int reminder) {
            switch (reminder) {
                case 1:
                    return 0;
                case 3:
                    return 1;
                case 5:
                    return 2;
                case 10:
                    return 3;
                case 15:
                    return 4;
                case 20:
                    return 5;
                case 25:
                    return 6;
                case 30:
                    return 7;
                default:
                    Log.e(TAG, "Invalid reminder value in DB: " + reminder);
                    return 0;
            }
        }

        private static int selectionToReminder(int selection) {
            switch (selection) {
                case 0:
                    return 1;
                case 1:
                    return 3;
                case 2:
                    return 5;
                case 3:
                    return 10;
                case 4:
                    return 15;
                case 5:
                    return 20;
                case 6:
                    return 25;
                case 7:
                    return 30;
                default:
                    Log.e(TAG, "Invalid selection: " + selection);
                    return 0;
            }
        }

        private void logReminderDetails(int reminderTime, String from) {
            Log.d(TAG, "From = " + from);
            Log.d(TAG, "mStopID: " + mStopId);
            Log.d(TAG, "mRouteId: " + mRouteId);
            Log.d(TAG, "mReminderTime: " + reminderTime);
            Log.d(TAG, "mStopSequence: " + mStopSequence);
            Log.d(TAG, "mServiceDate: " + mServiceDate);
            Log.d(TAG, "mTripID: " + mTripId);
            Log.d(TAG, "mVehicleID: " + mVehicleID);
            Log.d(TAG, "user-push-id: " + OneSignal.getUser().getPushSubscription().getId());
        }
    }

    static String getDepartureTime(Context ctx, long departure) {
        return ctx.getString(R.string.trip_info_depart, DateUtils.formatDateTime(ctx, departure, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT));
    }
}
