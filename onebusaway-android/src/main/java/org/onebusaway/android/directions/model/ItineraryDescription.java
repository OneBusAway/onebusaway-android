/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.directions.model;

import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.routing.core.TraverseMode;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Itinerary desciption is a list of trips and a rank. This is for the Realtime service.
 */
public class ItineraryDescription {

    private static final String TAG = "ItineraryDescription";

    private int mRank;

    private List<String> mTripIds;

    public ItineraryDescription(Itinerary itinerary, int rank) {

        this.mRank = rank;

        mTripIds = new ArrayList<>();

        for (Leg leg : itinerary.legs) {
            TraverseMode traverseMode = TraverseMode.valueOf(leg.mode);

            if (traverseMode.isTransit()) {
                mTripIds.add(leg.tripId);
            }

        }
    }

    public int getRank() {
        return mRank;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof ItineraryDescription)) {
            return false;
        }
        ItineraryDescription d = (ItineraryDescription) o;
        if (d.mRank != this.mRank) {
            return false;
        }
        if (d.mTripIds.size() != this.mTripIds.size()) {
            return false;
        }
        for (int i = 0; i < this.mTripIds.size(); i++) {
            if (!mTripIds.get(i).equals(d.mTripIds.get(i))) {
                return false;
            }
        }

        return true;
    }

    // For the notification, we need an ID so we don't get duplicates.
    // Right now we never send two notifications, but we may in future.
    // First trip ID suffices.
    public int getId() {
        if (mTripIds == null || mTripIds.isEmpty()) {
            return -1;
        }
        try {
            String tripId = mTripIds.get(0).split(":")[1];
            return Integer.parseInt(tripId);
        } catch (Exception ex) {
            Log.e(TAG, "Error calculating trip ID: " + ex.getMessage());
            return 0;
        }
    }

}
