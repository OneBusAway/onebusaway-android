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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UPostfixExpression

/**
 * Forbids the forced non-null operator `!!` in **production** Kotlin.
 *
 * `!!` is an assertion with no message and no recovery: when the value is null the user gets a bare
 * `NullPointerException` from a stack frame that says nothing about which invariant broke. Every `!!`
 * is a claim the author could not express in the type system, left unexplained for the next reader,
 * and paid for by whoever hits it in production. The two honest alternatives are to *handle* the null
 * (elvis, early return, a fallback) or to *assert it with a reason* — `requireNotNull(x) { "why" }` /
 * `checkNotNull(x) { "why" }` — so a crash report names the broken invariant.
 *
 * This is deliberately **not** a rename campaign. A bare `requireNotNull(x)` with no message throws a
 * different exception type from the same line and buys nothing; the check exists so each site gets a
 * decision. Most of them collapse into a shared helper — `GoogleMap.addMarker` returning `Marker?`
 * only when the map is released, `ContextCompat.getDrawable` returning null only for a missing
 * resource id — where one well-named function replaces N unexplained assertions.
 *
 * ### Why production only
 * The rule is scoped to `src/main`, `src/google`, and `src/maplibre`; test sources may keep `!!`.
 * A test that dereferences an unexpected null is *already failing correctly* — the NPE fails the test
 * with a stack trace pointing at the line, which is exactly the desired outcome, and wrapping it adds
 * ceremony without adding information. The cost `!!` imposes — an unexplained crash reaching a user —
 * simply does not exist in a test. Enrolling test sources would have meant ~167 mechanical rewrites
 * with no behavioural benefit, which is churn, not enforcement.
 *
 * Lint reports against the *variant* source sets it analyses, so scoping is enforced here rather than
 * by build wiring: [isProductionSource] rejects any path under a known test source root.
 *
 * ### Suppression
 * There is no baseline. A genuinely unavoidable `!!` carries an inline `@Suppress("ForcedNonNull")`
 * with a one-line rationale at the site, matching the other checks in this module.
 */
class ForcedNonNullDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UPostfixExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitPostfixExpression(node: UPostfixExpression) {
            if (node.operator.text != FORCED_NON_NULL) return
            if (!isProductionSource(context)) return
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "`!!` throws a bare `NullPointerException` with no indication of which invariant " +
                    "broke. Handle the null (elvis / early return / fallback), or assert it with a " +
                    "reason via `requireNotNull(x) { \"why\" }`. If several sites share one " +
                    "invariant, give them a named helper instead of repeating the assertion."
            )
        }
    }

    /**
     * True when the file under analysis is a production source. Keyed on the source-set directory
     * rather than the file name so a helper in `androidTest` that happens not to end in `Test` is
     * still exempt, and a production file that happens to contain "test" in a package segment is not.
     */
    private fun isProductionSource(context: JavaContext): Boolean {
        val path = context.file.invariantSeparatorsPath
        return TEST_SOURCE_ROOTS.none { path.contains(it) }
    }

    companion object {
        private const val FORCED_NON_NULL = "!!"

        private val TEST_SOURCE_ROOTS = listOf("/src/test/", "/src/androidTest/", "/src/testFixtures/")

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ForcedNonNull",
            briefDescription = "Forced non-null assertion (!!) in production code",
            explanation = """
                The forced non-null operator `!!` asserts an invariant the type system doesn't know \
                about, and does it without a message. When the assertion is wrong the user gets a bare \
                `NullPointerException`, and the crash report names a line rather than the invariant that \
                broke — so the report says where the code gave up, not what was actually missing.

                Prefer, in order:

                1. **Handle it.** An elvis (`?:`) with a real fallback, an early return, or a \
                `?.let { }` when the null case is genuinely expected.
                2. **Fix the type.** If the value is never null in practice, the nullability usually \
                belongs somewhere it can be removed — a non-null constructor parameter, a narrower \
                return type at the boundary that produced it.
                3. **Assert with a reason.** `requireNotNull(x) { "why" }` or `checkNotNull(x) { "why" }` \
                when the invariant is real but unexpressible. The message is the point: a bare \
                `requireNotNull(x)` is just `!!` with a different exception type.

                When several call sites assert the *same* invariant, extract a named helper rather than \
                repeating the assertion — one function that explains the invariant once beats N silent \
                operators.

                Test sources are exempt: a test that hits an unexpected null already fails with a stack \
                trace at the right line, which is the outcome you want there.

                If a `!!` is genuinely unavoidable, suppress this id inline with a one-line rationale.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ForcedNonNullDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
