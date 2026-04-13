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
package org.onebusaway.android.extrapolation.math.prob

/** A continuous probability distribution. */
interface ProbDistribution {
    val mean: Double
    fun median(): Double = quantile(0.5)
    fun pdf(x: Double): Double
    fun cdf(x: Double): Double
    fun quantile(p: Double): Double
}

private const val BISECT_REL_TOL = 1e-9
private const val BRACKET_MAX_ITER = 100
private const val REFINE_MAX_ITER = 200

/**
 * Finds x such that [f](x) = [target] by bisection, starting from [initialHi]. If [f]([initialHi])
 * < [target], doubles [initialHi] until it brackets.
 *
 * Guards against degenerate inputs: if [initialHi] is non-positive, NaN, or infinite, falls back to
 * 1.0. Both loops are iteration-capped to prevent hangs from pathological CDFs.
 *
 * Returns [Double.NaN] if the bracket loop fails to converge within its iteration cap — this
 * happens when the CDF is truly pathological (constant-below-target, stuck-under-threshold,
 * NaN-returning for all sampled points) and bisect has no meaningful answer to report. Callers must
 * check the result for finiteness before using it.
 *
 * The refine loop has its own iteration cap, but when it fires the loop has been halving `hi`
 * toward zero (because every sampled `mid` was `>= target`), which is the signature of an extremely
 * concentrated distribution whose median has underflowed below representable precision. In that
 * case the midpoint of the still-tiny bracket is a reasonable best-effort answer, so we return it
 * rather than NaN. Degrading to NaN here would break callers that legitimately expect a finite (if
 * astronomically small) median for highly-concentrated distributions.
 */
internal fun bisect(f: (Double) -> Double, target: Double, initialHi: Double): Double {
    var hi = if (initialHi > 0 && initialHi.isFinite()) initialHi else 1.0
    var iter = 0
    while (f(hi) < target) {
        hi *= 2
        if (++iter >= BRACKET_MAX_ITER || !hi.isFinite()) return Double.NaN
    }

    var lo = 0.0
    iter = 0
    while (hi - lo > BISECT_REL_TOL * (hi + lo) && ++iter < REFINE_MAX_ITER) {
        val mid = (lo + hi) / 2
        if (f(mid) < target) lo = mid else hi = mid
    }
    return (lo + hi) / 2
}
