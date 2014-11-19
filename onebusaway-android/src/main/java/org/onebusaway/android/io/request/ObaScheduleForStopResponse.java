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
package org.onebusaway.android.io.request;

import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaReferencesElement;
import org.onebusaway.android.io.elements.ObaRouteSchedule;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaStopSchedule;
import org.onebusaway.android.io.elements.ObaStopScheduleElement;

/**
 * Response object for ObaScheduleForStopRequest requests.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaScheduleForStopResponse extends ObaResponseWithRefs
        implements ObaStopSchedule {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;

        private final ObaStopScheduleElement entry = ObaStopScheduleElement.EMPTY_OBJECT;
    }

    private final Data data;

    private ObaScheduleForStopResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public String getStopId() {
        return data.entry.getStopId();
    }

    public ObaStop getStop() {
        return data.references.getStop(getStopId());
    }

    @Override
    public String getTimeZone() {
        return data.entry.getTimeZone();
    }

    @Override
    public long getDate() {
        return data.entry.getDate();
    }

    @Override
    public CalendarDay[] getCalendarDays() {
        return data.entry.getCalendarDays();
    }

    @Override
    public ObaRouteSchedule[] getRouteSchedules() {
        return data.entry.getRouteSchedules();
    }

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
