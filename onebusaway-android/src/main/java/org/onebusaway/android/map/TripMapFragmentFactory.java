/*
 * Copyright (C) 2024 Open Transit Software Foundation
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
package org.onebusaway.android.map;

import org.onebusaway.android.BuildConfig;

import java.lang.reflect.Method;

import androidx.fragment.app.Fragment;

/**
 * Creates the flavor-specific TripMapFragment via reflection, following the
 * same
 * pattern as {@link ObaMapFragment#newInstance()}.
 */
public class TripMapFragmentFactory {

    public static final String TAG = "TripMapFragment";

    public static Fragment newInstance(String tripId, String stopId) {
        try {
            Class<?> clazz = Class.forName(BuildConfig.TRIP_MAP_FRAGMENT_CLASS);
            Method method = clazz.getMethod("newInstance", String.class, String.class);
            return (Fragment) method.invoke(null, tripId, stopId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Trip map fragment not found: "
                    + BuildConfig.TRIP_MAP_FRAGMENT_CLASS, e);
        }
    }
}
