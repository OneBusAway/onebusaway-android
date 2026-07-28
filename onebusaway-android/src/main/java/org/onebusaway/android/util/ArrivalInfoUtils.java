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

package org.onebusaway.android.util;

import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import org.onebusaway.android.R;
import org.onebusaway.android.ui.arrivals.ArrivalInfo;

public class ArrivalInfoUtils {

  public static final class InfoComparator implements Comparator<ArrivalInfo> {

    public int compare(ArrivalInfo lhs, ArrivalInfo rhs) {
      return (int) (lhs.getEta() - rhs.getEta());
    }
  }

  /**
   * Returns the index in the provided infoList for the first non-negative arrival ETA in the list,
   * or -1 if no non-negative ETAs exist in the list
   *
   * @param infoList list to search for non-negative arrival times, ordered by relative ETA from
   *     negative infinity to positive infinity
   * @return the index in the provided infoList for the first non-negative arrival ETA in the list,
   *     or -1 if no non-negative ETAs exist in the list
   */
  public static int findFirstNonNegativeArrival(@NonNull ArrayList<ArrivalInfo> infoList) {
    for (int i = 0; i < infoList.size(); i++) {
      ArrivalInfo info = infoList.get(i);
      if (info.getEta() >= 0) {
        return i;
      }
    }
    // We didn't find any non-negative ETAs
    return -1;
  }

  /**
   * Returns the indexes in the provided infoList for the preferred arrivals to be prioritized in
   * the header, or null if no non-negative ETAs exist in the list. An arrival is preferred when its
   * route is starred ({@code favoriteRouteIds} contains its route id — a route star is wholesale
   * now, #1751). If none are favorited, the indexes returned may simply be the indexes of the first
   * (and second, if it exists) non-negative arrival times.
   *
   * @param infoList list to search for non-negative arrival times, ordered by relative ETA from
   *     negative infinity to positive infinity
   * @param favoriteRouteIds the ids of the user's starred routes
   * @return the indexes in the provided infoList for the preferred arrivals to be prioritized in
   *     the header, or null if no non-negative ETAs exist in the list
   */
  public static @Nullable ArrayList<Integer> findPreferredArrivalIndexes(
      @NonNull ArrayList<ArrivalInfo> infoList, @NonNull Set<String> favoriteRouteIds) {
    // Start by getting the index of the first non-negative arrival time
    int firstIndex = findFirstNonNegativeArrival(infoList);
    if (firstIndex == -1) {
      return null;
    }
    // Find any favorites
    ArrayList<Integer> preferredIndexes = new ArrayList<>();
    for (int i = firstIndex; i < infoList.size(); i++) {
      ArrivalInfo info = infoList.get(i);
      if (favoriteRouteIds.contains(info.getRouteId())) {
        preferredIndexes.add(i);
      }
    }

    // If we have at least two favorites, that's enough to fill the header - return them
    if (preferredIndexes.size() >= 2) {
      return preferredIndexes;
    }

    // If we have one favorite, and the index is different from the firstIndex, then add the
    // firstIndex and return
    if (preferredIndexes.size() == 1 && preferredIndexes.get(0) != firstIndex) {
      preferredIndexes.add(firstIndex);
    }

    // If we have no preferred indexes (i.e., starred route/headsigns) at this point, then add the
    // firstIndex
    if (preferredIndexes.size() == 0) {
      preferredIndexes.add(firstIndex);

      // If there is another non-negative arrival time, then add it too
      int secondIndex = firstIndex + 1;
      if (secondIndex < infoList.size()) {
        preferredIndexes.add(secondIndex);
      }
    }

    return preferredIndexes;
  }

  // The schedule-deviation color methods that used to live here (computeColor, statusColor,
  // computeColorFromDeviation) moved to org.onebusaway.android.util.ScheduleDeviation in #2043.
  // They bucketed on a strict sign test over whole minutes, so "on time" required the predicted and
  // scheduled minute-past-epoch to be exactly equal and was nearly unreachable on live data; the
  // replacement takes a full-precision kotlin.time.Duration and applies the shared on-time band.

  /**
   * Computes the arrival status label for an upcoming arrival.
   *
   * <p>The bucket comes from {@link ScheduleDeviation}, the same call that picks the color, so the
   * words and the hue can't contradict each other — before #2043 this took the raw signed minutes
   * and applied its own strict sign test, which after the on-time band landed meant a vehicle could
   * read "1 min late" in the on-time green.
   *
   * @param status the deviation bucket, from {@link ScheduleDeviation#status}
   * @param minutes how far off schedule, in whole minutes, unsigned — only read when [status] is
   *     early or delayed
   */
  public static @NonNull String computeArrivalLabel(
      @NonNull Resources res, @NonNull ScheduleDeviation.Status status, long minutes) {
    switch (status) {
      case DELAYED:
        return res.getQuantityString(R.plurals.stop_info_arrive_delayed, (int) minutes, minutes);
      case EARLY:
        return res.getQuantityString(R.plurals.stop_info_arrive_early, (int) minutes, minutes);
      default:
        return res.getString(R.string.stop_info_ontime);
    }
  }
}
