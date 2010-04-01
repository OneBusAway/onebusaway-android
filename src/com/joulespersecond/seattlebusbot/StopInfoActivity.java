/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaArrivalInfo;
import com.joulespersecond.oba.ObaData;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;

public class StopInfoActivity extends ListActivity {
    private static final String TAG = "StopInfoActivity";
    private static final long RefreshPeriod = 60*1000;

    private static final String STOP_ID = ".StopId";
    private static final String STOP_NAME = ".StopName";
    private static final String STOP_DIRECTION = ".StopDir";

    private static final String CURRENT_EDIT = ".CurrentEdit";

    private ObaResponse mResponse;
    private ObaStop mStop;
    private String mStopId;
    private String mStopName;
    private Uri mStopUri;
    private Timer mTimer;
    private long mResponseTime = 0;
    private ArrayList<String> mRoutesFilter;

    // Cached UI items
    private View mNameContainerView;
    private View mEditNameContainerView;
    private TextView mNameView;
    private EditText mEditNameView;
    private ImageView mFavoriteView;
    private TextView mEmptyText;
    private View mDirectionView;
    private View mFilterGroup;
    private View mResponseError;
    private boolean mInNameEdit;

    private AsyncTask<String,?,?> mAsyncTask;

    private ContentQueryMap mTripsForStop;

    private boolean mFavorite = false;
    private String mStopUserName;

