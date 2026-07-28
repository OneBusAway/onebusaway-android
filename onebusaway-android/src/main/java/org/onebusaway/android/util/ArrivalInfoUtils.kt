/* Copyright (C) 2016 University of South Florida */
package org.onebusaway.android.util

import android.content.res.Resources
import java.util.Comparator
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalInfo

object ArrivalInfoUtils {
    class InfoComparator : Comparator<ArrivalInfo> {
        override fun compare(lhs: ArrivalInfo, rhs: ArrivalInfo): Int = lhs.eta.compareTo(rhs.eta)
    }

    @JvmStatic
    fun findFirstNonNegativeArrival(infoList: ArrayList<ArrivalInfo>): Int = infoList.indexOfFirst { it.eta >= 0 }

    @JvmStatic
    fun findPreferredArrivalIndexes(
        infoList: ArrayList<ArrivalInfo>,
        favoriteRouteIds: Set<String>
    ): ArrayList<Int>? {
        val firstIndex = findFirstNonNegativeArrival(infoList)
        if (firstIndex == -1) return null
        val preferred = ArrayList<Int>()
        for (index in firstIndex until infoList.size) {
            if (infoList[index].routeId in favoriteRouteIds) preferred += index
        }
        if (preferred.size >= 2) return preferred
        if (preferred.size == 1 && preferred[0] != firstIndex) preferred += firstIndex
        if (preferred.isEmpty()) {
            preferred += firstIndex
            if (firstIndex + 1 < infoList.size) preferred += firstIndex + 1
        }
        return preferred
    }

    @JvmStatic
    fun computeArrivalLabel(
        resources: Resources,
        status: ScheduleDeviation.Status,
        minutes: Long
    ): String = when (status) {
        ScheduleDeviation.Status.DELAYED ->
            resources.getQuantityString(R.plurals.stop_info_arrive_delayed, minutes.toInt(), minutes)
        ScheduleDeviation.Status.EARLY ->
            resources.getQuantityString(R.plurals.stop_info_arrive_early, minutes.toInt(), minutes)
        else -> resources.getString(R.string.stop_info_ontime)
    }
}
