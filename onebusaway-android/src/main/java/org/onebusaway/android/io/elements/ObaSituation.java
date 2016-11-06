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

import java.util.List;

public interface ObaSituation extends ObaElement {

    String SEVERITY_UNKNOWN = "unknown";
    String SEVERITY_NO_IMPACT = "noImpact";
    String SEVERITY_VERY_SLIGHT = "verySlight";
    String SEVERITY_SLIGHT = "slight";
    String SEVERITY_NORMAL = "normal";
    String SEVERITY_SEVERE = "severe";
    String SEVERITY_VERY_SEVERE = "verySevere";

    public interface AllAffects {

        /**
         * The list of affected direction IDs.
         *
         * @return The affected direction ID, if any.
         */
        public String getDirectionId();

        /**
         * @return The affected stop ID, if any.
         */
        public String getStopId();

        /**
         * @return The affected trip ID, if any.
         */
        public String getTripId();

        /**
         * @return The affected application ID, if any.
         */
        public String getApplicationId();

        /**
         * @return The affected route ID, if any.
         */
        public String getRouteId();

        /**
         * @return The affected agency ID, if any.
         */
        public String getAgencyId();
    }

    public interface ConditionDetails {

        /**
         * @return For diversion conditions, this specifies the stop IDs
         * that are diverted.
         */
        public List<String> getDiversionStopIds();

        /**
         * @return For diversion conditions, this specifies the optional
         * path that the vehicle will take in the diversion.
         */
        public ObaShape getDiversionPath();
    }

    public interface Consequence {

        public static final String CONDITION_DIVERSION = "diversion";
        public static final String CONDITION_ALTERED = "altered";
        public static final String CONDITION_DETOUR = "detour";

        /**
         * @return The string describing the consequence condition.
         */
        public String getCondition();

        /**
         * @return Optional details of the consequence condition.
         */
        public ConditionDetails getDetails();
    }

    public interface ActiveWindow {

        /**
         * @return the starting time of the active window for this situation
         */
        public long getFrom();

        /**
         *
         * @return the ending time of the active window for this situation
         */
        public long getTo();
    }

    /**
     * @return Optional short summary of the situation.
     */
    public String getSummary();

    /**
     * @return Optional longer description of the situation.
     */
    public String getDescription();

    /**
     * @return Optional advice to the rider.
     */
    public String getAdvice();

    /**
     * @return The service alert code.
     */
    public String getReason();

    /**
     * @return The Unix timestamp of when this situation was created.
     */
    public long getCreationTime();

    /**
     * @return Information on what stops and routes this situation affects.
     */
    public AllAffects[] getAllAffects();

    /**
     * @return An array of consequences of this situation
     */
    public Consequence[] getConsequences();

    /**
     * @return The severity of the situation.
     */
    public String getSeverity();

    /*
     * @return An array of active windows of this situation
     */
    public ActiveWindow[] getActiveWindows();


    /**
     * @return A URL to a human-readable website with more details on the alert
     */
    public String getUrl();
}
