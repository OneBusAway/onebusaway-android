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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * The elimination door of the typed-time region. After the type-first migration the compiler owns
 * everything *inside* the region (a cross-domain subtraction won't compile); this check guards the
 * exit — `ServerTime.epochMs` / `WallTime.epochMs` / `ElapsedTime.ms` — so an instant is unwrapped to a
 * bare `Long` only at a genuine platform sink, not back into app logic where the domain is lost again.
 *
 * The mirror image of `UnwrappedClockValue`: that check catches a raw reading that never got minted at
 * the *introduction* door; this catches a typed instant thrown back to a bare `Long` at the *elimination*
 * door. It fires when a domain-instant accessor read either
 * - feeds arithmetic / a comparison ([TimeLintSupport.FLAGGED_OPERATORS]), or
 * - comes to rest in a bare `Long` / `Int` slot (a local, field, return, or default parameter),
 *
 * and stays silent when the read is passed straight through as an argument to a consuming call — a
 * platform sink (formatting, `toPixelY`, an alarm) is exactly where unwrapping is correct.
 *
 * A Kotlin value-class property read (`serverTime.epochMs`) is a `UQualifiedReferenceExpression`, not a
 * getter call, so the method-name dispatch used by `RawTimeDetector` doesn't fire; and the receiver's
 * UAST *type* is the value class's inlined `long`, so type-keying fails too. Detection resolves the
 * property read to its light accessor method and keys on that method's `owner#getter`.
 *
 * Deliberately scoped to the **instant** types only. `ScheduleTime`'s accessor returns a
 * `kotlin.time.Duration` (already typed); schedule time only reaches a bare `Long` through `Duration`'s
 * own eliminators (`inWholeMilliseconds`, …), a domain-free stdlib quantity whose only hazard is units —
 * a Kotlin-ecosystem-wide concern, not this app's clock-domain discipline. Enrolling stdlib eliminators
 * would be noise far beyond that discipline; see lint-rules/README.md.
 */
class PrematureUnwrapDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UBinaryExpression::class.java, UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        // The domain package is where the instant types *define* their own algebra, which necessarily
        // touches the backing field (`minus` is `(epochMs - other.epochMs).milliseconds`). Unwrapping
        // to define the typed API is not "premature unwrap into app logic" — the whole region the check
        // guards is downstream of these definitions — so the check does not apply to the domain's own
        // source. (The introduction door, RawTimeDetector, still guards mints made here.)
        if (context.uastFile?.packageName == TIME_DOMAIN_PACKAGE) return UElementHandler.NONE
        return object : UElementHandler() {
            // Arithmetic / comparison with an unwrapped-instant operand.
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator !in TimeLintSupport.FLAGGED_OPERATORS) return
                val domain = unwrapDomainOf(node.leftOperand)
                    ?: unwrapDomainOf(node.rightOperand)
                    ?: return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Arithmetic on an unwrapped `$domain` instant — keep it typed and subtract instants " +
                        "(which yields a `kotlin.time.Duration`) or shift by a `Duration`, instead of " +
                        "doing the math on a bare `Long`.",
                )
            }

            // A domain-instant accessor read coming to rest in a bare Long/Int slot.
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val domain = unwrapDomainOf(node) ?: return
                val slot = TimeLintSupport.restingSlot(node) ?: return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "A `$domain` instant is unwrapped to a bare `Long` in ${slot.phrase} — pass the " +
                        "typed instant instead, and unwrap (`.epochMs` / `.ms`) only at a platform sink " +
                        "(formatting, a pixel coordinate, an alarm).",
                )
            }
        }
    }

    /**
     * The instant domain [expr] unwraps (an `x.epochMs` / `x.ms` accessor read on an instant type,
     * looking through parentheses), or null if it isn't such an unwrap.
     */
    private fun unwrapDomainOf(expr: UExpression?): String? =
        (expr?.skipParenthesizedExprDown() as? UQualifiedReferenceExpression)
            ?.let { DOMAIN_ESCAPES[TimeLintSupport.propertyKey(it, ACCESSOR_NAMES)] }

    companion object {
        /** The package that defines the instant types; exempt because it implements their own algebra. */
        private const val TIME_DOMAIN_PACKAGE = "org.onebusaway.android.time"

        /** Instant accessor `owner#property` -> the domain name (for the message). */
        private val DOMAIN_ESCAPES: Map<String, String> = mapOf(
            "org.onebusaway.android.time.ServerTime#epochMs" to "ServerTime",
            "org.onebusaway.android.time.WallTime#epochMs" to "WallTime",
            "org.onebusaway.android.time.ElapsedTime#ms" to "ElapsedTime",
        )

        /** The accessor simple-names (`epochMs`, `ms`) — the cheap gate before `propertyKey`'s resolve. */
        private val ACCESSOR_NAMES: Set<String> =
            DOMAIN_ESCAPES.keys.mapTo(mutableSetOf()) { it.substringAfterLast('#') }

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PrematureUnwrap",
            briefDescription = "Domain-time instant unwrapped away from a platform sink",
            explanation = """
                A domain-time instant (`ServerTime` / `WallTime` / `ElapsedTime` from \
                `org.onebusaway.android.time`) is being unwrapped to a bare `Long` — via `.epochMs` / \
                `.ms` — either into arithmetic/comparison or to rest in a `Long` slot, rather than being \
                kept typed. This is the elimination door of the typed-time region: the value classes make \
                a cross-domain mix a compile error *inside* the region, but an early unwrap drops the \
                value back to an undomained `Long` where the #27 / #1612 mix can happen again.

                Keep the instant typed: subtract two instants (same-domain, yields a \
                `kotlin.time.Duration`), or shift one by a `Duration` (`instant + 30.seconds`). Unwrap \
                with `.epochMs` / `.ms` only when handing the value to a platform sink that needs a raw \
                `Long` — formatting, a pixel coordinate, an alarm — where it is passed straight through \
                as a call argument (that case is deliberately not flagged).

                Note this guards instants only. `ScheduleTime` already carries a typed \
                `kotlin.time.Duration`; its unit-level hazards are a stdlib concern, not this app's \
                clock-domain discipline. Suppress this id only with a one-line rationale and a tracking \
                issue (see CLAUDE.md); the check is a latch, not a suggestion.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PrematureUnwrapDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
