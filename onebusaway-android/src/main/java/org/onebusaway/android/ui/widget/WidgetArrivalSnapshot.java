/*
 * Copyright (C) 2025 Rob Godfrey (rob_godfrey@outlook.com)
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
package org.onebusaway.android.ui.widget;

import java.util.List;

/**
 * Arrival data fetched from OBA API to be persisted in SharedPrefs. This is saved so we can
 * update the widget periodically with a new relative time ("5 min" -> "4 min" -> "3 min")
 * without making additional API calls.
 */
public final class WidgetArrivalSnapshot {
    private final long fetchedAtMs;
    private final List<Route> routes;

    public WidgetArrivalSnapshot(long fetchedAtMs, List<Route> routes) {
        this.fetchedAtMs = fetchedAtMs;
        this.routes = routes;
    }

    public long getFetchedAtMs() {
        return fetchedAtMs;
    }

    public List<Route> getRoutes() {
        return routes;
    }

    /**
     * A single route with its upcoming arrivals.
     */
    public static final class Route {
        private final String shortName;
        private final List<Arrival> arrivals;

        public Route(String shortName, List<Arrival> arrivals) {
            this.shortName = shortName;
            this.arrivals = arrivals;
        }

        public String getShortName() {
            return shortName;
        }

        public List<Arrival> getArrivals() {
            return arrivals;
        }
    }

    /**
     * A single upcoming arrival for a route, holding both the scheduled and real-time predicted
     * times. Use {@link #getBestArrivalTimeMs()} to get whichever is most accurate.
     */
    public static final class Arrival {

        /// Unix ms, or 0 if unavailable
        private final long predictedArrivalTimeMs;

        /// Unix ms (always present)
        private final long scheduledArrivalTimeMs;

        public Arrival(long predictedArrivalTimeMs, long scheduledArrivalTimeMs) {
            this.predictedArrivalTimeMs = predictedArrivalTimeMs;
            this.scheduledArrivalTimeMs = scheduledArrivalTimeMs;
        }

        public long getPredictedArrivalTimeMs() {
            return predictedArrivalTimeMs;
        }

        public long getScheduledArrivalTimeMs() {
            return scheduledArrivalTimeMs;
        }

        /// Returns the predicted time if available, the scheduled time otherwise.
        public long getBestArrivalTimeMs() {
            return predictedArrivalTimeMs > 0 ? predictedArrivalTimeMs : scheduledArrivalTimeMs;
        }
    }
}
