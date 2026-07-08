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

class PrematureUnwrapDetectorTest {

    /** Minimal stand-ins for the domain instant types, so the detector resolves the accessor owner. */
    private val typedTime = kotlin(
        """
        package org.onebusaway.android.time
        @JvmInline value class ServerTime(val epochMs: Long)
        @JvmInline value class WallTime(val epochMs: Long)
        @JvmInline value class ElapsedTime(val ms: Long)
        """,
    ).indented()

    private fun lintKotlin(source: String) =
        lint()
            .files(typedTime, kotlin(source).indented())
            .issues(PrematureUnwrapDetector.ISSUE)
            .allowMissingSdk()
            .run()

    /** The value-class accessor read resolves to the getter — pins the UAST-over-source resolution. */
    @Test
    fun flagsArithmeticOnUnwrappedInstant() {
        lintKotlin(
            """
            package test
            import org.onebusaway.android.time.ServerTime
            fun ageMs(anchor: ServerTime, now: ServerTime): Long {
                return now.epochMs - anchor.epochMs
            }
            """,
        ).expectWarningCount(1)
    }

    /** Comparison on an unwrapped instant is flagged. */
    @Test
    fun flagsComparisonOnUnwrappedInstant() {
        lintKotlin(
            """
            package test
            import org.onebusaway.android.time.WallTime
            fun after(a: WallTime, b: WallTime): Boolean {
                return a.epochMs > b.epochMs
            }
            """,
        ).expectWarningCount(1)
    }

    /** An unwrap coming to rest in a bare `Long` local. */
    @Test
    fun flagsUnwrapRestingInLocal() {
        lintKotlin(
            """
            package test
            import org.onebusaway.android.time.ServerTime
            fun f(t: ServerTime): Long {
                val ms: Long = t.epochMs
                return ms
            }
            """,
        ).expectWarningCount(1)
    }

    /** ElapsedTime's accessor is `.ms`. */
    @Test
    fun flagsElapsedMsRestingInReturn() {
        lintKotlin(
            """
            package test
            import org.onebusaway.android.time.ElapsedTime
            fun raw(t: ElapsedTime): Long = t.ms
            """,
        ).expectWarningCount(1)
    }

    /** Passed straight through to a platform sink (a call argument) — the correct place to unwrap. */
    @Test
    fun doesNotFlagUnwrapPassedToSink() {
        lintKotlin(
            """
            package test
            import org.onebusaway.android.time.ServerTime
            fun format(ms: Long): String = ms.toString()
            fun label(t: ServerTime): String = format(t.epochMs)
            """,
        ).expectClean()
    }

    /** Typed same-domain subtraction carries no accessor read, so it's not flagged. */
    @Test
    fun doesNotFlagTypedInstantArithmetic() {
        lintKotlin(
            """
            package test
            import org.onebusaway.android.time.ServerTime
            operator fun ServerTime.minus(o: ServerTime): Long = 0
            fun d(a: ServerTime, b: ServerTime) = a - b
            """,
        ).expectClean()
    }
}
