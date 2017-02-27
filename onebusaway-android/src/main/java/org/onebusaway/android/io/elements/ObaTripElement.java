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
package org.onebusaway.android.io.elements;

public final class ObaTripElement implements ObaTrip {

    public static final ObaTripElement EMPTY_OBJECT = new ObaTripElement();

    public static final ObaTripElement[] EMPTY_ARRAY = new ObaTripElement[]{};

    private final String id;

    private final String tripShortName;

    private final String shapeId;

    private final String directionId;

    private final String serviceId;

    private final String tripHeadsign;

    private final String timeZone;

    private final String routeId;

    private final String blockId;

    private ObaTripElement() {
        id = "";
        tripShortName = "";
        shapeId = "";
        directionId = "";
        serviceId = "";
        tripHeadsign = "";
        timeZone = "";
        routeId = "";
        blockId = "";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getShortName() {
        return tripShortName;
    }

    @Override
    public String getShapeId() {
        return shapeId;
    }

    @Override
    public int getDirectionId() {
        return Integer.getInteger(directionId, DIRECTION_OUTBOUND);
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public String getHeadsign() {
        return tripHeadsign;
    }

    @Override
    public String getTimezone() {
        return timeZone;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public String getBlockId() {
        return blockId;
    }
}
