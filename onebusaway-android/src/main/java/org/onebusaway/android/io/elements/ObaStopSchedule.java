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


public interface ObaStopSchedule {

    /**
     * Element that lists out all the days that a particular stop has service.
     */
    public static final class CalendarDay {

        public static final CalendarDay[] EMPTY_ARRAY = new CalendarDay[]{};

        private final long date;

        private final long group;

        CalendarDay() {
            date = 0;
            group = 0;
        }

        /**
         * @return The date of service in milliseconds since the epoch.
         */
        public long getDate() {
            return date;
        }

        /**
         * @return We provide a group id that groups <stopCalendarDay/> into
         * collections of days with similar service. For example, Monday-Friday
         * might all have the same schedule and the same group id as result, while
         * Saturday and Sunday have a different weekend schedule, so they'd get
         * their own group id.
         */
        public long getGroup() {
            return group;
        }
    }

    /**
     * @return Information about the requested stop.
     */
    public String getStopId();

    /**
     * @return The time zone the stop is located in.
     */
    public String getTimeZone();

    /**
     * @return The active date for the returned calendar.
     */
    public long getDate();

    /**
     * @return Elements that list out all the days that a particular stop has service.
     */
    public CalendarDay[] getCalendarDays();

    /**
     * @return The schedules of all the routes that service this stop.
     */
    public ObaRouteSchedule[] getRouteSchedules();
}
