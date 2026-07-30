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
package org.onebusaway.android.util

import android.content.res.Resources
import java.util.Comparator
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalInfo

object ArrivalInfoUtils {
    class InfoComparator : Comparator<ArrivalInfo> {
        override fun compare(lhs: ArrivalInfo, rhs: ArrivalInfo): Int = lhs.eta.compareTo(rhs.eta)
    }

    fun findFirstNonNegativeArrival(infoList: ArrayList<ArrivalInfo>): Int = infoList.indexOfFirst { it.eta >= 0 }

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
