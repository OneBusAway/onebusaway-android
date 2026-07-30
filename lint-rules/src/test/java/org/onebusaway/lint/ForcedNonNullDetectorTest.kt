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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class ForcedNonNullDetectorTest {

    private fun run(vararg files: TestFile) = lint()
        .files(*files)
        .issues(ForcedNonNullDetector.ISSUE)
        .allowMissingSdk()
        .run()

    private fun production(source: String) = kotlin("src/main/java/test/Prod.kt", source).indented()

    /** The motivating case: a bare `!!` on a nullable local in production code. */
    @Test
    fun flagsForcedNonNullInProduction() {
        run(
            production(
                """
                package test
                fun name(value: String?): Int = value!!.length
                """
            )
        ).expectWarningCount(1)
    }

    /** Each operator in a chain is its own unexplained assertion, so both are reported. */
    @Test
    fun flagsEachOperatorInAChain() {
        run(
            production(
                """
                package test
                class Inner(val name: String?)
                class Outer(val inner: Inner?)
                fun name(o: Outer): Int = o.inner!!.name!!.length
                """
            )
        ).expectWarningCount(2)
    }

    /** `!=` must not be mistaken for the operator — it is a different token entirely. */
    @Test
    fun doesNotFlagNotEquals() {
        run(
            production(
                """
                package test
                fun differs(a: String?, b: String?): Boolean = a != b
                """
            )
        ).expectClean()
    }

    /** Boolean negation is a *prefix* `!`; only the postfix `!!` is the forced unwrap. */
    @Test
    fun doesNotFlagBooleanNegation() {
        run(
            production(
                """
                package test
                fun negate(flag: Boolean): Boolean = !flag
                """
            )
        ).expectClean()
    }

    /** The sanctioned replacements are exactly what the message asks for — never flagged. */
    @Test
    fun doesNotFlagRequireNotNullOrElvis() {
        run(
            production(
                """
                package test
                fun asserted(value: String?): Int = requireNotNull(value) { "must be set" }.length
                fun handled(value: String?): Int = (value ?: "fallback").length
                """
            )
        ).expectClean()
    }

    /** Unit-test sources are exempt: an unexpected null there already fails the test correctly. */
    @Test
    fun doesNotFlagUnitTestSources() {
        run(
            kotlin(
                "src/test/java/test/SomeTest.kt",
                """
                package test
                fun name(value: String?): Int = value!!.length
                """
            ).indented()
        ).expectClean()
    }

    /** Instrumentation sources are exempt for the same reason. */
    @Test
    fun doesNotFlagAndroidTestSources() {
        run(
            kotlin(
                "src/androidTest/java/test/SomeTest.kt",
                """
                package test
                fun name(value: String?): Int = value!!.length
                """
            ).indented()
        ).expectClean()
    }

    /** A flavor source set is production and must be covered, not just `src/main`. */
    @Test
    fun flagsFlavorSourceSets() {
        run(
            kotlin(
                "src/google/java/test/Flavor.kt",
                """
                package test
                fun name(value: String?): Int = value!!.length
                """
            ).indented()
        ).expectWarningCount(1)
    }

    /** A production file whose package contains "test" is still production. */
    @Test
    fun flagsProductionFileWithTestInPackageName() {
        run(
            kotlin(
                "src/main/java/test/util/TestUtil.kt",
                """
                package test.util
                fun name(value: String?): Int = value!!.length
                """
            ).indented()
        ).expectWarningCount(1)
    }

    /** Inline suppression is the sanctioned escape hatch; there is no baseline. */
    @Test
    fun respectsInlineSuppression() {
        run(
            production(
                """
                package test
                @Suppress("ForcedNonNull")
                fun name(value: String?): Int = value!!.length
                """
            )
        ).expectClean()
    }
}
