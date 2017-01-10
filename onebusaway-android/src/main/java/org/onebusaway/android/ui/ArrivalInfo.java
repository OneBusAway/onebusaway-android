/*
 * Copyright (C) 2010-2016 Paul Watts (paulcwatts@gmail.com)
 * University of South Florida (sjbarbeau@gmail.com)
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

import android.content.Context;
import android.content.res.Resources;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaArrivalInfo.Frequency;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.UIUtils;

import java.text.DateFormat;
import java.util.Date;

public final class ArrivalInfo {

    private final ObaArrivalInfo mInfo;

    private final long mEta;

    private final long mDisplayTime;

    private final String mStatusText;

    private final String mTimeText;

    private final String mNotifyText;

    private final int mColor;

    private static final int ms_in_mins = 60 * 1000;

    private final boolean mPredicted;

    private final boolean mIsArrival;

    private final boolean mIsRouteAndHeadsignFavorite;

    /**
     * @param includeArrivalDepartureInStatusLabel true if the arrival/departure label
     *                                             should be
     *                                             included in the status label false if it
     *                                             should not
     */
    public ArrivalInfo(Context context, ObaArrivalInfo info, long now,
                       boolean includeArrivalDepartureInStatusLabel) {
        mInfo = info;
        // First, all times have to have to be converted to 'minutes'
        final long nowMins = now / ms_in_mins;
        long scheduled, predicted;
        // If this is the first stop in the sequence, show the departure time.
        if (info.getStopSequence() != 0) {
            scheduled = info.getScheduledArrivalTime();
            predicted = info.getPredictedArrivalTime();
            mIsArrival = true;
        } else {
            // Show departure time
            scheduled = info.getScheduledDepartureTime();
            predicted = info.getPredictedDepartureTime();
            mIsArrival = false;
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

        mColor = ArrivalInfoUtils.computeColor(scheduledMins, predictedMins);

        mStatusText = computeStatusLabel(context, info, now, predicted,
                scheduledMins, predictedMins, includeArrivalDepartureInStatusLabel);
        mTimeText = computeTimeLabel(context);

        // Check if the user has marked this routeId/headsign/stopId as a favorite
        mIsRouteAndHeadsignFavorite = ObaContract.RouteHeadsignFavorites
                .isFavorite(info.getRouteId(), info.getHeadsign(), info.getStopId());

        mNotifyText = computeNotifyText(context);
    }

    /**
     * @param includeArrivalDeparture true if the arrival/departure label should be included, false
     *                                if it should not
     */
    private String computeStatusLabel(Context context,
                                      ObaArrivalInfo info,
                                      final long now,
                                      final long predicted,
                                      final long scheduledMins,
                                      final long predictedMins, boolean includeArrivalDeparture) {
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
                // Bus hasn't yet arrived/departed
                return ArrivalInfoUtils.computeArrivalLabelFromDelay(res, delay);
            } else {
                /**
                 * Arrival/departure time has passed
                 */
                if (!includeArrivalDeparture) {
                    // Don't include "depart" or "arrive" in label
                    if (delay > 0) {
                        // Delayed
                        return res.getQuantityString(
                                R.plurals.stop_info_status_late_without_arrive_depart, (int) delay,
                                delay);
                    } else if (delay < 0) {
                        // Early
                        delay = -delay;
                        return res.getQuantityString(
                                R.plurals.stop_info_status_early_without_arrive_depart,
                                (int) delay, delay);
                    } else {
                        // On time
                        return context.getString(R.string.stop_info_ontime);
                    }
                }

                if (mIsArrival) {
                    // Is an arrival time
                    if (delay > 0) {
                        // Arrived late
                        return res.getQuantityString(
                                R.plurals.stop_info_arrived_delayed, (int) delay,
                                delay);
                    } else if (delay < 0) {
                        // Arrived early
                        delay = -delay;
                        return res
                                .getQuantityString(
                                        R.plurals.stop_info_arrived_early,
                                        (int) delay, delay);
                    } else {
                        // Arrived on time
                        return context.getString(R.string.stop_info_arrived_ontime);
                    }
                } else {
                    // Is a departure time
                    if (delay > 0) {
                        // Departed late
                        return res.getQuantityString(
                                R.plurals.stop_info_depart_delayed, (int) delay,
                                delay);
                    } else if (delay < 0) {
                        // Departed early
                        delay = -delay;
                        return res
                                .getQuantityString(
                                        R.plurals.stop_info_depart_early,
                                        (int) delay, delay);
                    } else {
                        // Departed on time
                        return context.getString(R.string.stop_info_departed_ontime);
                    }
                }
            }
        } else {
            // Scheduled times
            if (!includeArrivalDeparture) {
                return context.getString(R.string.stop_info_scheduled);
            }

            if (mIsArrival) {
                return context.getString(R.string.stop_info_scheduled_arrival);
            } else {
                return context.getString(R.string.stop_info_scheduled_departure);
            }
        }
    }

    private String computeTimeLabel(Context context) {
        if (context == null) {
            // The Activity has been destroyed, so just return an empty string to avoid an NPE
            return "";
        }

        String displayTime = UIUtils.formatTime(context, getDisplayTime());

        if (mEta >= 0) {
            // Bus hasn't yet arrived
            if (mIsArrival) {
                return context.getString(R.string.stop_info_time_arriving_at, displayTime);
            } else {
                return context
                        .getString(R.string.stop_info_time_departing_at, displayTime);
            }
        } else {
            // Arrival/departure time has passed
            if (mIsArrival) {
                return context.getString(R.string.stop_info_time_arrived_at, displayTime);
            } else {
                return context.getString(R.string.stop_info_time_departed_at, displayTime);
            }
        }
    }

    private String computeNotifyText(Context context) {
        if (context == null) {
            // The Activity has been destroyed, so just return an empty string to avoid an NPE
            return "";
        }

        final String routeDisplayName = UIUtils.getRouteDisplayName(mInfo);

        if (mEta > 0) {
            // Bus hasn't yet arrived/departed
            if (mIsArrival) {
                return context.getString(R.string.trip_stat_arriving, routeDisplayName,
                        (int) (mEta));
            } else {
                return context.getString(R.string.trip_stat_departing, routeDisplayName,
                        (int) (mEta));
            }
        } else if (mEta < 0) {
            // Bus arrived or departed
            if (mIsArrival) {
                return context.getString(R.string.trip_stat_gone_arrived, routeDisplayName);
            } else {
                return context.getString(R.string.trip_stat_gone_departed, routeDisplayName);
            }
        } else {
            // Bus is arriving/departing now
            if (mIsArrival) {
                return context.getString(R.string.trip_stat_lessthanone_arriving, routeDisplayName);
            } else {
                return context.getString(R.string.trip_stat_lessthanone_departing, routeDisplayName);
            }
        }
    }

    public final ObaArrivalInfo getInfo() {
        return mInfo;
    }

    public final long getEta() {
        return mEta;
    }

    public final long getDisplayTime() {
        return mDisplayTime;
    }

    public final String getStatusText() {
        return mStatusText;
    }

    public final String getTimeText() {
        return mTimeText;
    }

    public final String getNotifyText() {
        return mNotifyText;
    }

    /**
     * Returns true if this arrival info is for an arrival time, false if it is for a departure
     * time
     */
    public final boolean isArrival() {
        return mIsArrival;
    }

    /**
     * Returns the resource code for the color that should be used for the arrival time
     *
     * @return the resource code for the color that should be used for the arrival time
     */
    public final int getColor() {
        return mColor;
    }

    /**
     * Returns true if there is real-time arrival info available for this trip, false if there is
     * not
     *
     * @return true if there is real-time arrival info available for this trip, false if there is
     * not
     */
    public final boolean getPredicted() {
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
