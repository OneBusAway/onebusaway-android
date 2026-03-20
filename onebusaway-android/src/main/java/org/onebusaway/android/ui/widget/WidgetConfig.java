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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Stores the user's configuration for a single Stop Times widget.
 * Serialized to JSON and persisted via {@link WidgetPrefs}.
 */
public final class WidgetConfig {
    private final String stopId;
    private final String stopName;
    private final String widgetName;
    // Maps route ID → short name (e.g. "1_100" → "44")
    private final Map<String, String> routeShortNames;

    public WidgetConfig(String stopId, String stopName, String widgetName, Map<String, String> routeShortNames) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.widgetName = widgetName;
        this.routeShortNames = routeShortNames;
    }

    public String getStopId() {
        return stopId;
    }

    public String getStopName() {
        return stopName;
    }

    public String getWidgetName() {
        return widgetName;
    }

    public Set<String> getRoutes() {
        return routeShortNames != null ? routeShortNames.keySet() : Collections.emptySet();
    }

    public Map<String, String> getRouteShortNameMap() {
        return routeShortNames != null ? routeShortNames : Collections.emptyMap();
    }
}
