package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.request.ObaArrivalInfoRequest;
import com.joulespersecond.oba.request.ObaArrivalInfoResponse;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

//
// We don't use the ListFragment because the support library's version of
// the ListFragment doesn't work well with our header.
//
public class ArrivalsListFragment extends MyListFragment
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse> {
    private static final String TAG = "ArrivalsListFragment";
    //private static int TRIPS_FOR_STOP_LOADER = 1;
    private static int ARRIVALS_LIST_LOADER = 2;

    private ArrivalsListAdapter mAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Give some text to display if there is no data.  In a real
        // application this would come from a resource.
        setEmptyText(getString(R.string.stop_info_nodata));

        // We have a menu item to show in action bar.
        //setHasOptionsMenu(true);

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new ArrivalsListAdapter(getActivity());
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        LoaderManager.enableDebugLogging(true);

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
    public Loader<ObaArrivalInfoResponse> onCreateLoader(int id, Bundle args) {
        // Get the URI from the arguments
        Uri uri = (Uri)args.getParcelable("uri");
        if (uri == null) {
            Log.e(TAG, "No URI in arguments");
            return null;
        }
        return new ArrivalsListLoader(getActivity(), uri.getLastPathSegment());
    }

    @Override
    public void onLoadFinished(Loader<ObaArrivalInfoResponse> loader,
            ObaArrivalInfoResponse data) {
        Log.d(TAG, "Load finished!");
        // Set the new data in the adapter.
        // TODO: Specify the route filter

        ArrayList<ArrivalInfo> list =
               ArrivalInfo.convertObaArrivalInfo(getActivity(),
                       data.getArrivalInfo(), null);
        mAdapter.setData(list);

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
        mAdapter.setData(null);
    }

    //
    // Arrivals list loader
    //
    public static class ArrivalsListLoader extends AsyncTaskLoader<ObaArrivalInfoResponse> {
        private final String mStopId;

        public ArrivalsListLoader(Context context, String stopId) {
            super(context);
            mStopId = stopId;
        }

        @Override
        public ObaArrivalInfoResponse loadInBackground() {
            return ObaArrivalInfoRequest.newRequest(getContext(), mStopId).call();
        }

        @Override
        protected void onStartLoading() {
            // TODO: Perhaps we can cache the result? Or not??
            forceLoad();
        }

        /**
         * Handles a request to stop the Loader.
         */
        @Override
        protected void onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad();
        }

        /**
         * Handles a request to completely reset the Loader.
         */
        @Override
        protected void onReset() {
            super.onReset();
            // Ensure the loader is stopped
            onStopLoading();
        }
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
