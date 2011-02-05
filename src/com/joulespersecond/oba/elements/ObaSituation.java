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
package com.joulespersecond.oba.elements;

import java.util.List;

public interface ObaSituation extends ObaElement {
    public static final String REASON_TYPE_EQUIPMENT = "equipment";
    public static final String REASON_TYPE_ENVIRONMENT = "environment";
    public static final String REASON_TYPE_PERSONNEL = "personnel";
    public static final String REASON_TYPE_MISCELLANEOUS = "miscellaneous";
    public static final String REASON_TYPE_UNDEFINED = "undefined";

    public interface VehicleJourney {
        /**
         * @return An optional direction ID specify the direction of travel.
         */
        public String getDirection();

        /**
         * @return The ID of the affected route.
         */
        public String getRouteId();

        /**
         * @return A list of stop IDs along the vehicle journey that are affected.
         */
        public List<String> getStopIds();
    }

    public interface Affects {
        /**
         * The list of affected stop IDs.
         * @return
         */
        public List<String> getStopIds();

        /**
         * @return An array of transit vehicle journeys the situation affects.
         */
        public VehicleJourney[] getVehicleJourneys();
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

        /**
         * @return The string describing the consequence condition.
         */
        public String getCondition();

        /**
         * @return Optional details of the consequence condition.
         */
        public ConditionDetails getDetails();
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
     * @return The type of alert code.
     */
    public String getReasonType();

    /**
     * @return The Unix timestamp of when this situation was created.
     */
    public long getCreationTime();

    /**
     * @return Information on what stops and routes this situation affects.
     */
    public Affects getAffects();

    /**
     * @return An array of consequences of this situation
     */
    public Consequence[] getConsequences();

    // getInternal
    // getDetail?
}
