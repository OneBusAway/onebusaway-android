/*
* Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.ui.util;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.report.constants.ReportConstants;

public class ServiceUtils {

    /**
     * This method looks at the predefined transit keywords, and tries to determine if the this is
     * a transit stop service (e.g., stop problem)
     *
     * @param key Service name
     * @return true if it is a transit service
     */
    public static boolean isTransitStopServiceByKey(String key) {
        String[] transitKeywords = Application.get().getResources().
                getStringArray(R.array.report_stop_transit_category_keywords);

        for (String keyword : transitKeywords) {
            if (key != null && key.toLowerCase().contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method looks at the predefined transit keywords, and tries to determine if the this is
     * a transit trip service (e.g., arrival time problem)
     *
     * @param key Service name
     * @return true if it is a transit service
     */
    public static boolean isTransitTripServiceByKey(String key) {
        String[] transitKeywords = Application.get().getResources().
                getStringArray(R.array.report_trip_transit_category_keywords);

        for (String keyword : transitKeywords) {
            if (key != null && key.toLowerCase().contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param type Service type
     * @return true if it is a transit stop service
     */
    public static boolean isTransitStopServiceByType(String type) {
        return ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP.equals(type)
                || ReportConstants.STATIC_TRANSIT_SERVICE_STOP.equals(type);
    }

    /**
     * @param type Service type
     * @return true if it is a transit trip service
     */
    public static boolean isTransitTripServiceByType(String type) {
        return ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP.equals(type)
                || ReportConstants.STATIC_TRANSIT_SERVICE_TRIP.equals(type);

    }

    /**
     * @param type Service type
     * @return true if it is a transit service
     */
    public static boolean isTransitServiceByType(String type) {
        return isTransitStopServiceByType(type) || isTransitTripServiceByType(type);
    }

    /**
     * @param type Service type
     * @return true if it is a transit service and it is coming from open311 endpoint
     */
    public static boolean isTransitOpen311ServiceByType(String type) {
        return ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP.equals(type)
                || ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP.equals(type);
    }

    /**
     * This method determines if the given dynamic open311 field is for stop id.
     *
     * @param desc field description
     * @return true if the field description matches at least two keywords
     */
    public static boolean isStopIdField(String desc) {
        String[] stopIdKeywords = Application.get().getResources().
                getStringArray(R.array.report_stop_id_field_keywords);

        boolean didMatch = false;

        for (String keyword : stopIdKeywords) {
            if (desc != null && desc.toLowerCase().contains(keyword)) {
                if (didMatch) {
                    // if this is the second matched keyword then return true
                    return true;
                } else {
                    didMatch = true;
                }
            }
        }
        return false;
    }
}
