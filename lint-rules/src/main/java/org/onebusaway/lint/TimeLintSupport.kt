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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.skipParenthesizedExprUp

/**
 * Machinery shared by the time-domain detectors. Both doors of the typed-time region have the same
 * shape — a time-shaped value read at a boundary that must not flow into arithmetic or rest in a bare
 * `Long`. [RawTimeDetector] guards the introduction door (raw clock producers that never got minted);
 * [PrematureUnwrapDetector] guards the elimination door (typed instants unwrapped too early). Only the
 * source map (which method is a "boundary read") and the emitted message differ, so the operator set,
 * the bare-`Long` slot test, and the resting-slot classifier live here, once.
 */
internal object TimeLintSupport {

    /**
     * Additive arithmetic + ordering comparison only — the shape of the bug class
     * (`serverTimestamp − deviceNow`, `now > deadline`). Multiply/divide/mod and equality/compound-assign
     * are intentionally out of scope (not clock-mixing operations); don't widen this without re-checking
     * the baselines, whose entries key on the emitted message.
     */
    val FLAGGED_OPERATORS: Set<UastBinaryOperator> = setOf(
        UastBinaryOperator.PLUS,
        UastBinaryOperator.MINUS,
        UastBinaryOperator.GREATER,
        UastBinaryOperator.LESS,
        UastBinaryOperator.GREATER_OR_EQUALS,
        UastBinaryOperator.LESS_OR_EQUALS,
    )

    /** A bare `Long`/`Int` slot a time can come to rest in domainless; [phrase] is its user-message noun. */
    enum class Slot(val phrase: String) {
        LOCAL("a local variable"),
        FIELD("a field"),
        RETURN("a return value"),
        PARAMETER("a default parameter value"),
        ASSIGNMENT("an assignment"),
    }

    /**
     * `ownerFqn#propertyName` for a Kotlin property read whose name is in [ofInterest], else null. Both
     * time-domain doors key member reads on this. The [ofInterest] gate is tested against the (cheap)
     * selector identifier *before* the relatively expensive PSI `resolve()`, so — since the handler fires
     * on every `a.b` in the tree — `resolve()` runs only on the handful of candidate reads.
     *
     * UAST resolves a property read differently by class kind — a `@JvmInline value class` accessor to a
     * light getter *method*, a `data class` `val` to the light constructor *parameter* — and the value
     * class's receiver *type* is its inlined primitive, so neither the receiver type nor a single resolved
     * shape is reliable. So the property name is the selector identifier (always the source name) and the
     * owner is from whichever resolved shape carries a containing class.
     */
    fun propertyKey(ref: UQualifiedReferenceExpression, ofInterest: Set<String>): String? {
        val property = (ref.selector as? USimpleNameReferenceExpression)?.identifier ?: return null
        if (property !in ofInterest) return null
        val owner = ownerFqnOf(ref.resolve()) ?: return null
        return "$owner#$property"
    }

    private fun ownerFqnOf(resolved: PsiElement?): String? = when (resolved) {
        is PsiMember -> resolved.containingClass?.qualifiedName            // a getter method or backing field
        is PsiParameter -> (resolved.declarationScope as? PsiMethod)?.containingClass?.qualifiedName
        else -> null
    }

    /** A `Long`/`Int` (boxed or primitive) — a slot a time can hide in domainless. */
    private fun isBareTimeType(type: PsiType?): Boolean =
        type != null && type.canonicalText in BARE_TIME_TYPES

    private val BARE_TIME_TYPES =
        setOf("long", "int", "java.lang.Long", "java.lang.Integer")

    /**
     * The kind of bare-`Long`/`Int` slot [read]'s result comes to rest in, or null when it doesn't rest
     * untyped — passed on to another call (a DAO write, an alarm, a domain mint), used in arithmetic (a
     * separate trigger), or bound to a non-time-shaped type. [read] is the boundary read: a producer
     * call (`RawTimeDetector`) or a domain-instant accessor reference (`PrematureUnwrapDetector`).
     */
    fun restingSlot(read: UExpression): Slot? =
        when (val parent = skipParenthesizedExprUp(read.getQualifiedParentOrThis().uastParent)) {
            is UParameter -> Slot.PARAMETER.takeIf { isBareTimeType(parent.type) }
            is ULocalVariable -> Slot.LOCAL.takeIf { isBareTimeType(parent.type) }
            is UField -> Slot.FIELD.takeIf { isBareTimeType(parent.type) }
            is UReturnExpression ->
                Slot.RETURN.takeIf { isBareTimeType(read.getParentOfType<UMethod>()?.returnType) }
            is UBinaryExpression -> Slot.ASSIGNMENT.takeIf {
                parent.operator == UastBinaryOperator.ASSIGN &&
                    isBareTimeType(parent.leftOperand.getExpressionType())
            }
            else -> null
        }
}
