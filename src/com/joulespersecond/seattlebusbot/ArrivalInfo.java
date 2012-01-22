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
package com.joulespersecond.seattlebusbot;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import android.content.Context;
import android.content.res.Resources;

import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.elements.ObaArrivalInfo.Frequency;

final class ArrivalInfo {
    final static class InfoComparator implements Comparator<ArrivalInfo> {
        public int compare(ArrivalInfo lhs, ArrivalInfo rhs) {
            return (int)(lhs.mEta - rhs.mEta);
        }
    }

    public static final ArrayList<ArrivalInfo> convertObaArrivalInfo(Context context,
            ObaArrivalInfo[] arrivalInfo,
            ArrayList<String> filter) {
        final int len = arrivalInfo.length;
        ArrayList<ArrivalInfo> result = new ArrayList<ArrivalInfo>(len);
        final long ms = System.currentTimeMillis();
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

    private final ObaArrivalInfo mInfo;
    private final long mEta;
    private final long mDisplayTime;
    private final String mStatusText;
    private final int mColor;

    private static final int ms_in_mins = 60 * 1000;

    public ArrivalInfo(Context context, ObaArrivalInfo info, long now) {
        mInfo = info;
        // First, all times have to have to be converted to 'minutes'
        final long nowMins = now / ms_in_mins;
        final long scheduled = info.getScheduledArrivalTime();
        final long predicted = info.getPredictedArrivalTime();
        final long scheduledMins = scheduled / ms_in_mins;
        final long predictedMins = predicted / ms_in_mins;

        if (predicted != 0) {
            mEta = predictedMins - nowMins;
            mDisplayTime = predicted;
        } else {
            mEta = scheduledMins - nowMins;
            mDisplayTime = scheduled;
        }

        mColor = computeColor(scheduled, predicted);

        mStatusText = computeStatusLabel(context, info, now, predicted,
                scheduledMins, predictedMins);

    }

    private int computeColor(final long scheduled, final long predicted) {

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
            return R.color.stop_info_scheduled;
        }
    }

    private String computeStatusLabel(Context context,
            ObaArrivalInfo info,
            final long now,
            final long predicted,
            final long scheduledMins,
            final long predictedMins) {

        final Resources res = context.getResources();

        Frequency frequency = info.getFrequency();

        if (frequency != null) {

            int headwayAsMinutes = (int)(frequency.getHeadway() / 60);
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
                            R.plurals.stop_info_arrive_delayed, (int)delay,
                            delay);
                } else if (delay < 0) {
                    // Arriving early
                    delay = -delay;
                    return res
                            .getQuantityString(
                                    R.plurals.stop_info_arrive_early,
                                    (int)delay, delay);
                } else {
                    // Arriving on time
                    return context.getString(R.string.stop_info_ontime);
                }
            } else {
                // Bus is departing
                if (delay > 0) {
                    // Departing delayed
                    return res.getQuantityString(
                            R.plurals.stop_info_depart_delayed, (int)delay,
                            delay);
                } else if (delay < 0) {
                    // Departing early
                    delay = -delay;
                    return res
                            .getQuantityString(
                                    R.plurals.stop_info_depart_early,
                                    (int)delay, delay);
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

    final ObaArrivalInfo getInfo() {
        return mInfo;
    }

    final long getEta() {
        return mEta;
    }

    final long getDisplayTime() {
        return mDisplayTime;
    }

    final String getStatusText() {
        return mStatusText;
    }

    final int getColor() {
        return mColor;
    }
}
