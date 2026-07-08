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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.getUCallExpression
import org.jetbrains.uast.skipParenthesizedExprUp

/**
 * Gives the app a phobia of bare time `Long`s. A raw time reading — a call to a known time producer
 * (`System.currentTimeMillis()`, `SystemClock.elapsedRealtime()`, `Location.getTime()`,
 * `Instant.toEpochMilli()`, …) — carries no record of which clock it came from, so a server-vs-device
 * mix (the #27 / #1612 bug class: `serverTimestamp − deviceNow`) compiles and passes review. The domain
 * of an already-laundered `Long` is undecidable in lint — that undecidability is the whole reason
 * `TypedTime` exists — so this detector keys on the **boundary**: the producer calls that should be
 * minted into a domain type in `org.onebusaway.android.time` (`ServerTime` / `WallTime` / `ElapsedTime`)
 * right where they are read. It is the lint complement to those value classes: the types make typed code
 * safe by construction; this closes the gap where a producer reading never got minted.
 *
 * Two issues:
 * - [ISSUE] `RawClockArithmetic` — a producer reading used directly in arithmetic or a comparison.
 * - [ISSUE_VALUE] `UnwrappedClockValue` — a producer reading that comes to **rest in a bare `Long`/`Int`
 *   slot** (a local, a field/property, a return, a default parameter) instead of a domain type.
 *   Capturing the reading where the domain is lost subsumes tracing where the bare `Long` later flows:
 *   if a time can't rest untyped, downstream arithmetic on it can't happen. A producer passed straight
 *   through to a consuming API (a DAO write, an alarm, a domain-type mint) is not flagged — the value
 *   never rests, so no domain is lost.
 */
class RawTimeDetector : Detector(), SourceCodeScanner {

