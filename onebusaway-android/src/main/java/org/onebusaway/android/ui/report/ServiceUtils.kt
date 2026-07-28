/*
 * Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.onebusaway.android.ui.report

import android.content.Context
import edu.usf.cutr.open311client.models.Service
import java.util.Locale
import org.onebusaway.android.R

object ServiceUtils {
    fun markTransitServices(context: Context, serviceList: MutableList<Service>): Boolean {
        var stopProblemFound = false
        var tripProblemFound = false
        for (service in serviceList) {
            if (isTransitStopServiceByText(context, service.group) && !stopProblemFound) {
                service.group = ReportConstants.ISSUE_GROUP_TRANSIT
                service.type = ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP
                stopProblemFound = true
            } else if (isTransitTripServiceByText(context, service.group) && !tripProblemFound) {
                service.group = ReportConstants.ISSUE_GROUP_TRANSIT
                service.type = ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP
                tripProblemFound = true
            }
        }
        if (!stopProblemFound || !tripProblemFound) {
            for (service in serviceList) {
                if (isTransitStopServiceByText(context, service.keywords) && !stopProblemFound) {
                    service.group = ReportConstants.ISSUE_GROUP_TRANSIT
                    service.type = ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP
                    stopProblemFound = true
                } else if (isTransitTripServiceByText(context, service.keywords) && !tripProblemFound) {
                    service.group = ReportConstants.ISSUE_GROUP_TRANSIT
                    service.type = ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP
                    tripProblemFound = true
                }
            }
        }
        if (stopProblemFound && tripProblemFound) return false

        var transitServiceCounter = 0
        for (service in serviceList) {
            if (isTransitStopServiceByText(context, service.service_name)) {
                service.group = ReportConstants.ISSUE_GROUP_TRANSIT
                service.type = ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP
                stopProblemFound = true
                transitServiceCounter++
            } else if (isTransitTripServiceByText(context, service.service_name)) {
                service.group = ReportConstants.ISSUE_GROUP_TRANSIT
                service.type = ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP
                tripProblemFound = true
                transitServiceCounter++
            }
        }
        if (transitServiceCounter >= ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD) {
            serviceList.filter { it.group == null }.forEach {
                it.group = ReportConstants.ISSUE_GROUP_TRANSIT
                it.type = ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP
            }
            return true
        }
        if (!stopProblemFound) {
            serviceList += Service(context.getString(R.string.ri_service_stop), ReportConstants.STATIC_TRANSIT_SERVICE_STOP).apply {
                group = ReportConstants.ISSUE_GROUP_TRANSIT
            }
        }
        if (!tripProblemFound) {
            serviceList += Service(context.getString(R.string.ri_service_trip), ReportConstants.STATIC_TRANSIT_SERVICE_TRIP).apply {
                group = ReportConstants.ISSUE_GROUP_TRANSIT
            }
        }
        return false
    }

    fun isTransitStopServiceByText(context: Context, text: String?): Boolean = containsKeyword(context, R.array.report_stop_transit_category_keywords, text)

    fun isTransitTripServiceByText(context: Context, text: String?): Boolean = containsKeyword(context, R.array.report_trip_transit_category_keywords, text)

    fun isTransitStopServiceByType(type: String?): Boolean = type == ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP || type == ReportConstants.STATIC_TRANSIT_SERVICE_STOP

    fun isTransitTripServiceByType(type: String?): Boolean = type == ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP || type == ReportConstants.STATIC_TRANSIT_SERVICE_TRIP

    fun isTransitServiceByType(type: String?): Boolean = isTransitStopServiceByType(type) || isTransitTripServiceByType(type)

    fun isTransitOpen311ServiceByType(type: String?): Boolean = type == ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP || type == ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP

    fun isStopIdField(context: Context, desc: String?): Boolean {
        val text = desc?.lowercase(Locale.getDefault()) ?: return false
        return context.resources.getStringArray(R.array.report_stop_id_field_keywords).count { text.contains(it) } >= 2
    }

    private fun containsKeyword(context: Context, resourceId: Int, text: String?): Boolean {
        val normalized = text?.lowercase(Locale.getDefault()) ?: return false
        return context.resources.getStringArray(resourceId).any(normalized::contains)
    }
}
