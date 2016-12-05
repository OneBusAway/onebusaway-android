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

import android.content.Context;

import java.util.List;

import edu.usf.cutr.open311client.models.Service;

public class ServiceUtils {

    /**
     * Given a list of Open311 services (request types), mark which ones are transit-related in
     * place based on group, keyword, or text heuristic matching
     *
     * @param context
     * @param serviceList the list of Open311 services to potentially be marked as transit-related
     * @return true if the services were determined to be via all transit-related via heuristics,
     * false if heuristics matching wasn't used
     */
    public static boolean markTransitServices(Context context, List<Service> serviceList) {
        boolean stopProblemFound = false;
        boolean tripProblemFound = false;

        // Search transit services by groups (this is the "right" way to group transit services)
        for (Service s : serviceList) {
            if (ServiceUtils.isTransitStopServiceByText(s.getGroup()) && !stopProblemFound) {
                s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP);
                stopProblemFound = true;
            } else if (ServiceUtils.isTransitTripServiceByText(s.getGroup()) && !tripProblemFound) {
                s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP);
                tripProblemFound = true;
            }
        }

        // Search transit services by keywords
        if (!stopProblemFound || !tripProblemFound) {
            for (Service s : serviceList) {
                if (ServiceUtils.isTransitStopServiceByText(s.getKeywords()) && !stopProblemFound) {
                    s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                    s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP);
                    stopProblemFound = true;
                } else if (ServiceUtils.isTransitTripServiceByText(s.getKeywords())
                        && !tripProblemFound) {
                    s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                    s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP);
                    tripProblemFound = true;
                }
            }
        }

        if (stopProblemFound && tripProblemFound) {
            // Yay!  We had explicit matching via groups or keywords and didn't need to use heuristics
            return false;
        }

        // Search transit services by name and text matching heuristics - count matches
        int transitServiceCounter = 0;

        for (Service s : serviceList) {
            if (ServiceUtils.isTransitStopServiceByText(s.getService_name())) {
                s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP);
                stopProblemFound = true;
                transitServiceCounter++;
            } else if (ServiceUtils.isTransitTripServiceByText(s.getService_name())) {
                s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP);
                tripProblemFound = true;
                transitServiceCounter++;
            }
        }

        /**
         * If we've found a large number of potential transit services via heuristics search, assume all
         * services are transit related, and those without a group are stop-related
         */
        if (transitServiceCounter >= ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD) {
            for (Service s : serviceList) {
                if (s.getGroup() == null) {
                    s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                    s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP);
                }
            }
            return true;
        }

        // Add our own transit services if none have been found
        if (!stopProblemFound) {
            Service s1 = new Service(context.getString(R.string.ri_service_stop),
                    ReportConstants.STATIC_TRANSIT_SERVICE_STOP);
            s1.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
            serviceList.add(s1);
        }

        if (!tripProblemFound) {
            Service s2 = new Service(context.getString(R.string.ri_service_trip),
                    ReportConstants.STATIC_TRANSIT_SERVICE_TRIP);
            s2.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
            serviceList.add(s2);
        }
        return false;
    }

    /**
     * This method looks at the given text and predefined transit keywords (e.g., groups, keywords,
     * and service name), and tries to determine if the this is a transit stop service (i.e., stop
     * problem)
     *
     * @param text text to search for transit stop service match
     * @return true if the text is "transit stop-related"
     */
    public static boolean isTransitStopServiceByText(String text) {
        String[] transitKeywords = Application.get().getResources().
                getStringArray(R.array.report_stop_transit_category_keywords);
        for (String keyword : transitKeywords) {
            if (text != null && text.toLowerCase().contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method looks at the given text and predefined transit keywords (e.g., groups, keywords,
     * and service name), and tries to determine if the this is a transit trip service (i.e., trip
     * problem)
     *
     * @param text text to search for transit trip service match
     * @return true if the text is "transit trip-related"
     */
    public static boolean isTransitTripServiceByText(String text) {
        String[] transitKeywords = Application.get().getResources().
                getStringArray(R.array.report_trip_transit_category_keywords);

        for (String keyword : transitKeywords) {
            if (text != null && text.toLowerCase().contains(keyword)) {
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