    // --- ISSUE: producer reading used in arithmetic / comparison (operand of a binary expression) ---

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UBinaryExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator !in FLAGGED_OPERATORS) return
                // Report against whichever operand is the raw clock read (left wins if both are).
                val key = producerKeyOf(node.leftOperand) ?: producerKeyOf(node.rightOperand) ?: return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Arithmetic on a raw clock reading — ${mintPhrase(CLOCK_SOURCES[key])} " +
                        "(org.onebusaway.android.time) at the boundary and do the math through the " +
                        "typed API, rather than on an unwrapped `Long`.",
                )
            }
        }

    // --- ISSUE_VALUE: producer reading coming to rest in a bare Long/Int slot ---

    override fun getApplicableMethodNames(): List<String> = PRODUCER_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val key = producerKey(method) ?: return
        val slot = restingSlot(node) ?: return
        context.report(
            ISSUE_VALUE,
            node,
            context.getLocation(node),
            "A raw time reading comes to rest in ${slot.phrase} as a bare `Long` — " +
                "${mintPhrase(CLOCK_SOURCES[key])} (org.onebusaway.android.time) at the read so the " +
                "clock domain travels with the value, instead of letting an untyped time escape into " +
                "the code.",
        )
    }

    /**
     * The kind of bare-`Long`/`Int` slot [call]'s result comes to rest in, or null when it doesn't rest
     * untyped — passed on to another call (a DAO write, an alarm, a domain mint), used in arithmetic
     * (that's [ISSUE]'s job), or bound to a non-time-shaped type.
     */
    private fun restingSlot(call: UCallExpression): Slot? =
        when (val parent = skipParenthesizedExprUp(call.getQualifiedParentOrThis().uastParent)) {
            is UParameter -> Slot.PARAMETER.takeIf { isBareTimeType(parent.type) }
            is ULocalVariable -> Slot.LOCAL.takeIf { isBareTimeType(parent.type) }
            is UField -> Slot.FIELD.takeIf { isBareTimeType(parent.type) }
            is UReturnExpression ->
                Slot.RETURN.takeIf { isBareTimeType(call.getParentOfType<UMethod>()?.returnType) }
            is UBinaryExpression -> Slot.ASSIGNMENT.takeIf {
                parent.operator == UastBinaryOperator.ASSIGN &&
                    isBareTimeType(parent.leftOperand.getExpressionType())
            }
            else -> null
        }

    /** The `CLOCK_SOURCES` key of [expr] if it (unwrapping receiver/parens) is a known producer call. */
    private fun producerKeyOf(expr: UExpression?): String? =
        producerKey(expr?.getUCallExpression()?.resolve())

    companion object {
        /** A bare `Long`/`Int` slot a raw time can come to rest in; [phrase] is its user-message noun. */
        private enum class Slot(val phrase: String) {
            LOCAL("a local variable"),
            FIELD("a field"),
            RETURN("a return value"),
            PARAMETER("a default parameter value"),
            ASSIGNMENT("an assignment"),
        }

        /** `owner#method` key into [CLOCK_SOURCES], or null if [method] isn't a known producer. */
        private fun producerKey(method: PsiMethod?): String? {
            method ?: return null
            return "${method.containingClass?.qualifiedName}#${method.name}"
                .takeIf { CLOCK_SOURCES.containsKey(it) }
        }

        private fun mintPhrase(domain: String?): String =
            if (domain != null) "mint it into `$domain`"
            else "mint it into the matching `ServerTime` / `WallTime` / `ElapsedTime`"

        /** A `Long`/`Int` (boxed or primitive) — a slot a raw time can hide in domainless. */
        private fun isBareTimeType(type: PsiType?): Boolean =
            type != null && type.canonicalText in BARE_TIME_TYPES

        private val BARE_TIME_TYPES =
            setOf("long", "int", "java.lang.Long", "java.lang.Integer")

        // Additive arithmetic + ordering comparison only — the shape of the bug class
        // (`serverTimestamp − deviceNow`, `now > deadline`). Multiply/divide/mod and equality/
        // compound-assign are intentionally out of scope (not clock-mixing operations); don't widen
        // this without re-checking the baseline, whose entries key on the emitted message.
        private val FLAGGED_OPERATORS = setOf(
            UastBinaryOperator.PLUS,
            UastBinaryOperator.MINUS,
            UastBinaryOperator.GREATER,
            UastBinaryOperator.LESS,
            UastBinaryOperator.GREATER_OR_EQUALS,
            UastBinaryOperator.LESS_OR_EQUALS,
        )

        // Known time producers (fully-qualified owner#method) -> the domain the reading belongs to,
        // or null when it's context-dependent (an epoch-from-object source could be any clock). The
        // goal is a phobia of bare time `Long`s: every raw reading here should be minted into a
        // domain type before it enters arithmetic. Membership in this map is what "is a time
        // producer" means; the value only tunes the suggested fix.
        private val CLOCK_SOURCES: Map<String, String?> = mapOf(
            // Device wall clock (epoch millis).
            "java.lang.System#currentTimeMillis" to "WallTime",
            // Monotonic / elapsed clocks.
            "java.lang.System#nanoTime" to "ElapsedTime",
            "android.os.SystemClock#elapsedRealtime" to "ElapsedTime",
            "android.os.SystemClock#elapsedRealtimeNanos" to "ElapsedTime",
            "android.os.SystemClock#uptimeMillis" to "ElapsedTime",
            "android.os.SystemClock#currentThreadTimeMillis" to "ElapsedTime",
            // Location fix timestamps: getTime() is a wall-clock epoch; the nanos variant is monotonic.
            "android.location.Location#getTime" to "WallTime",
            "android.location.Location#getElapsedRealtimeNanos" to "ElapsedTime",
            // Object-carried epochs — real clock domain depends on where the object came from.
            "java.util.Date#getTime" to null,
            "java.util.Calendar#getTimeInMillis" to null,
            "java.time.Instant#toEpochMilli" to null,
            "java.time.Instant#getEpochSecond" to null,
            "java.time.Clock#millis" to null,
            "java.time.ZonedDateTime#toEpochSecond" to null,
            "java.time.OffsetDateTime#toEpochSecond" to null,
            "java.time.LocalDateTime#toEpochSecond" to null,
        )

        /** Simple method names lint dispatches on; the owner is confirmed via [producerKey]. */
        private val PRODUCER_METHOD_NAMES: List<String> =
            CLOCK_SOURCES.keys.map { it.substringAfter('#') }.distinct()

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "RawClockArithmetic",
            briefDescription = "Arithmetic on an unwrapped clock reading",
            explanation = """
                A raw clock reading (`System.currentTimeMillis()`, `SystemClock.elapsedRealtime()`, …) \
                is being used directly in arithmetic or a comparison. A bare `Long` carries no record \
                of which clock it came from, so a server-vs-device mix — the #27 / #1612 bug class, \
                `serverTimestamp − deviceNow` — compiles and passes review, then leaks device clock \
                skew into a user-facing number.

                Mint the reading into a domain type at the boundary — `WallTime.now()`, \
                `ElapsedTime.now()`, or `ServerTime(...)` from `org.onebusaway.android.time` — and do \
                the arithmetic through the typed API (same-domain subtraction yields a \
                `kotlin.time.Duration`; a cross-domain subtraction then fails to compile).

                If this genuinely is a sanctioned device-clock local timer (a cache TTL, a poll \
                cadence, "updated N s ago" against a locally-stamped write), wrap it in \
                `WallTime` / `ElapsedTime` anyway so the domain is explicit, or suppress this id with a \
                one-line rationale. See CLAUDE.md "Time domains" and `TypedTime.kt`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                RawTimeDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        @JvmField
        val ISSUE_VALUE: Issue = Issue.create(
            id = "UnwrappedClockValue",
            briefDescription = "Raw time reading stored in a bare Long",
            explanation = """
                A raw time reading (`System.currentTimeMillis()`, `SystemClock.elapsedRealtime()`, \
                `Location.getTime()`, `Instant.toEpochMilli()`, …) is coming to rest in a bare `Long` / \
                `Int` slot — a local, a field/property, a return value, or a default parameter — instead \
                of a domain type. A bare `Long` carries no record of which clock it came from, so once a \
                time is allowed to rest untyped it can be mixed with another clock later (the #27 / #1612 \
                bug class) with nothing to catch it.

                Mint the reading at the read into `WallTime` / `ElapsedTime` / `ServerTime` from \
                `org.onebusaway.android.time`, so the clock domain travels with the value and same-domain \
                arithmetic stays a `kotlin.time.Duration` (a cross-domain mix then fails to compile). \
                Capturing the reading here is deliberately stricter than the arithmetic check \
                (`RawClockArithmetic`): if a time can't rest in a bare `Long`, there's no untyped time to \
                trace downstream.

                A reading passed straight through to a consuming API — a DAO/prefs write, an alarm, a \
                domain-type constructor — is not flagged: it never rests, so no domain is lost. For a \
                genuinely sanctioned boundary (e.g. the single injected `TimeProvider`), suppress this id \
                with a one-line rationale. See CLAUDE.md "Time domains" and `TypedTime.kt`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                RawTimeDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
