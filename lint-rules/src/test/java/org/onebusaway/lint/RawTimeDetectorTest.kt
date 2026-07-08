/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class RawTimeDetectorTest {

    private fun lintKotlin(source: String) =
        lint()
            .files(kotlin(source).indented())
            .issues(RawTimeDetector.ISSUE, RawTimeDetector.ISSUE_VALUE)
            .allowMissingSdk()
            .run()

    /** The motivating case: a server/scheduled timestamp minus the device clock (#27 / #1612). */
    @Test
    fun flagsServerTimestampMinusDeviceNow() {
        lintKotlin(
            """
            package test
            fun etaMillis(departTime: Long): Long {
                return departTime - System.currentTimeMillis()
            }
            """,
        ).expectWarningCount(1)
    }

    /** Comparison against a raw clock read is flagged too. */
    @Test
    fun flagsComparisonAgainstDeviceNow() {
        lintKotlin(
            """
            package test
            fun expired(deadline: Long): Boolean {
                return System.currentTimeMillis() > deadline
            }
            """,
        ).expectWarningCount(1)
    }

    /** UnwrappedClockValue: a reading coming to rest in a bare `Long` local (would escape untyped). */
    @Test
    fun flagsReadingRestingInBareLocal() {
        lintKotlin(
            """
            package test
            fun now(): Long {
                val wall = System.currentTimeMillis()
                return wall
            }
            """,
        ).expectWarningCount(1)
    }

    /** UnwrappedClockValue: a reading returned as a bare `Long`. */
    @Test
    fun flagsReadingReturnedAsBareLong() {
        lintKotlin(
            """
            package test
            fun now(): Long = System.currentTimeMillis()
            """,
        ).expectWarningCount(1)
    }

    /** UnwrappedClockValue: a reading as a bare `Long` default parameter value. */
    @Test
    fun flagsReadingAsBareLongDefaultParameter() {
        lintKotlin(
            """
            package test
            fun refresh(nowMs: Long = System.currentTimeMillis()): Long = nowMs
            """,
        ).expectWarningCount(1)
    }

    /** Passing a reading straight through to a consuming API doesn't let it rest untyped — not flagged. */
    @Test
    fun doesNotFlagPassThroughArgument() {
        lintKotlin(
            """
            package test
            fun record(timestampMs: Long) {}
            fun stamp() {
                record(System.currentTimeMillis())
            }
            """,
        ).expectClean()
    }

    /** System.nanoTime() is a time producer too. */
    @Test
    fun flagsNanoTimeSubtraction() {
        lintKotlin(
            """
            package test
            fun elapsed(startNanos: Long): Long {
                return System.nanoTime() - startNanos
            }
            """,
        ).expectWarningCount(1)
    }

    /** Object-carried epochs (Date#getTime) are flagged with the generic "matching domain" hint. */
    @Test
    fun flagsDateGetTimeSubtraction() {
        lintKotlin(
            """
            package test
            import java.util.Date
            fun age(fix: Date): Long {
                return System.currentTimeMillis() - fix.getTime()
            }
            """,
        ).expectWarningCount(1)
    }

    /** java.time epoch extraction in arithmetic is flagged. */
    @Test
    fun flagsInstantToEpochMilliSubtraction() {
        lintKotlin(
            """
            package test
            import java.time.Instant
            fun span(start: Instant, end: Instant): Long {
                return end.toEpochMilli() - start.toEpochMilli()
            }
            """,
        ).expectWarningCount(1)
    }

    /** Arithmetic on already-typed / plain Longs carries no clock-source call, so it's not flagged. */
    @Test
    fun doesNotFlagPlainLongArithmetic() {
        lintKotlin(
            """
            package test
            fun delta(a: Long, b: Long): Long {
                return a - b
            }
            """,
        ).expectClean()
    }
}
