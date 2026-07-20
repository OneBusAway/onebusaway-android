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

class SwallowedCancellationDetectorTest {

    private fun lintKotlin(vararg sources: String) = lint()
        .files(*sources.map { kotlin(it).indented() }.toTypedArray())
        .issues(SwallowedCancellationDetector.ISSUE)
        .allowMissingSdk()
        .run()

    /** The motivating case: bare `runCatching` directly in a suspend function body. */
    @Test
    fun flagsRunCatchingInSuspendFunction() {
        lintKotlin(
            """
            package test
            suspend fun load(): Result<Int> = runCatching { fetch() }
            suspend fun fetch(): Int = 1
            """
        ).expectWarningCount(1)
    }

    /** Nested in an `async { … }` lambda, but the enclosing named function is still suspend — flagged. */
    @Test
    fun flagsRunCatchingInLambdaInsideSuspendFunction() {
        lintKotlin(
            """
            package test
            suspend fun load(): Result<Int> {
                lateinit var r: Result<Int>
                run { r = runCatching { fetch() } }
                return r
            }
            suspend fun fetch(): Int = 1
            """
        ).expectWarningCount(1)
    }

    /** The sanctioned wrapper must not be flagged (different symbol). */
    @Test
    fun doesNotFlagRunCatchingCancellable() {
        lintKotlin(
            """
            package test
            fun <T> runCatchingCancellable(block: () -> T): Result<T> = runCatching(block)
            suspend fun load(): Result<Int> = runCatchingCancellable { fetch() }
            suspend fun fetch(): Int = 1
            """
        ).expectClean()
    }

    /** `runCatching` in a plain (non-suspend) function is fine — no coroutine, no cancellation. */
    @Test
    fun doesNotFlagRunCatchingInNonSuspendFunction() {
        lintKotlin(
            """
            package test
            fun parse(s: String): Result<Int> = runCatching { s.toInt() }
            """
        ).expectClean()
    }

    /** The wrapper's own body: `runCatching` inside a non-suspend inline fun is not flagged. */
    @Test
    fun doesNotFlagInsideNonSuspendInlineWrapper() {
        lintKotlin(
            """
            package test
            inline fun <T> guard(block: () -> T): Result<T> =
                runCatching(block).onFailure { }
            """
        ).expectClean()
    }

    /** A same-named `runCatching` that is not kotlin.runCatching must not be flagged. */
    @Test
    fun doesNotFlagUnrelatedRunCatchingFunction() {
        lintKotlin(
            """
            package test
            class Helper { fun runCatching(block: () -> Int): Int = block() }
            suspend fun load(h: Helper): Int = h.runCatching { 1 }
            """
        ).expectClean()
    }
}
