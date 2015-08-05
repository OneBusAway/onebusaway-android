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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaArrivalInfo.Frequency;
import org.onebusaway.android.provider.ObaContract;

import android.content.Context;
import android.content.res.Resources;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

public final class ArrivalInfo {

    final static class InfoComparator implements Comparator<ArrivalInfo> {

        public int compare(ArrivalInfo lhs, ArrivalInfo rhs) {
            return (int) (lhs.mEta - rhs.mEta);
        }
    }

    /**
     * Converts the ObaArrivalInfo array received from the server to an ArrayList for the adapter
     * @param context
     * @param arrivalInfo
     * @param filter routeIds to filter for
     * @param ms current time in milliseconds
     * @return ArrayList of arrival info to be used with the adapter
     */
    public static final ArrayList<ArrivalInfo> convertObaArrivalInfo(Context context,
            ObaArrivalInfo[] arrivalInfo,
            ArrayList<String> filter, long ms) {
        final int len = arrivalInfo.length;
        ArrayList<ArrivalInfo> result = new ArrayList<ArrivalInfo>(len);
        if (filter != null && filter.size() > 0) {
            for (int i = 0; i < len; ++i) {
                ObaArrivalInfo arrival = arrivalInfo[i];
                if (filter.contains(arrival.getRouteId())) {
                    result.add(new ArrivalInfo(context, arrival, ms));
                }
            }
        } else {
            for (int i = 0; i < len; ++i) {
                result.add(new ArrivalInfo(context, arrivalInfo[i], ms));
            }
        }

        // Sort by ETA
        Collections.sort(result, new InfoComparator());
        return result;
    }


    /**
     * Returns the index in the provided infoList for the first non-negative arrival ETA in the
     * list, or -1 if no non-negative ETAs exist in the list
     *
     * @param infoList list to search for non-negative arrival times, ordered by relative ETA from
     *                 negative infinity to positive infinity
     * @return the index in the provided infoList for the first non-negative arrival ETA in the
     * list, or -1 if no non-negative ETAs exist in the list
     */
    public static int findFirstNonNegativeArrival(ArrayList<ArrivalInfo> infoList) {
        for (int i = 0; i < infoList.size(); i++) {
            ArrivalInfo info = infoList.get(i);
            if (info.getEta() >= 0) {
                return i;
            }
        }
        // We didn't find any non-negative ETAs
        return -1;
    }

    /**
     * Returns the indexes in the provided infoList for the preferred route/headsign combinations
     * to be prioritized for displayed in the header, or null if no non-negative ETAs exist in the
     * list.  If no route/headsign combinations are favorited, the indexes returned may simply be
     * the indexes of the first (and second, if it exists) non-negative arrival times.
     *
     * @param infoList list to search for non-negative arrival times, ordered by relative ETA from
     *                 negative infinity to positive infinity
     * @return the indexes in the provided infoList for the preferred route/headsign combinations
     * to be prioritized for displayed in the header, or null if no non-negative ETAs exist in the
     * list
     */
    public static ArrayList<Integer> findPreferredArrivalIndexes(ArrayList<ArrivalInfo> infoList) {
        // Start by getting the index of the first non-negative arrival time
        int firstIndex = findFirstNonNegativeArrival(infoList);
        if (firstIndex == -1) {
            return null;
        }
        // Find any favorites
        ArrayList<Integer> preferredIndexes = new ArrayList<>();
        for (int i = firstIndex; i < infoList.size(); i++) {
            ArrivalInfo info = infoList.get(i);
            if (info.isRouteAndHeadsignFavorite()) {
                preferredIndexes.add(i);
            }
        }

        // If we have at least two favorites, that's enough to fill the header - return them
        if (preferredIndexes.size() >= 2) {
            return preferredIndexes;
        }

        // If we have one favorite, and the index is different from the firstIndex, then add the firstIndex and return
        if (preferredIndexes.size() == 1 && preferredIndexes.get(0) != firstIndex) {
            preferredIndexes.add(firstIndex);
        }

        // If we have no preferred indexes (i.e., starred route/headsigns) at this point, then add the firstIndex
        if (preferredIndexes.size() == 0) {
            preferredIndexes.add(firstIndex);

            // If there is another non-negative arrival time, then add it too
            int secondIndex = firstIndex + 1;
            if (secondIndex < infoList.size()) {
                preferredIndexes.add(secondIndex);
            }
        }

        return preferredIndexes;
    }


    private final ObaArrivalInfo mInfo;

    private final long mEta;

    private final long mDisplayTime;

    private final String mStatusText;

    private final Integer mColor;

    private static final int ms_in_mins = 60 * 1000;

    private final boolean mPredicted;

    private final boolean mIsRouteAndHeadsignFavorite;