    public static void start(Context context, String stopId) {
        context.startActivity(makeIntent(context, stopId));
    }
    public static void start(Context context, String stopId, String stopName) {
        context.startActivity(makeIntent(context, stopId, stopName));
    }
    public static void start(Context context,
                        String stopId,
                        String stopName,
                        String stopDir) {
        context.startActivity(makeIntent(context, stopId, stopName, stopDir));
    }
    public static void start(Context context, ObaStop stop) {
        context.startActivity(makeIntent(context, stop));
    }
    public static Intent makeIntent(Context context, String stopId) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        return myIntent;
    }
    public static Intent makeIntent(Context context, String stopId, String stopName) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        myIntent.putExtra(STOP_NAME, stopName);
        return myIntent;
    }
    public static Intent makeIntent(Context context,
                        String stopId,
                        String stopName,
                        String stopDir) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        myIntent.putExtra(STOP_NAME, stopName);
        myIntent.putExtra(STOP_DIRECTION, stopDir);
        return myIntent;
    }
    public static Intent makeIntent(Context context, ObaStop stop) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
        myIntent.putExtra(STOP_NAME, stop.getName());
        myIntent.putExtra(STOP_DIRECTION, stop.getDirection());
        return myIntent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.stop_info);
        setListAdapter(new StopInfoListAdapter());

        final Intent intent = getIntent();
        final Bundle bundle = intent.getExtras();
        final Uri data = intent.getData();
        if (data != null) {
            mStopId = data.getLastPathSegment();
        }
        else if (bundle != null) {
            // This is for backward compatibility
            mStopId = bundle.getString(STOP_ID);
        }
        else {
            Log.e(TAG, "No stop ID!");
            finish();
            return;
        }

        mNameContainerView = findViewById(R.id.name_container);
        mEditNameContainerView = findViewById(R.id.edit_name_container);
        mNameView = (TextView)findViewById(R.id.stop_name);
        mEditNameView = (EditText)findViewById(R.id.edit_name);
        mFavoriteView = (ImageView)findViewById(R.id.stop_favorite);
        mEmptyText = (TextView)findViewById(android.R.id.empty);
        mResponseError = findViewById(R.id.response_error);
        mDirectionView = findViewById(R.id.direction);
        mFilterGroup = findViewById(R.id.filter_group);

        // This is useful to have around
        mStopUri = Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, mStopId);

        getUserInfo();

        initHeader();
        setHeader(bundle);
        UIHelp.setChildClickable(this, R.id.show_all, mShowAllClick);

        mRoutesFilter = ObaContract.StopRouteFilters.get(this, mStopId);
        mTripsForStop = getTripsForStop();

        Object response = getLastNonConfigurationInstance();
        if (response != null) {
            setResponse((ObaResponse)response, false);
        }
        else {
            getStopInfo(false);
        }

        if (savedInstanceState != null) {
            final String lastEdit = savedInstanceState.getString(CURRENT_EDIT);
            if (lastEdit != null) {
                beginNameEdit(lastEdit);
            }
        }
    }
    @Override
    public void onDestroy() {
        mTripsForStop.close();
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        super.onDestroy();
    }
    @Override
    public Object onRetainNonConfigurationInstance() {
        return mResponse;
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mInNameEdit) {
            outState.putString(CURRENT_EDIT, mEditNameView.getText().toString());
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stop_info_options, menu);
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.toggle_favorite)
            .setTitle(mFavorite ?
                    R.string.stop_info_option_removestar :
                    R.string.stop_info_option_addstar);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.show_on_map) {
            if (mResponse != null) {
                MapViewActivity.start(this,
                        mStopId,
                        mStop.getLatitude(),
                        mStop.getLongitude());
            }
            return true;
        }
        else if (id == R.id.refresh) {
            getStopInfo(true);
            return true;
        }
        else if (id == R.id.filter) {
            if (mResponse != null) {
                showRoutesFilterDialog();
            }
        }
        else if (id == R.id.edit_name) {
            beginNameEdit(null);
        }
        else if (id == R.id.toggle_favorite) {
            toggleFavorite();
        }
        return false;
    }
    @Override
    public void onPause() {
        mTripsForStop.setKeepUpdated(false);
        mTimer.cancel();
        mTimer = null;
        super.onPause();
    }
    @Override
    public void onResume() {
        if (mTimer == null) {
            mTimer = new Timer();
        }
        mTripsForStop.setKeepUpdated(true);
        mTripsForStop.requery();
        ((BaseAdapter)getListAdapter()).notifyDataSetChanged();

        // If our timer would have gone off, then refresh.
        if (System.currentTimeMillis() > (mResponseTime+RefreshPeriod)) {
            getStopInfo(true);
        }

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mRefreshHandler.post(mRefresh);
            }
        }, RefreshPeriod, RefreshPeriod);
        super.onResume();
    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final StopInfo stop = (StopInfo)getListView().getItemAtPosition(position);
        if (stop == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.stop_info_item_options_title);

        final ObaRoute route = stop.getRoute(mResponse);
        final String url = route != null ? route.getUrl() : null;
        final boolean hasUrl = !TextUtils.isEmpty(url);
        // Check to see if the trip name is visible.
        // (we don't have any other state, so this is good enough)
        int options;
        View tripView = v.findViewById(R.id.trip_info);
        if (tripView.getVisibility() != View.GONE) {
            if (hasUrl) {
                options = R.array.stop_item_options_edit;
            }
            else {
                options = R.array.stop_item_options_edit_noschedule;
            }
        }
        else if (hasUrl) {
            options = R.array.stop_item_options;
        }
        else {
            options = R.array.stop_item_options_noschedule;
        }
        builder.setItems(options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case 0:
                    goToTrip(stop);
                    break;
                case 1:
                    goToRoute(stop);
                    break;
                case 2:
                    ArrayList<String> routes = new ArrayList<String>(1);
                    routes.add(stop.getInfo().getRouteId());
                    setRoutesFilter(routes);
                    break;
                case 3:
                    UIHelp.goToUrl(StopInfoActivity.this, url);
                    break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(this);
        dialog.show();
    }

    private static final int FILTER_DIALOG_RESULT = 1;

    private void showRoutesFilterDialog() {
        final ObaArray<ObaRoute> routes = mStop.getRoutes();
        final int len = routes.length();
        final ArrayList<String> filter = mRoutesFilter;

        //mRouteIds = new ArrayList<String>(len);
        String[] items = new String[len];
        boolean[] checks = new boolean[len];

        // Go through all the stops, add them to the Ids and Names
        // For each stop, if it is in the enabled list, mark it as checked.
        for (int i=0; i < len; ++i) {
            final ObaRoute route = routes.get(i);
            //final String id = route.getId();
            //mRouteIds.add(i, id);
            items[i] = route.getShortName();
            if (filter.contains(route.getId())) {
                checks[i] = true;
            }
        }
        new MultiChoiceActivity.Builder(this)
            .setTitle(R.string.stop_info_filter_title)
            .setItems(items, checks)
            .setPositiveButton(R.string.stop_info_save)
            .setNegativeButton(R.string.stop_info_cancel)
            .startForResult(FILTER_DIALOG_RESULT);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case FILTER_DIALOG_RESULT:
            if (resultCode == Activity.RESULT_OK) {
                setRoutesFilterFromIntent(data);
            }
            break;
        default:
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
    private void setRoutesFilterFromIntent(Intent intent) {
        final boolean[] checks =
            intent.getBooleanArrayExtra(MultiChoiceActivity.CHECKED_ITEMS);
        if (checks == null) {
            return;
        }

        final int len = checks.length;
        final ArrayList<String> newFilter = new ArrayList<String>(len);

        final ObaArray<ObaRoute> routes = mStop.getRoutes();
        assert(routes.length() == len);

        for (int i=0; i < len; ++i) {
            final ObaRoute route = routes.get(i);
            if (checks[i]) {
                newFilter.add(route.getId());
            }
        }
        // If the size of the filter is the number of routes
        // (i.e., the user selected every checkbox) act then
        // don't select any.
        if (newFilter.size() == len) {
            newFilter.clear();
        }

        setRoutesFilter(newFilter);
    }

    final class StopInfoListAdapter extends BaseAdapter {
        private ArrayList<StopInfo> mInfo;

        public StopInfoListAdapter() {
            mInfo = new ArrayList<StopInfo>();
        }
        public int getCount() {
            return mInfo.size();
        }
        public Object getItem(int position) {
            // Replace this when we add a real stop info array
            return mInfo.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup newView;
            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                newView = (ViewGroup)inflater.inflate(R.layout.stop_info_listitem, null);
            }
            else {
                newView = (ViewGroup)convertView;
            }
            setData(newView, position);
            return newView;
        }
        public boolean hasStableIds() {
            return false;
        }

        public void setData(ObaArray<ObaArrivalInfo> arrivals) {
            mInfo = StopInfo.convertObaArrivalInfo(StopInfoActivity.this, arrivals, mRoutesFilter);
            setFilterHeader();
            notifyDataSetChanged();
        }
        private void setData(ViewGroup view, int position) {
            TextView route = (TextView)view.findViewById(R.id.route);
            TextView destination = (TextView)view.findViewById(R.id.destination);
            TextView time = (TextView)view.findViewById(R.id.time);
            TextView status = (TextView)view.findViewById(R.id.status);
            TextView etaView = (TextView)view.findViewById(R.id.eta);

            final StopInfo stopInfo = mInfo.get(position);
            final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();

            route.setText(arrivalInfo.getShortName());
            destination.setText(arrivalInfo.getHeadsign());
            status.setText(stopInfo.getStatusText());

            long eta = stopInfo.getEta();
            if (eta == 0) {
                etaView.setText(R.string.stop_info_eta_now);
            }
            else {
                etaView.setText(String.valueOf(eta));
            }

            int color = getResources().getColor(stopInfo.getColor());
            //status.setTextColor(color); // This just doesn't look very good.
            etaView.setTextColor(color);

            time.setText(DateUtils.formatDateTime(StopInfoActivity.this,
                    stopInfo.getDisplayTime(),
                    DateUtils.FORMAT_SHOW_TIME|
                    DateUtils.FORMAT_NO_NOON|
                    DateUtils.FORMAT_NO_MIDNIGHT));

            ContentValues values = mTripsForStop.getValues(arrivalInfo.getTripId());
            if (values != null) {
                String tripName = values.getAsString(ObaContract.Trips.NAME);

                View tripInfo = view.findViewById(R.id.trip_info);
                TextView tripNameView = (TextView)view.findViewById(R.id.trip_name);
                if (tripName.length() == 0) {
                    tripName = getString(R.string.trip_info_noname);
                }
                tripNameView.setText(tripName);
                tripInfo.setVisibility(View.VISIBLE);
            }
            else {
                // Explicitly set this to invisible because we might be reusing this view.
                View tripInfo = view.findViewById(R.id.trip_info);
                tripInfo.setVisibility(View.GONE);

            }
        }
    }

    private void goToTrip(StopInfo stop) {
        ObaArrivalInfo stopInfo = stop.getInfo();
        TripInfoActivity.start(this,
                stopInfo.getTripId(),
                mStopId,
                stopInfo.getRouteId(),
                stopInfo.getShortName(),
                mStop.getName(),
                stopInfo.getScheduledDepartureTime(),
                stopInfo.getHeadsign());
    }
    private void goToRoute(StopInfo stop) {
        RouteInfoActivity.start(this, stop.getInfo().getRouteId());
    }
    private void setRoutesFilter(ArrayList<String> routes) {
        mRoutesFilter = routes;
        ObaContract.StopRouteFilters.set(this, mStopId, mRoutesFilter);
        StopInfoListAdapter adapter = (StopInfoListAdapter)getListView().getAdapter();
        adapter.setData(mResponse.getData().getArrivalsAndDepartures());
    }

    // Similar to the annoying bit in MapViewActivity, the timer is run
    // in a separate task, so we need to post back to the main thread
    // to run our AsyncTask. We can't do everything in the timer thread
    // because the progressBar has to be modified in the UI (main) thread.
    private final Handler mRefreshHandler = new Handler();
    private final Runnable mRefresh = new Runnable() {
        public void run() {
            getStopInfo(true);
        }
    };

    //
    // Async tasks for various things.
    //
    private final AsyncTasks.Progress mLoadingProgress = new AsyncTasks.Progress() {
        public void showLoading() {
            View v = findViewById(R.id.loading);
            v.setVisibility(View.VISIBLE);
        }
        public void hideLoading() {
            View v = findViewById(R.id.loading);
            v.setVisibility(View.GONE);
        }
    };
    private final AsyncTasks.Progress mTitleProgress
        = new AsyncTasks.ProgressIndeterminateVisibility(this);

    private final class GetStopInfo extends AsyncTasks.StringToResponse {
        private final boolean mAddToDb;

        GetStopInfo(AsyncTasks.Progress progress, boolean addToDb) {
            super(progress);
            mAddToDb = addToDb;
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            return ObaApi.getArrivalsDeparturesForStop(StopInfoActivity.this, params[0]);
        }
        @Override
        protected void doResult(ObaResponse result) {
            if (result.getCode() == ObaApi.OBA_OK) {
                setResponse(result, mAddToDb);
            }
            else if (mResponse != null) {
                setRefreshError();
            }
            else {
                mEmptyText.setText(UIHelp.getStopErrorString(result.getCode()));
            }
            mAsyncTask = null;
        }
    }

    private void getStopInfo(boolean refresh) {
        if (mStopId == null) {
            return;
        }
        if (AsyncTasks.isRunning(mAsyncTask)) {
            return;
        }
        // To determine
        AsyncTasks.Progress progress = refresh ? mTitleProgress : mLoadingProgress;

        // Get it from the Net
        mAsyncTask = new GetStopInfo(progress, !refresh).execute(mStopId);
    }

    void setResponse(ObaResponse response, boolean addToDb) {
        assert(response != null);
        mResponse = response;
        mResponseTime = System.currentTimeMillis();
        ObaData data = response.getData();
        mStop = data.getStop();
        setHeader(mStop, addToDb);
        StopInfoListAdapter adapter = (StopInfoListAdapter)getListView().getAdapter();
        adapter.setData(data.getArrivalsAndDepartures());

        mResponseError.setVisibility(View.GONE);

        // Ensure there is some hint text in case there is no error
        // but the data is empty.
        mEmptyText.setText(R.string.stop_info_nodata);

        mLoadingProgress.hideLoading();
        setProgressBarIndeterminateVisibility(false);
    }
    void setRefreshError() {
        TextView errorText = (TextView)mResponseError.findViewById(R.id.response_error_text);
        CharSequence relativeTime =
            DateUtils.getRelativeTimeSpanString(mResponseTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    0);
        errorText.setText(getString(R.string.stop_info_old_data, relativeTime));

        mResponseError.setVisibility(View.VISIBLE);
        mEmptyText.setText(R.string.stop_info_nodata);

        // Just refresh the data with the current response.
        ((BaseAdapter)getListAdapter()).notifyDataSetChanged();

        mLoadingProgress.hideLoading();
        setProgressBarIndeterminateVisibility(false);
    }

    private void setHeader(Bundle bundle) {
        setHeader(bundle.getString(STOP_NAME), bundle.getString(STOP_DIRECTION));
    }

    void setHeader(ObaStop stop, boolean addToDb) {
        String code = stop.getCode();
        String name = stop.getName();
        String direction = stop.getDirection();
        double lat = stop.getLatitude();
        double lon = stop.getLongitude();

        setHeader(name, direction);

        if (addToDb) {
            // Update the database
            ContentValues values = new ContentValues();
            values.put(ObaContract.Stops.CODE, code);
            values.put(ObaContract.Stops.NAME, name);
            values.put(ObaContract.Stops.DIRECTION, direction);
            values.put(ObaContract.Stops.LATITUDE, lat);
            values.put(ObaContract.Stops.LONGITUDE, lon);
            ObaContract.Stops.insertOrUpdate(this, stop.getId(), values, true);
        }
    }
    private void setHeader(String name, String direction) {
        mStopName = name;

        if (!TextUtils.isEmpty(mStopUserName)) {
            mNameView.setText(mStopUserName);
        }
        else if (name != null) {
            mNameView.setText(name);
        }
        if (direction != null) {
            UIHelp.setStopDirection(mDirectionView, direction, false);
        }
    }
    void setFilterHeader() {
        TextView v = (TextView)findViewById(R.id.filter);
        final int num = (mRoutesFilter != null) ? mRoutesFilter.size() : 0;
        if (num > 0) {
            final int total = mStop.getRoutes().length();
            v.setText(getString(R.string.stop_info_filter_header, num, total));
            // Show the filter text (except when in name edit mode)
            mFilterGroup.setVisibility(mInNameEdit ? View.GONE : View.VISIBLE);
        }
        else {
            mFilterGroup.setVisibility(View.GONE);
        }
    }
    private final ClickableSpan mShowAllClick = new ClickableSpan() {
        public void onClick(View v) {
            if (mResponse != null) {
                setRoutesFilter(new ArrayList<String>());
            }
        }
    };

    private void initHeader() {
        mFavoriteView.setImageResource(mFavorite ?
                R.drawable.focus_star_on :
                R.drawable.focus_star_off);

        mFavoriteView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFavorite();
            }
        });

        mNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginNameEdit(null);
            }
        });
        // Implement the "Save" and "Clear" buttons
        View save = findViewById(R.id.edit_name_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setUserStopName(mEditNameView.getText().toString());
                endNameEdit();
            }
        });
        mEditNameView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    setUserStopName(mEditNameView.getText().toString());
                    endNameEdit();
                    return true;
                }
                return false;
            }
        });
        // "Cancel"
        View cancel = findViewById(R.id.edit_name_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endNameEdit();
            }
        });

        View clear = findViewById(R.id.edit_name_revert);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setUserStopName(null);
                endNameEdit();
            }
        });
    }
    private void beginNameEdit(String initial) {
        // If we can click on this, then we're definitely not
        // editable, so we should go into edit mode.
        mEditNameView.setText((initial != null) ? initial : mNameView.getText());
        mNameContainerView.setVisibility(View.GONE);
        mDirectionView.setVisibility(View.GONE);
        mFilterGroup.setVisibility(View.GONE);
        mEditNameContainerView.setVisibility(View.VISIBLE);
        mEditNameView.requestFocus();
        mInNameEdit = true;
        // TODO: Ensure the soft keyboard is up
    }
    private void endNameEdit() {
        mInNameEdit = false;
        mNameContainerView.setVisibility(View.VISIBLE);
        mEditNameContainerView.setVisibility(View.GONE);
        mDirectionView.setVisibility(View.VISIBLE);
        setFilterHeader();
        InputMethodManager imm =
            (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditNameView.getWindowToken(), 0);
    }
    private void setUserStopName(String name) {
        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        if (TextUtils.isEmpty(name)) {
            mNameView.setText(mStopName);
            values.putNull(ObaContract.Stops.USER_NAME);
            mStopUserName = null;
        }
        else {
            values.put(ObaContract.Stops.USER_NAME, name);
            mNameView.setText(name);
            mStopUserName = name;
        }
        cr.update(mStopUri, values, null, null);
    }
    private void toggleFavorite() {
        final ImageView favoriteView = (ImageView)findViewById(R.id.stop_favorite);
        int newRes;
        if (mFavorite) {
            // Turn it off
            newRes = R.drawable.focus_star_off;
        }
        else {
            // Turn it on
            newRes = R.drawable.focus_star_on;
        }
        if (ObaContract.Stops.markAsFavorite(
                StopInfoActivity.this, mStopUri, !mFavorite)) {
            mFavorite = !mFavorite;
            favoriteView.setImageResource(newRes);
        }
    }

    private static final String[] TRIPS_PROJECTION = {
        ObaContract.Trips._ID,
        ObaContract.Trips.NAME
    };
    private ContentQueryMap getTripsForStop() {
        Cursor c = managedQuery(ObaContract.Trips.CONTENT_URI,
                TRIPS_PROJECTION,
                ObaContract.Trips.STOP_ID+"=?",
                new String[] { mStopId },
                null);
        return new ContentQueryMap(c, ObaContract.Trips._ID, true, null);
    }

    private static final String[] USER_PROJECTION = {
        ObaContract.Stops.FAVORITE,
        ObaContract.Stops.USER_NAME
    };

    private void getUserInfo() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(mStopUri, USER_PROJECTION, null, null, null);
        if (c != null) {
            try {
                if (c.moveToNext()) {
                    mFavorite = (c.getInt(0) == 1);
                    mStopUserName = c.getString(1);
                }
            }
            finally {
                c.close();
            }
        }
    }
}
