/*
 * Copyright (C) 2013 The Android Open Source Project
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

/**
 * A utility class containing arithmetic and geometry helper methods.
 *
 * (from Glass Compass sample - https://github.com/googleglass/gdk-compass-sample/)
 */
public class MathUtils {

    /** The number of half winds for boxing the compass. */
    private static final int NUMBER_OF_HALF_WINDS = 16;

    /**
     * Calculates {@code a mod b} in a way that respects negative values (for example,
     * {@code mod(-1, 5) == 4}, rather than {@code -1}).
     *
     * @param a the dividend
     * @param b the divisor
     * @return {@code a mod b}
     */
    public static float mod(float a, float b) {
        return (a % b + b) % b;
    }

    /**
     * Converts the specified heading angle into an index between 0-15 that can be used to retrieve
     * the direction name for that heading (known as "boxing the compass", down to the half-wind
     * level).
     *
     * @param heading the heading angle
     * @return the index of the direction name for the angle
     */
    public static int getHalfWindIndex(float heading) {
        float partitionSize = 360.0f / NUMBER_OF_HALF_WINDS;
        float displacedHeading = MathUtils.mod(heading + partitionSize / 2, 360.0f);
        return (int) (displacedHeading / partitionSize);
    }
}
