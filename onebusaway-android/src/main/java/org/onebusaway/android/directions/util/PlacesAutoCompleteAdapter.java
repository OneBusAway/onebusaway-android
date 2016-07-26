package org.onebusaway.android.directions.util;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.LocationUtils;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.ArrayList;
import java.util.List;

public class PlacesAutoCompleteAdapter extends ArrayAdapter<CustomAddress> implements Filterable {

    private Context mContext;
    private ObaRegion mRegion;

    private List<CustomAddress> mResultList = new ArrayList<CustomAddress>();

    public PlacesAutoCompleteAdapter(Context context, int textViewResourceId,
                                     ObaRegion region) {
        super(context, textViewResourceId);
        this.mContext = context;
        this.mRegion = region;
    }

    @Override
    public int getCount() {
        if (mResultList != null){
            return mResultList.size();
        } else {
            return 0;
        }
    }

    @Override
    public CustomAddress getItem(int index) {
        return mResultList.get(index);
    }

    @Override
    public Filter getFilter() {
        Filter filter = new Filter() {
            @Override
            protected Filter.FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                if (constraint != null) {
                    // Retrieve the autocomplete results.
                        mResultList = LocationUtils.processGeocoding(mContext, mRegion,
                                constraint.toString());
                    if (mResultList != null){
                        // Assign the data to the FilterResults
                        filterResults.values = mResultList;
                        filterResults.count = mResultList.size();
                    }
                }
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }};
        return filter;
    }

    public ObaRegion getRegion() {
        return mRegion;
    }

    public void setRegion(ObaRegion region) {
        this.mRegion = region;
    }

}