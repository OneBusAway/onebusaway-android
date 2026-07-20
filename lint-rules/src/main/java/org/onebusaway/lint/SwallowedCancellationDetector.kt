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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/**
 * Gives the app a phobia of bare `runCatching` in coroutine code. `kotlin.runCatching` catches every
 * `Throwable`, so inside a `suspend` function it also swallows the `CancellationException` a cancelled
 * coroutine throws from a suspend call — turning cancellation into an ordinary `Result.failure`. The
 * cancelled coroutine then keeps running and its callers read the cancellation as a normal error/empty
 * state; structured concurrency breaks. That is the #1908/#1921 bug class, and it's invisible on the
 * happy path and in review.
 *
 * `CancellationException` is a plain `IllegalStateException` subtype, so there is no type-level guard —
 * the compiler can't tell a swallow from a legitimate catch. So this detector keys on the shape: a
 * `runCatching` whose **nearest enclosing named function is `suspend`** (the coroutine boundary lint can
 * see deterministically). The sanctioned replacement is `runCatchingCancellable` (in
 * `org.onebusaway.android.util`), which is behaviour-identical but rethrows `CancellationException`.
 *
 * Scope is the enclosing `suspend` function, matching detekt's `SuspendFunSwallowedCancellation`. A
 * `runCatching` in a coroutine-builder lambda inside a *non-suspend* function (e.g. `scope.launch { … }`)
 * is deliberately out of scope — the enclosing named function isn't `suspend`, so lint can't see the
 * boundary without heuristics; guard those by hand.
 */
class SwallowedCancellationDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("runCatching")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Both the receiverless `runCatching` and the `T.runCatching` extension live in kotlin.ResultKt;
        // confirm the owner so a same-named function elsewhere doesn't match.
        if (method.containingClass?.qualifiedName != KOTLIN_RESULT_KT) return
        // Only inside a suspend function — that's where a CancellationException can be thrown and where
        // swallowing it breaks cancellation. Lambdas are skipped, so `async { runCatching { … } }` in a
        // suspend function is still attributed to that suspend function.
        val enclosing = node.getParentOfType<UMethod>() ?: return
        if (!enclosing.isSuspendFunction()) return
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "`runCatching` in a `suspend` function also catches `CancellationException`, converting a " +
                "cancelled coroutine into a `Result.failure` (the cancellation stops propagating and the " +
                "work keeps running). Use `runCatchingCancellable` " +
                "(org.onebusaway.android.util), which rethrows cancellation and is otherwise identical."
        )
    }

    /**
     * True when this function carries the `suspend` modifier. A Kotlin `suspend fun` compiles with a
     * trailing `kotlin.coroutines.Continuation` parameter on its light (`javaPsi`) method, which is how
     * lint sees suspension without depending on Kotlin compiler PSI.
     */
    private fun UMethod.isSuspendFunction(): Boolean = javaPsi.parameterList.parameters.lastOrNull()
        ?.type?.canonicalText?.startsWith(CONTINUATION_FQN) == true

    companion object {
        private const val KOTLIN_RESULT_KT = "kotlin.ResultKt"
        private const val CONTINUATION_FQN = "kotlin.coroutines.Continuation"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SwallowedCancellation",
            briefDescription = "runCatching in a suspend function swallows CancellationException",
            explanation = """
                `kotlin.runCatching` catches every `Throwable`. Inside a `suspend` function that includes \
                the `CancellationException` a cancelled coroutine throws from a suspend call, so the \
                cancellation is captured into the returned `Result` instead of propagating. The cancelled \
                coroutine keeps running, structured concurrency breaks, and callers see the cancellation \
                as an ordinary failure/empty result. `CancellationException` is a plain \
                `IllegalStateException` subtype, so nothing at the type level catches this — it passes \
                compilation and review, and only misbehaves under real cancellation (screen closed, \
                search superseded, poll tick outrun). This is the #1908 / #1921 bug class.

                Replace it with `runCatchingCancellable` from `org.onebusaway.android.util`: it is \
                behaviour-identical to `runCatching` but rethrows `CancellationException` (a real failure \
                still resolves to `Result.failure`). For a broad `try/catch (Exception)` in a suspend \
                function, add a leading `catch (e: CancellationException) { throw e }` instead — this \
                check covers `runCatching` only.

                If a `suspend` function genuinely must capture cancellation (extremely rare), suppress \
                this id at the site with a one-line rationale and a tracking issue.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SwallowedCancellationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
