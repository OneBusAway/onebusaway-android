/* Copyright (C) 2013 The Android Open Source Project */
package org.onebusaway.android.util

/** Arithmetic and geometry helpers. */
object MathUtils {
    @JvmStatic fun mod(a: Float, b: Float): Float = (a % b + b) % b

    @JvmStatic
    fun getHalfWindIndex(heading: Float, numHalfWinds: Int): Int {
        val partitionSize = 360f / numHalfWinds
        val displacedHeading = mod(heading + partitionSize / 2, 360f)
        return (displacedHeading / partitionSize).toInt()
    }

    @JvmStatic
    fun toDirection(orientation: Double): Double {
        var direction = (-orientation + 90) % 360
        if (direction < 0) direction += 360
        return direction
    }
}
