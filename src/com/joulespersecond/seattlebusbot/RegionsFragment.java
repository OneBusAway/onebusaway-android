package com.joulespersecond.seattlebusbot;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.joulespersecond.oba.region.ObaRegion;
import com.joulespersecond.oba.region.ObaRegionsLoader;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;

public class RegionsFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ArrayList<ObaRegion>> {
    private static final String RELOAD = ".reload";

    private ArrayAdapter<ObaRegion> mAdapter;
    private Location mLocation;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setHasOptionsMenu(true);

        mLocation = UIHelp.getLocation2(getActivity());

        Bundle args = new Bundle();
        args.putBoolean(RELOAD, false);
        getLoaderManager().initLoader(0, args, this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {

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
            Float r1distance = r1.getDistanceAway(mLocation);
            Float r2distance = r2.getDistanceAway(mLocation);
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
            super(context, android.R.layout.simple_list_item_2);
        }

        @Override
        protected void initView(View view, ObaRegion region) {
            TextView text1 = (TextView)view.findViewById(android.R.id.text1);
            TextView text2 = (TextView)view.findViewById(android.R.id.text2);
            text1.setText(region.getName());
            Float distance = null;
            Resources r = getResources();

            if (mLocation != null) {
                distance = region.getDistanceAway(mLocation);
            }
            if (distance != null) {
                NumberFormat fmt = NumberFormat.getInstance();
                if (fmt instanceof DecimalFormat) {
                    ((DecimalFormat)fmt).setMaximumFractionDigits(1);
                }
                double miles = distance * 0.000621371;
                text2.setText(r.getQuantityString(R.plurals.region_distance,
                                        (int)miles,
                                        fmt.format(miles)));
            } else {
                view.setEnabled(false);
                text2.setText(R.string.region_unavailable);
            }
        }
    }

}
