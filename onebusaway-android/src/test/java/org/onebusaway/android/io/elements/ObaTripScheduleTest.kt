/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.io.elements

import org.junit.Assert.assertEquals
import org.junit.Test

class ObaTripScheduleTest {

    @Test
    fun `findSegmentStartIndex returns 0 for distance in first segment`() {
        val schedule = createSchedule(doubleArrayOf(0.0, 1000.0, 3000.0))
        assertEquals(0, schedule.findSegmentStartIndex(500.0))
    }

    @Test
    fun `findSegmentStartIndex returns 1 for distance in second segment`() {
        val schedule = createSchedule(doubleArrayOf(0.0, 1000.0, 3000.0))
        assertEquals(1, schedule.findSegmentStartIndex(1500.0))
    }

    @Test
    fun `findSegmentStartIndex returns 0 for distance exactly at first stop`() {
        val schedule = createSchedule(doubleArrayOf(0.0, 1000.0, 3000.0))
        assertEquals(0, schedule.findSegmentStartIndex(0.0))
    }

    @Test
    fun `findSegmentStartIndex returns 1 for distance exactly at middle stop`() {
        val schedule = createSchedule(doubleArrayOf(0.0, 1000.0, 3000.0))
        assertEquals(1, schedule.findSegmentStartIndex(1000.0))
    }

    @Test
    fun `findSegmentStartIndex returns last segment for distance exactly at last stop`() {
        val schedule = createSchedule(doubleArrayOf(0.0, 1000.0, 3000.0))
        assertEquals(1, schedule.findSegmentStartIndex(3000.0))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `findSegmentStartIndex throws for distance before first stop`() {
        val schedule = createSchedule(doubleArrayOf(100.0, 1000.0, 3000.0))
        schedule.findSegmentStartIndex(50.0)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `findSegmentStartIndex throws for distance after last stop`() {
        val schedule = createSchedule(doubleArrayOf(0.0, 1000.0, 3000.0))
        schedule.findSegmentStartIndex(3500.0)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `findSegmentStartIndex throws for fewer than 2 stops`() {
        val schedule = createSchedule(doubleArrayOf(0.0))
        schedule.findSegmentStartIndex(0.0)
    }

    private fun createSchedule(distances: DoubleArray): ObaTripSchedule {
        val stopTimeClass = ObaTripSchedule.StopTime::class.java
        val stCtor = stopTimeClass.getDeclaredConstructor()
        stCtor.isAccessible = true

        val stopTimesArray = java.lang.reflect.Array.newInstance(stopTimeClass, distances.size)
        for (i in distances.indices) {
            val st = stCtor.newInstance()
            setField(st, "distanceAlongTrip", distances[i])
            setField(st, "stopId", "stop_$i")
            java.lang.reflect.Array.set(stopTimesArray, i, st)
        }

        val schedCtor = ObaTripSchedule::class.java.getDeclaredConstructor()
        schedCtor.isAccessible = true
        val schedule = schedCtor.newInstance()
        setField(schedule, "stopTimes", stopTimesArray)
        return schedule
    }

    private fun setField(obj: Any, fieldName: String, value: Any?) {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }
}