    public ArrivalInfo(Context context, ObaArrivalInfo info, long now) {
        mInfo = info;
        // First, all times have to have to be converted to 'minutes'
        final long nowMins = now / ms_in_mins;
        long scheduled, predicted;
        // If this is the first stop in the sequence, show the departure time.
        if (info.getStopSequence() != 0) {
            scheduled = info.getScheduledArrivalTime();
            predicted = info.getPredictedArrivalTime();
        } else {
            scheduled = info.getScheduledDepartureTime();
            predicted = info.getPredictedDepartureTime();
        }

        final long scheduledMins = scheduled / ms_in_mins;
        final long predictedMins = predicted / ms_in_mins;

        if (predicted != 0) {
            mPredicted = true;
            mEta = predictedMins - nowMins;
            mDisplayTime = predicted;
        } else {
            mPredicted = false;
            mEta = scheduledMins - nowMins;
            mDisplayTime = scheduled;
        }

        mColor = computeColor(scheduled, predicted);

        mStatusText = computeStatusLabel(context, info, now, predicted,
                scheduledMins, predictedMins);

        // Check if the user has marked this routeId/headsign/stopId as a favorite
        mIsRouteAndHeadsignFavorite = ObaContract.RouteHeadsignFavorites
                .isFavorite(context, info.getRouteId(),
                        info.getHeadsign(), info.getStopId());
    }

    private Integer computeColor(final long scheduled, final long predicted) {
        if (predicted != 0) {
            long delay = predicted - scheduled;

            // Bus is arriving
            if (delay > 0) {
                // Arriving delayed
                return R.color.stop_info_delayed;
            } else if (delay < 0) {
                // Arriving early
                return R.color.stop_info_early;
            } else {
                // Arriving on time
                return R.color.stop_info_ontime;
            }
        } else {
            // The default color for the UI element should be used, so return null
            return null;
        }
    }

    private String computeStatusLabel(Context context,
            ObaArrivalInfo info,
            final long now,
            final long predicted,
            final long scheduledMins,
            final long predictedMins) {
        if (context == null) {
            // The Activity has been destroyed, so just return an empty string to avoid an NPE
            return "";
        }

        final Resources res = context.getResources();

        Frequency frequency = info.getFrequency();

        if (frequency != null) {

            int headwayAsMinutes = (int) (frequency.getHeadway() / 60);
            DateFormat formatter = DateFormat.getTimeInstance(DateFormat.SHORT);

            int statusLabelId = -1;
            long time = 0;

            if (now < frequency.getStartTime()) {
                statusLabelId = R.string.stop_info_frequency_from;
                time = frequency.getStartTime();
            } else {
                statusLabelId = R.string.stop_info_frequency_until;
                time = frequency.getEndTime();
            }

            String label = formatter.format(new Date(time));
            return context.getString(statusLabelId, headwayAsMinutes, label);
        }

        if (predicted != 0) {
            long delay = predictedMins - scheduledMins;

            if (mEta >= 0) {
                // Bus is arriving
                if (delay > 0) {
                    // Arriving delayed
                    return res.getQuantityString(
                            R.plurals.stop_info_arrive_delayed, (int) delay,
                            delay);
                } else if (delay < 0) {
                    // Arriving early
                    delay = -delay;
                    return res
                            .getQuantityString(
                                    R.plurals.stop_info_arrive_early,
                                    (int) delay, delay);
                } else {
                    // Arriving on time
                    return context.getString(R.string.stop_info_ontime);
                }
            } else {
                // Bus is departing
                if (delay > 0) {
                    // Departing delayed
                    return res.getQuantityString(
                            R.plurals.stop_info_depart_delayed, (int) delay,
                            delay);
                } else if (delay < 0) {
                    // Departing early
                    delay = -delay;
                    return res
                            .getQuantityString(
                                    R.plurals.stop_info_depart_early,
                                    (int) delay, delay);
                } else {
                    // Departing on time
                    return context.getString(R.string.stop_info_ontime);
                }
            }
        } else {
            if (mEta > 0) {
                return context.getString(R.string.stop_info_scheduled_arrival);
            } else {
                return context
                        .getString(R.string.stop_info_scheduled_departure);
            }
        }
    }

    public final ObaArrivalInfo getInfo() {
        return mInfo;
    }

    public final long getEta() {
        return mEta;
    }

    final long getDisplayTime() {
        return mDisplayTime;
    }

    final String getStatusText() {
        return mStatusText;
    }

    /**
     * Returns the resource code for the color that should be used for the arrival time, or null if
     * the default color for UI element (e.g., Heading1 TextBox) should be used
     *
     * @return the resource code for the color that should be used for the arrival time, or null if
     * the default color for UI element (e.g., Heading1 TextBox) should be used
     */
    final Integer getColor() {
        return mColor;
    }

    /**
     * Returns true if there is real-time arrival info available for this trip, false if there is not
     * @return true if there is real-time arrival info available for this trip, false if there is not
     */
    final boolean getPredicted() {
        return mPredicted;
    }

    /**
     * Returns true if this route is a user-designated favorite, false if it is not
     *
     * @return true if this route is a user-designated favorite, false if it is not
     */
    public final boolean isRouteAndHeadsignFavorite() {
        return mIsRouteAndHeadsignFavorite;
    }
}
