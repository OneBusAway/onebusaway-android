package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.oba.request.ObaArrivalInfoResponse;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

//
// We don't use the ListFragment because the support library's version of
// the ListFragment doesn't work well with our header.
//
public class ArrivalsListFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse>,
                   ArrivalsListHeader.Controller {
    private static final String TAG = "ArrivalsListFragment";
    private static final long RefreshPeriod = 60 * 1000;

    //private static int TRIPS_FOR_STOP_LOADER = 1;
    private static int ARRIVALS_LIST_LOADER = 2;

    private ArrivalsListAdapter mAdapter;
    private ArrivalsListHeader mHeader;

    private ObaStop mStop;
    private String mStopId;
    private Uri mStopUri;
    private ArrayList<String> mRoutesFilter;

    private boolean mFavorite = false;
    private String mStopUserName;

    // Used by the test code to signal when we've retrieved stops.
    private Object mStopWait;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setEmptyText(getString(R.string.stop_info_nodata));

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        mHeader = new ArrivalsListHeader(getActivity(), this);
        View header = getView().findViewById(R.id.arrivals_list_header);
        mHeader.initView(header);
        mHeader.refresh();

        // This sets the stopId and uri
        setStopId();
        setUserInfo();

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new ArrivalsListAdapter(getActivity());
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        mRoutesFilter = ObaContract.StopRouteFilters.get(getActivity(), mStopId);
        //mTripsForStop = getTripsForStop();

        //LoaderManager.enableDebugLogging(true);

        // First load the trips for stop map. When this is finished, we load
        // the arrivals info.
        //LoaderManager mgr = getLoaderManager();
        //Loader<Cursor> loader = mgr.initLoader(TRIPS_FOR_STOP_LOADER, null, new TripsForStopCallback());
        //loader.forceLoad();

        getLoaderManager().initLoader(ARRIVALS_LIST_LOADER, getArguments(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        return inflater.inflate(R.layout.fragment_arrivals_list, null);
    }

    @Override
    public void onPause() {
        //mTripsForStop.setKeepUpdated(false);
        mRefreshHandler.removeCallbacks(mRefresh);
        super.onPause();
    }

    @Override
    public void onResume() {
        //mTripsForStop.setKeepUpdated(true);
        //mTripsForStop.requery();
        mAdapter.notifyDataSetChanged();

        // If our timer would have gone off, then refresh.
        long lastResponseTime = getArrivalsLoader().getLastResponseTime();
        long newPeriod = Math.min(RefreshPeriod, (lastResponseTime + RefreshPeriod)
                - System.currentTimeMillis());
        // Wait at least one second at least, and the full minute at most.
        //Log.d(TAG, "Refresh period:" + newPeriod);
        if (newPeriod <= 0) {
            refresh();
        } else {
            mRefreshHandler.postDelayed(mRefresh, newPeriod);
        }

        super.onResume();
    }

    @Override
    public void onDestroy() {
        //if (mTripsForStop != null) {
        //    mTripsForStop.close();
        //}
        super.onDestroy();
    }


    @Override
    public Loader<ObaArrivalInfoResponse> onCreateLoader(int id, Bundle args) {
        return new ArrivalsListLoader(getActivity(), mStopId);
    }

    //
    // This is where the bulk of the initialization takes place to create
    // this screen.
    //
    @Override
    public void onLoadFinished(Loader<ObaArrivalInfoResponse> loader,
            ObaArrivalInfoResponse result) {
        Log.d(TAG, "Load finished!");
        ((FragmentActivity)getActivity()).setProgressBarIndeterminateVisibility(Boolean.FALSE);

        ObaArrivalInfo[] info = null;

        if (result.getCode() == ObaApi.OBA_OK) {
            if (mStop == null) {
                mStop = result.getStop();
                addToDB(mStop);
            }
            info = result.getArrivalInfo();

        } else {
            // If there was a last good response, then this is a refresh
            // and we should use a toast. Otherwise, it's a initial
            // page load and we want to display the error in the empty text.
            ObaArrivalInfoResponse lastGood =
                    getArrivalsLoader().getLastGoodResponse();
            if (lastGood != null) {
                // Refresh error
                Toast.makeText(getActivity(),
                        R.string.generic_comm_error_toast,
                        Toast.LENGTH_LONG).show();
                info = lastGood.getArrivalInfo();

            } else {
                setEmptyText(getString(UIHelp.getStopErrorString(result.getCode())));
            }
        }

        mHeader.refresh();

        if (info != null) {
            // Reset the empty text just in case there is no data.
            setEmptyText(getString(R.string.stop_info_nodata));
            mAdapter.setData(result.getArrivalInfo(), mRoutesFilter);
        }

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }

        if (mStopWait != null) {
            synchronized (mStopWait) {
                mStopWait.notifyAll();
            }
        }

        // Post an update
        mRefreshHandler.postDelayed(mRefresh, RefreshPeriod);
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
        mAdapter.setData(null, mRoutesFilter);
    }

    //
    // Action Bar / Options Menu
    //
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.arrivals_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        String title = mFavorite ?
                getString(R.string.stop_info_option_removestar) :
                getString(R.string.stop_info_option_addstar);
        menu.findItem(R.id.toggle_favorite)
            .setTitle(title)
            .setTitleCondensed(title);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.show_on_map) {
            /*
            if (mResponse != null) {
                MapViewActivity.start(this, mStopId, mStop.getLatitude(), mStop.getLongitude());
            }
            */
            return true;
        } else if (id == R.id.refresh) {
            refresh();
            return true;
        } else if (id == R.id.filter) {
            if (mStop != null) {
                showRoutesFilterDialog();
            }
        } else if (id == R.id.edit_name) {
            mHeader.beginNameEdit(null);
        } else if (id == R.id.toggle_favorite) {
            setFavorite(!mFavorite);
            mHeader.refresh();
        }
        return false;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final ArrivalInfo stop = (ArrivalInfo)getListView().getItemAtPosition(position);
        if (stop == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.stop_info_item_options_title);

        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        final ObaRoute route = response.getRoute(stop.getInfo().getRouteId());
        final String url = route != null ? route.getUrl() : null;
        final boolean hasUrl = !TextUtils.isEmpty(url);
        // Check to see if the trip name is visible.
        // (we don't have any other state, so this is good enough)
        int options;
        View tripView = v.findViewById(R.id.trip_info);
        if (tripView.getVisibility() != View.GONE) {
            if (hasUrl) {
                options = R.array.stop_item_options_edit;
            } else {
                options = R.array.stop_item_options_edit_noschedule;
            }
        } else if (hasUrl) {
            options = R.array.stop_item_options;
        } else {
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
                        mHeader.refresh();
                        break;
                    case 3:
                        UIHelp.goToUrl(getActivity(), url);
                        break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    //
    // ActivityListHeader.Controller
    //
    @Override
    public String getStopName() {
        String name;
        if (mStop != null) {
            name = mStop.getName();
        } else {
            // Check the arguments
            Bundle args = getArguments();
            name = args.getString(ArrivalsListActivity.STOP_NAME);
        }
        return MyTextUtils.toTitleCase(name);
    }

    @Override
    public String getStopDirection() {
        if (mStop != null) {
            return mStop.getDirection();
        } else {
            // Check the arguments
            Bundle args = getArguments();
            return args.getString(ArrivalsListActivity.STOP_DIRECTION);
        }
    }

    @Override
    public String getUserStopName() {
        return mStopUserName;
    }

    @Override
    public void setUserStopName(String name) {
        ContentResolver cr = getActivity().getContentResolver();
        ContentValues values = new ContentValues();
        if (TextUtils.isEmpty(name)) {
            values.putNull(ObaContract.Stops.USER_NAME);
            mStopUserName = null;
        } else {
            values.put(ObaContract.Stops.USER_NAME, name);
            mStopUserName = name;
        }
        cr.update(mStopUri, values, null, null);
    }

    @Override
    public ArrayList<String> getRoutesFilter() {
        return mRoutesFilter;
    }

    @Override
    public void setRoutesFilter(ArrayList<String> routes) {
        mRoutesFilter = routes;
        ObaContract.StopRouteFilters.set(getActivity(), mStopId, mRoutesFilter);
        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        mAdapter.setData(response.getArrivalInfo(), mRoutesFilter);
    }

    @Override
    public long getLastGoodResponseTime() {
        ArrivalsListLoader loader = getArrivalsLoader();
        if (loader == null) {
            return 0;
        }
        return loader.getLastGoodResponseTime();
    }

    @Override
    public int getNumRoutes() {
        return mStop.getRouteIds().length;
    }

    @Override
    public boolean isFavorite() {
        return mFavorite;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        if (ObaContract.Stops.markAsFavorite(getActivity(), mStopUri, favorite)) {
            mFavorite = favorite;
        }
        // Apparently we can't rely on onPrepareOptionsMenu to set the
        // menus like we did before.
        // ALSO: we need to downcast this because getActivity() returns
        // an Activity, but Activity.invalidateOptionsMenu doesn't exist
        // pre-Honeycomb. So we need to make sure we are calling the
        // FragmentActivity's version.
        // Gotta love backward compatibility...
        ((FragmentActivity)getActivity()).invalidateOptionsMenu();
        return mFavorite;
    }

    private void showRoutesFilterDialog() {
        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        final List<ObaRoute> routes = response.getRoutes(mStop.getRouteIds());
        final int len = routes.size();
        final ArrayList<String> filter = mRoutesFilter;

        // mRouteIds = new ArrayList<String>(len);
        String[] items = new String[len];
        boolean[] checks = new boolean[len];

        // Go through all the stops, add them to the Ids and Names
        // For each stop, if it is in the enabled list, mark it as checked.
        for (int i = 0; i < len; ++i) {
            final ObaRoute route = routes.get(i);
            // final String id = route.getId();
            // mRouteIds.add(i, id);
            items[i] = UIHelp.getRouteDisplayName(route);
            if (filter.contains(route.getId())) {
                checks[i] = true;
            }
        }
        // Arguments
        Bundle args = new Bundle();
        args.putStringArray(RoutesFilterDialog.ITEMS, items);
        args.putBooleanArray(RoutesFilterDialog.CHECKS, checks);
        RoutesFilterDialog frag = new RoutesFilterDialog();
        frag.setArguments(args);
        frag.show(getSupportFragmentManager(), ".RoutesFilterDialog");
    }

    public static class RoutesFilterDialog extends DialogFragment
            implements DialogInterface.OnMultiChoiceClickListener,
                       DialogInterface.OnClickListener {

        static final String ITEMS = ".items";
        static final String CHECKS = ".checks";
        private boolean[] mChecks;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            String[] items = args.getStringArray(ITEMS);
            mChecks = args.getBooleanArray(CHECKS);
            if (savedInstanceState != null) {
                mChecks = args.getBooleanArray(CHECKS);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            return builder.setTitle(R.string.stop_info_filter_title)
                          .setMultiChoiceItems(items, mChecks, this)
                          .setPositiveButton(R.string.stop_info_save, this)
                          .setNegativeButton(R.string.stop_info_cancel, null)
                          .create();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putBooleanArray(CHECKS, mChecks);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            ArrivalsListActivity act = (ArrivalsListActivity)getActivity();
            // Get the fragment we want...
            ArrivalsListFragment frag = act.getArrivalsListFragment();
            frag.setRoutesFilter(mChecks);
            dialog.dismiss();
        }

        @Override
        public void onClick(DialogInterface arg0, int which, boolean isChecked) {
            mChecks[which] = isChecked;
        }
    }

    private void setRoutesFilter(boolean[] checks) {
        final int len = checks.length;
        final ArrayList<String> newFilter = new ArrayList<String>(len);

        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        final List<ObaRoute> routes = response.getRoutes(mStop.getRouteIds());
        assert(routes.size() == len);

        for (int i = 0; i < len; ++i) {
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
        mHeader.refresh();
    }

    //
    // Navigation
    //
    private void goToTrip(ArrivalInfo stop) {
        ObaArrivalInfo stopInfo = stop.getInfo();
        TripInfoActivity.start(getActivity(),
                stopInfo.getTripId(),
                mStopId,
                stopInfo.getRouteId(),
                stopInfo.getShortName(),
                mStop.getName(),
                stopInfo.getScheduledDepartureTime(),
                stopInfo.getHeadsign());
    }

    private void goToRoute(ArrivalInfo stop) {
        RouteInfoActivity.start(getActivity(),
                stop.getInfo().getRouteId());
    }

    //
    // Helpers
    //
    private ArrivalsListLoader getArrivalsLoader() {
        Loader<ObaArrivalInfoResponse> l =
                getLoaderManager().getLoader(ARRIVALS_LIST_LOADER);
        return (ArrivalsListLoader)l;
    }

    //
    // Refreshing!
    //
    private void refresh() {
        ((FragmentActivity)getActivity()).setProgressBarIndeterminateVisibility(Boolean.TRUE);
        getArrivalsLoader().onContentChanged();
    }

    private final Handler mRefreshHandler = new Handler();

    private final Runnable mRefresh = new Runnable() {
        public void run() {
            refresh();
        }
    };

    private void setStopId() {
        Uri uri = (Uri)getArguments().getParcelable(FragmentUtils.URI);
        if (uri == null) {
            Log.e(TAG, "No URI in arguments");
            return;
        }
        mStopId = uri.getLastPathSegment();
        mStopUri = uri;
    }

    private static final String[] USER_PROJECTION = {
        ObaContract.Stops.FAVORITE,
        ObaContract.Stops.USER_NAME
    };

    private void setUserInfo() {
        ContentResolver cr = getActivity().getContentResolver();
        Cursor c = cr.query(mStopUri, USER_PROJECTION, null, null, null);
        if (c != null) {
            try {
                if (c.moveToNext()) {
                    mFavorite = (c.getInt(0) == 1);
                    mStopUserName = c.getString(1);
                }
            } finally {
                c.close();
            }
        }
    }

    public void setStopWait(Object obj) {
        mStopWait = obj;
    }

    private void addToDB(ObaStop stop) {
        String name = MyTextUtils.toTitleCase(stop.getName());

        // Update the database
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops.CODE, stop.getStopCode());
        values.put(ObaContract.Stops.NAME, name);
        values.put(ObaContract.Stops.DIRECTION, stop.getDirection());
        values.put(ObaContract.Stops.LATITUDE, stop.getLatitude());
        values.put(ObaContract.Stops.LONGITUDE, stop.getLongitude());
        ObaContract.Stops.insertOrUpdate(getActivity(), stop.getId(), values, true);
    }

    /*
    private static final String[] TRIPS_PROJECTION = {
        ObaContract.Trips._ID, ObaContract.Trips.NAME
    };

    //
    // The asynchronously loads the trips for stop list.
    //
    private class TripsForStopCallback
            implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getActivity(),
                            ObaContract.Trips.CONTENT_URI,
                            TRIPS_PROJECTION,
                            ObaContract.Trips.STOP_ID + "=?",
                            new String[] { mStopId },
                            null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
            ContentQueryMap map =
                    new ContentQueryMap(c, ObaContract.Trips._ID, true, null);
            // Call back into the fragment and say we've finished this.
            mAdapter.setTripsForStop(map);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    }
    */

}
