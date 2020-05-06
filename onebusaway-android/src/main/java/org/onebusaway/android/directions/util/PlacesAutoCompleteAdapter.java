/*
 * Copyright (C) 2015-2017 University of South Florida (sjbarbeau@gmail.com),
 * Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.directions.util;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.LocationUtils;

import java.util.ArrayList;
import java.util.List;

public class PlacesAutoCompleteAdapter extends org.onebusaway.android.util.ArrayAdapter<CustomAddress> implements Filterable {

    private Context mContext;
    private ObaRegion mRegion;

    private List<CustomAddress> mResultList = new ArrayList<CustomAddress>();

    public PlacesAutoCompleteAdapter(Context context, int viewId,
                                     ObaRegion region) {
        super(context, viewId);
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
                    // Retrieve the autocomplete results
                    if (BuildConfig.USE_PELIAS_GEOCODING) {
                        mResultList = LocationUtils.processPeliasGeocoding(mContext, mRegion, constraint.toString());
                    } else {
                        // Use Google Places SDK
                        mResultList = LocationUtils.processGooglePlacesGeocoding(mContext, mRegion, constraint.toString());
                    }
                    if (mResultList != null){
                        Log.d("Geocode", "Num of results: " + mResultList.size());
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

    @Override
    protected void initView(View view, CustomAddress address) {
        TextView text = view.findViewById(R.id.geocode_text);
        ImageView icon = view.findViewById(R.id.geocode_transit_icon);

        text.setText(address.toString());

        if (address.isTransitCategory()) {
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }
    }
}