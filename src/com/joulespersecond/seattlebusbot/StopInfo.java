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

import com.joulespersecond.oba.elements.ObaArrivalInfo;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

final class StopInfo {
    final static class StopInfoComparator implements Comparator<StopInfo> {
        public int compare(StopInfo lhs, StopInfo rhs) {
            return (int)(lhs.mEta - rhs.mEta);
        }
    }

    public static final ArrayList<StopInfo>
    convertObaArrivalInfo(Context context,
              ObaArrivalInfo[] arrivalInfo,
              ArrayList<String> filter) {
        final int len = arrivalInfo.length;
        ArrayList<StopInfo> result = new ArrayList<StopInfo>(len);
        final long ms = System.currentTimeMillis();
        if (filter != null && filter.size() > 0) {
            for (int i=0; i < len; ++i) {
                ObaArrivalInfo arrival = arrivalInfo[i];
                if (filter.contains(arrival.getRouteId())) {
                    result.add(new StopInfo(context, arrival, ms));
                }
            }
        }
        else {
            for (int i=0; i < len; ++i) {
                result.add(new StopInfo(context, arrivalInfo[i], ms));
            }
        }

        // Sort by ETA
        Collections.sort(result, new StopInfoComparator());
        return result;
    }

    private final ObaArrivalInfo mInfo;
    private final long mEta;
    private final long mDisplayTime;
    private final String mStatusText;
    private final int mColor;

    private static final int ms_in_mins = 60*1000;

    public StopInfo(Context context, ObaArrivalInfo info, long now) {
        mInfo = info;
        // First, all times have to have to be converted to 'minutes'
        final long nowMins = now/ms_in_mins;
        final long scheduled = info.getScheduledArrivalTime();
        final long predicted = info.getPredictedArrivalTime();
        final long scheduledMins = scheduled/ms_in_mins;
        final long predictedMins = predicted/ms_in_mins;
        final Resources res = context.getResources();

        if (predicted != 0) {
            mEta = predictedMins - nowMins;
            mDisplayTime = predicted;
            long delay = predictedMins - scheduledMins;

            if (mEta >= 0) {
                // Bus is arriving
                if (delay > 0) {
                    // Arriving delayed
                    mColor = R.color.stop_info_delayed;
                    mStatusText = res.getQuantityString(R.plurals.stop_info_arrive_delayed,
                                                (int)delay, delay);
                }
                else if (delay < 0) {
                    // Arriving early
                    mColor = R.color.stop_info_early;
                    delay = -delay;
                    mStatusText = res.getQuantityString(R.plurals.stop_info_arrive_early,
                                                (int)delay, delay);
                }
                else {
                    // Arriving on time
                    mColor = R.color.stop_info_ontime;
                    mStatusText = context.getString(R.string.stop_info_ontime);
                }
            }
            else {
                // Bus is departing
                if (delay > 0) {
                    // Departing delayed
                    mColor = R.color.stop_info_delayed;
                    mStatusText = res.getQuantityString(R.plurals.stop_info_depart_delayed,
                                                (int)delay, delay);
                }
                else if (delay < 0) {
                    // Departing early
                    mColor = R.color.stop_info_early;
                    delay = -delay;
                    mStatusText = res.getQuantityString(R.plurals.stop_info_depart_early,
                                                (int)delay, delay);
                }
                else {
                    // Departing on time
                    mColor = R.color.stop_info_ontime;
                    mStatusText = context.getString(R.string.stop_info_ontime);
                }
            }
        }
        else {
            mColor = R.color.stop_info_scheduled;

            mEta = scheduledMins - nowMins;
            mDisplayTime = scheduled;
            if (mEta > 0) {
                mStatusText = context.getString(R.string.stop_info_scheduled_arrival);
            } else {
                mStatusText = context.getString(R.string.stop_info_scheduled_departure);
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
