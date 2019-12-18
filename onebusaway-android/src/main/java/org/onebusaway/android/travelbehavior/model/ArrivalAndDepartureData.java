/*
 * Copyright (C) 2019 University of South Florida
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

import org.onebusaway.android.io.elements.ObaArrivalInfo;

import android.location.Location;

import java.util.ArrayList;
import java.util.List;

public class ArrivalAndDepartureData {
    public TravelBehaviorInfo.LocationInfo locationInfo;

    public List<ObaArrivalInfoPojo> arrivalList;

    public Long localElapsedRealtimeNanos;

    public Long localSystemCurrMillis;

    public Long obaServerTimestamp;

    public String stopId;

    public Long regionId;

    public String url;

    public ArrivalAndDepartureData() {
    }

    public ArrivalAndDepartureData(ObaArrivalInfo[] info, String stopId, Long regionId,
                                   String url, Long localElapsedRealtimeNanos,
                                   Long localSystemCurrMillis, Long obaServerTimestamp) {
        this.stopId = stopId;
        this.regionId = regionId;
        this.url = url;
        this.localElapsedRealtimeNanos = localElapsedRealtimeNanos;
        this.localSystemCurrMillis = localSystemCurrMillis;
        this.obaServerTimestamp = obaServerTimestamp;

        arrivalList = new ArrayList<>();
        if (info != null && info.length != 0) {
            for (ObaArrivalInfo oai: info) {
                arrivalList.add(new ObaArrivalInfoPojo(oai));
            }
        }
    }

    public List<ObaArrivalInfoPojo> getArrivalList() {
        return arrivalList;
    }

    public Long getLocalElapsedRealtimeNanos() {
        return localElapsedRealtimeNanos;
    }

    public Long getLocalSystemCurrMillis() {
        return localSystemCurrMillis;
    }

    public Long getObaServerTimestamp() {
        return obaServerTimestamp;
    }

    public String getStopId() {
        return stopId;
    }

    public Long getRegionId() {
        return regionId;
    }

    public String getUrl() {
        return url;
    }

    public void setLocation(Location location) {
        locationInfo = new TravelBehaviorInfo.LocationInfo(location);
    }
}
