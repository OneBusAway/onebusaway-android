/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Benjamin Du (bendu@me.com), and individual contributors.
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
package org.onebusaway.android.travelbehavior.model;

import android.location.Location;

public class DestinationReminderData {
    public String currStopId;

    public String destStopId;

    public String tripId;

    public String routeId;

    public Long regionId;

    public Long localElapsedRealtimeNanos;

    public Long localSystemCurrMillis;

    public Long obaServerTimestamp;

    public TravelBehaviorInfo.LocationInfo locationInfo;

    public DestinationReminderData(String currStopId, String destStopId, String tripId,
                                   String routeId, Long regionId, Long localElapsedRealtimeNanos,
                                   Long localSystemCurrMillis, Long obaServerTimestamp,
                                   Location location) {
        this.currStopId = currStopId;
        this.destStopId = destStopId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.regionId = regionId;
        this.localElapsedRealtimeNanos = localElapsedRealtimeNanos;
        this.localSystemCurrMillis = localSystemCurrMillis;
        this.obaServerTimestamp = obaServerTimestamp;
        this.locationInfo = new TravelBehaviorInfo.LocationInfo(location);
    }

    public Long getLocalElapsedRealtimeNanos() {
        return localElapsedRealtimeNanos;
    }

    public Long getLocalSystemCurrMillis() {
        return localSystemCurrMillis;
    }
}
