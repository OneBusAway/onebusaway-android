package com.joulespersecond.seattlebusbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.content.res.Resources;

import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaArrivalInfo;

final class StopInfo {
    final static class StopInfoComparator implements Comparator<StopInfo> {
        public int compare(StopInfo lhs, StopInfo rhs) {
            return (int)(lhs.mEta - rhs.mEta);
        }
    }

    public static final ArrayList<StopInfo>
    convertObaArrivalInfo(Context context, ObaArray arrivalInfo, ArrayList<String> filter) {
        final int len = arrivalInfo.length();
        ArrayList<StopInfo> result = new ArrayList<StopInfo>(len);
        final long ms = System.currentTimeMillis();
        if (filter != null && filter.size() > 0) {
            for (int i=0; i < len; ++i) {
                ObaArrivalInfo arrival = arrivalInfo.getArrivalInfo(i);
                if (filter.contains(arrival.getRouteId())) {
                    result.add(new StopInfo(context, arrivalInfo.getArrivalInfo(i), ms));
                }
            }            
        }
        else {
            for (int i=0; i < len; ++i) {
                result.add(new StopInfo(context, arrivalInfo.getArrivalInfo(i), ms));
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
            final long delay = predictedMins - scheduledMins;
            
            if (mEta >= 0) {
                // Bus is arriving
                if (delay > 0) {
                    // Arriving delayed
                    mColor = R.color.stop_info_delayed;
                    if (delay == 1) {
                        mStatusText = res.getString(R.string.stop_info_arrive_delayed1);                            
                    }
                    else {
                        String fmt = res.getString(R.string.stop_info_arrive_delayed);
                        mStatusText = String.format(fmt, delay);                        
                    }
                }
                else if (delay < 0) {
                    // Arriving early
                    mColor = R.color.stop_info_early;
                    if (delay == -1) {
                        mStatusText = res.getString(R.string.stop_info_arrive_early1);                            
                    }
                    else {
                        String fmt = res.getString(R.string.stop_info_arrive_early);
                        mStatusText = String.format(fmt, -delay);                                
                    }
                }
                else {
                    // Arriving on time
                    mColor = R.color.stop_info_ontime;
                    mStatusText = res.getString(R.string.stop_info_ontime);
                }
            } 
            else {
                // Bus is departing
                if (delay > 0) {
                    // Departing delayed
                    mColor = R.color.stop_info_delayed;
                    if (delay == 1) {
                        mStatusText = res.getString(R.string.stop_info_depart_delayed1);                            
                    }
                    else {
                        String fmt = res.getString(R.string.stop_info_depart_delayed);
                        mStatusText = String.format(fmt, delay);                        
                    }
                } 
                else if (delay < 0) {
                    // Departing early
                    mColor = R.color.stop_info_early;
                    if (delay == -1) {
                        mStatusText = res.getString(R.string.stop_info_depart_early1);                            
                    }
                    else {
                        String fmt = res.getString(R.string.stop_info_depart_early);
                        mStatusText = String.format(fmt, -delay);                        
                    }
                }
                else {
                    // Departing on time
                    mColor = R.color.stop_info_ontime;
                    mStatusText = res.getString(R.string.stop_info_ontime);
                }
            }                
        }
        else {
            mColor = R.color.stop_info_ontime;
            
            mEta = scheduledMins - nowMins;
            mDisplayTime = scheduled;
            if (mEta > 0) {
                mStatusText = res.getString(R.string.stop_info_scheduled_arrival);
            } else {
                mStatusText = res.getString(R.string.stop_info_scheduled_departure);                    
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
