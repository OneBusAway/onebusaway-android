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
package org.onebusaway.android.directions.realtime;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.model.ItineraryDescription;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

/**
 * This service is started after a trip is planned by the user so they can be notified if the
 * trip results for their request change in the near future. For example, if a user plans a trip,
 * and then the top result for that trip gets delayed by 20 minutes, the user will be notified
 * that new trip results are available.
 */
public class RealtimeService {
    private static final String TAG = "RealtimeService";
    private static final String ITINERARY_DESC = ".ItineraryDesc";
    private static final String ITINERARY_END_DATE = ".ItineraryEndDate";

    /**
     * Start realtime updates.
     *
     * @param source Activity from which class information is read
     * @param bundle Bundle with selected itinerary/parameters
     * @param context Context with which work manager is initialized
     */
    public static void start(Bundle bundle, Activity source, Context context){

        // FIXME - Figure out why sometimes the bundle is empty - see #790 and #791
        if(bundle == null){
            Log.d(TAG, "Bundle is null");
            return;
        }

        if(isRealTimeEnabled() && isItineraryRealTime(bundle)){
            Data data = buildData(bundle, source.getClass());

            PeriodicWorkRequest checkItineraries = new PeriodicWorkRequest
                    .Builder(RealTimeWorker.class, OTPConstants.DEFAULT_UPDATE_INTERVAL_TRIP_TIME, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .setInitialDelay(getStartTime(bundle), TimeUnit.MILLISECONDS)
                    .build();

            Log.d(TAG, "RealtimeService Enqueued");
            WorkManager workManager = WorkManager.getInstance(context);

            workManager.enqueueUniquePeriodicWork(
                    OTPConstants.REALTIME_UNIQUE_WORKER_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    checkItineraries);
        }
    }

    private static boolean isRealTimeEnabled(){
        SharedPreferences prefs = Application.getPrefs();
        Log.d(TAG, "RealtimeEnabled :"+String.valueOf(prefs.getBoolean(OTPConstants.PREFERENCE_KEY_LIVE_UPDATES, true)));
        return prefs.getBoolean(OTPConstants.PREFERENCE_KEY_LIVE_UPDATES, true);
    }

    private static boolean isItineraryRealTime(Bundle bundle){
        Itinerary itinerary = getItinerary(bundle);
        boolean realtimeLegsOnItineraries = false;
        for (Leg leg : itinerary.legs) {
            if (leg.realTime) {
                realtimeLegsOnItineraries = true;
            }
        }
        Log.d(TAG, "RealtimeItinerary :"+String.valueOf(realtimeLegsOnItineraries));
        return realtimeLegsOnItineraries;
    }

    private static Data buildData(Bundle bundle, final Class<? extends Activity> source) {
        bundle.putSerializable(OTPConstants.NOTIFICATION_TARGET, source);

        Data result = new TripRequestBuilder(bundle).getRealTimeData();
        Data.Builder builder = new Data.Builder();
        builder.putAll(result);

        Itinerary itinerary = getItinerary(bundle);
        ItineraryDescription desc = new ItineraryDescription(itinerary);
        List<String> idList = desc.getTripIds();

        String[] ids = idList.toArray(new String[idList.size()]);
        long endDate = desc.getEndDate().getTime();
        builder.putStringArray(ITINERARY_DESC, ids);
        builder.putLong(ITINERARY_END_DATE, endDate);

        builder.putString(OTPConstants.NOTIFICATION_TARGET,
                bundle.getSerializable(OTPConstants.NOTIFICATION_TARGET).toString());

        Data simplifiedData = builder.build();
        return simplifiedData;
    }

    public static Bundle toBundle(Data data) {
        Bundle bundle = TripRequestBuilder.convertRealTimeData(data);
        Map<String, Object> map = data.getKeyValueMap();

        bundle.putStringArray(ITINERARY_DESC, (String[]) map.get(ITINERARY_DESC));
        bundle.putLong(ITINERARY_END_DATE, (Long) map.get(ITINERARY_END_DATE));

        bundle.putSerializable(OTPConstants.NOTIFICATION_TARGET, (String) map.get(OTPConstants.NOTIFICATION_TARGET));

        return bundle;
    }

    /**
     * Check to see if the start of real-time trip updates should be rescheduled, and if necessary
     * reschedule it
     *
     * @param bundle trip details to be passed to TripRequestBuilder constructor
     * @return Time to delay the start of trip updates
     */
    private static long getStartTime(Bundle bundle){
        Date start = new TripRequestBuilder(bundle).getDateTime();
        Date queryStart = new Date(start.getTime() - OTPConstants.REALTIME_SERVICE_QUERY_WINDOW);
        boolean reschedule = new Date().before(queryStart);
        if(reschedule){
            return queryStart.getTime();
        }
        return 0;
    }

    private static Itinerary getItinerary(Bundle bundle) {
        ArrayList<Itinerary> itineraries = (ArrayList<Itinerary>) bundle
                .getSerializable(OTPConstants.ITINERARIES);
        int i = bundle.getInt(OTPConstants.SELECTED_ITINERARY);
        return itineraries.get(i);
    }
}
