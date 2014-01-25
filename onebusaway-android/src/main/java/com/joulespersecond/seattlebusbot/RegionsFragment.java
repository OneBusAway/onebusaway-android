/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.region.ObaRegionsLoader;
import com.joulespersecond.oba.region.RegionUtils;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

public class RegionsFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ArrayList<ObaRegion>> {

    private static final String TAG = "RegionsFragment";

    private static final String RELOAD = ".reload";

    private ArrayAdapter<ObaRegion> mAdapter;

    private Location mLocation;

    // Current region
    private ObaRegion mCurrentRegion;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setHasOptionsMenu(true);

        mLocation = UIHelp.getLocation2(getActivity());
        mCurrentRegion = Application.get().getCurrentRegion();

        Bundle args = new Bundle();
        args.putBoolean(RELOAD, false);
        getLoaderManager().initLoader(0, args, this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Get the region and set this as the default region.
        ObaRegion region = mAdapter.getItem(position);
        Application.get().setCurrentRegion(region);

        //If we're currently auto-selecting regions, disable this so it doesn't override the manual setting
        if (Application.getPrefs()
                .getBoolean(getString(R.string.preference_key_auto_select_region), true)) {
            PreferenceHelp
                    .saveBoolean(getString(R.string.preference_key_auto_select_region), false);
            Toast.makeText(this.getActivity(),
                    R.string.region_disabled_auto_selection, Toast.LENGTH_LONG)
                    .show();
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "User manually set region to '" + region.getName() + "'.");
        }

        NavHelp.goHome(getActivity());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.regions_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.refresh) {
            refresh();
            return true;
        }
        return false;
    }

    private void refresh() {
        setListShown(false);
        setListAdapter(null);
        mAdapter = null;
        Bundle args = new Bundle();
        args.putBoolean(RELOAD, true);
        getLoaderManager().restartLoader(0, args, this);
    }

    @Override
    public Loader<ArrayList<ObaRegion>> onCreateLoader(int id, Bundle args) {
        boolean refresh = args.getBoolean(RELOAD);
        return new ObaRegionsLoader(getActivity(), refresh);
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<ObaRegion>> loader,
            ArrayList<ObaRegion> results) {
        // Create our generic adapter
        mAdapter = new Adapter(getActivity());
        setListAdapter(mAdapter);

        //Remove any regions that aren't usable before setting the adapter.
        //Loop using an Iterator, since per Oracle Iterator.remove() is the only safe way 
        //to remove an item from a Collection (including ArrayList) during iteration:
        //http://docs.oracle.com/javase/tutorial/collections/interfaces/collection.html
        try {
            Iterator<ObaRegion> iter = results.iterator();
            while (iter.hasNext()) {
                ObaRegion r = iter.next();
                if (!RegionUtils.isRegionUsable(r)) {
                    iter.remove();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Removed region '" + r.getName() + "' from adapter.");
                    }
                }
            }
        } catch (UnsupportedOperationException e) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Problem removing region from list using iterator: " + e);
            }
            //The platform apparently didn't like the "efficient" way to do this, so we'll just 
            //loop through a copy and remove what we don't want from the original
            ArrayList<ObaRegion> copy = new ArrayList<ObaRegion>(results);
            for (ObaRegion r : copy) {
                if (!RegionUtils.isRegionUsable(r)) {
                    results.remove(r);
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Removed region '" + r.getName() + "' from adapter.");
                    }
                }
            }
        }
        mAdapter.setData(results);
        if (mLocation != null) {
            mAdapter.sort(mClosest);
        }
    }

    @Override
    public void onLoaderReset(Loader<ArrayList<ObaRegion>> arg0) {
        setListAdapter(null);
        mAdapter = null;
    }

    private Comparator<ObaRegion> mClosest = new Comparator<ObaRegion>() {
        @Override
        public int compare(ObaRegion r1, ObaRegion r2) {
            Float r1distance = RegionUtils.getDistanceAway(r1, mLocation);
            Float r2distance = RegionUtils.getDistanceAway(r2, mLocation);
            if (r1distance == null) {
                r1distance = Float.MAX_VALUE;
            }
            if (r2distance == null) {
                r2distance = Float.MAX_VALUE;
            }
            return r1distance.compareTo(r2distance);
        }
    };

    private class Adapter extends ArrayAdapter<ObaRegion> {

        Adapter(Context context) {
            super(context, R.layout.simple_list_item_2_checked);
        }

        @Override
        protected void initView(View view, ObaRegion region) {
            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            TextView text2 = (TextView) view.findViewById(android.R.id.text2);
            ImageView image = (ImageView) view.findViewById(android.R.id.selectedIcon);
            text1.setText(region.getName());
            Float distance = null;
            Resources r = getResources();

            int regionVis = View.INVISIBLE;
            if (mCurrentRegion != null && region.getId() == mCurrentRegion.getId()) {
                regionVis = View.VISIBLE;
            }

            image.setVisibility(regionVis);

            if (mLocation != null) {
                distance = RegionUtils.getDistanceAway(region, mLocation);
            }
            if (distance != null) {
                NumberFormat fmt = NumberFormat.getInstance();
                if (fmt instanceof DecimalFormat) {
                    ((DecimalFormat) fmt).setMaximumFractionDigits(1);
                }
                double miles = distance * 0.000621371;
                text2.setText(r.getQuantityString(R.plurals.region_distance,
                        (int) miles,
                        fmt.format(miles)));
            } else {
                view.setEnabled(false);
                text2.setText(R.string.region_unavailable);
            }
        }
    }

}
