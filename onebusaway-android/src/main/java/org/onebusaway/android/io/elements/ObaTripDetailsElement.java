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

/**
 * Extended information for a specific trip
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_TripDetails}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaTripDetailsElement implements ObaTripDetails {

    public static final ObaTripDetailsElement EMPTY_OBJECT = new ObaTripDetailsElement();

    public static final ObaTripDetailsElement[] EMPTY_ARRAY = new ObaTripDetailsElement[]{};

    private final String tripId;

    private final ObaTripSchedule schedule;

    private final ObaTripStatusElement status;

    private ObaTripDetailsElement() {
        tripId = "";
        schedule = null;
        status = null;
    }

    @Override
    public ObaTripSchedule getSchedule() {
        return schedule;
    }

    @Override
    public ObaTripStatus getStatus() {
        return status;
    }

    @Override
    public String getId() {
        return tripId;
    }
}
