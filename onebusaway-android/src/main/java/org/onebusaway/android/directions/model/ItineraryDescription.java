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

import org.onebusaway.android.directions.util.ConversionUtils;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.routing.core.TraverseMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Itinerary desciption is a list of trips and a rank. This is for the Realtime service.
 */
public class ItineraryDescription {

    private static final String TAG = "ItineraryDescription";

    private List<String> mTripIds;

    private Date mEndDate;

    public ItineraryDescription(Itinerary itinerary) {
        mTripIds = new ArrayList<>();
        for (Leg leg : itinerary.legs) {
            TraverseMode traverseMode = TraverseMode.valueOf(leg.mode);
            if (traverseMode.isTransit()) {
                mTripIds.add(leg.tripId);
            }
        }

        Leg last = itinerary.legs.get(itinerary.legs.size() - 1);
        mEndDate = ConversionUtils.parseOtpDate(last.endTime);
    }

    public ItineraryDescription(List<String> tripIds, Date endDate) {
        mTripIds = tripIds;
        mEndDate = endDate;
    }

    /**
     * Check if this itinerary matches the itinerary of another ItineraryDescription
     *
     * @param other object to compare to
     * @return true if matches, false otherwise
     */
    public boolean itineraryMatches(ItineraryDescription other) {
        if (other.mTripIds.size() != this.mTripIds.size()) {
            return false;
        }
        for (int i = 0; i < this.mTripIds.size(); i++) {
            if (!mTripIds.get(i).equals(other.mTripIds.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check the delay on this itinerary relative to a newer one.
     * Positive indicates a delay, negative indicates running early.
     *
     * @param other Newer itinerary to use to calculate delay.
     * @return delay in seconds
     */
    public long getDelay(ItineraryDescription other) {
        return (other.getEndDate().getTime() - this.getEndDate().getTime())/1000;
    }

    /**
     * Return an ID for this ItineraryDescription.
     * The notification requires an ID so it does not create duplicates. Right now, sending a
     * notification cancels out the RealtimeService, so we do not send multiple notifications,
     * but we may in future.
     * Use the hash code of the trips array.
     *
     * @return ID for this itinerary description. Not guaranteed to be unique.
     */
    public int getId() {
        if (mTripIds == null || mTripIds.isEmpty()) {
            return -1;
        }
        return mTripIds.hashCode();
    }

    public Date getEndDate() {
        return mEndDate;
    }

    /**
     *
     * @return true if the itinerary's end date has passed
     */
    public boolean isExpired() {
        return getEndDate().before(new Date());
    }

    /**
     * return list of trip IDs
     */
    public List<String> getTripIds() {
        return mTripIds;
    }
}
