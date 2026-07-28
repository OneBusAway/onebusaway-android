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
package org.onebusaway.android.util

/** Arithmetic and geometry helpers. */
object MathUtils {
    fun mod(a: Float, b: Float): Float = (a % b + b) % b

    fun getHalfWindIndex(heading: Float, numHalfWinds: Int): Int {
        val partitionSize = 360f / numHalfWinds
        val displacedHeading = mod(heading + partitionSize / 2, 360f)
        return (displacedHeading / partitionSize).toInt()
    }

    fun toDirection(orientation: Double): Double {
        var direction = (-orientation + 90) % 360
        if (direction < 0) direction += 360
        return direction
    }
}
