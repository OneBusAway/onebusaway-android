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
package org.onebusaway.android.extrapolation.math.prob

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

/**
 * Argument range the tables cover. [FirstPassageDistribution] queries at `x = elapsed / theta`:
 * elapsed runs to the 15-minute extrapolation horizon and [org.onebusaway.android.extrapolation.DeviationModel]
 * holds theta in 22..146s, so real queries land in `(0, 41]`. The bounds here leave six times that
 * headroom above and reach down to a couple of milliseconds elapsed below; anything outside is
 * solved directly rather than clamped, so the range is a performance boundary, not a domain limit.
 */
private const val X_MIN = 1e-4
private const val X_MAX = 256.0

/**
 * Nodes spanning [X_MIN]..[X_MAX], plus [PAD] off each end so the interpolation stencil is interior
 * everywhere in the covered range — otherwise the first and last cells extrapolate from a clamped
 * stencil, which is where the error would concentrate.
 *
 * `ln(shape)` against `ln(x)` is a gentle monotone curve — slope about a tenth at the low end,
 * rising to 1 — so cubic interpolation over 1024 log-spaced nodes holds the worst relative error
 * below 4e-7 over the whole range, and below 1e-8 at every quantile the app actually asks for. In
 * schedule time that is under a millisecond at the horizon, against the ~2ms the 20-step bisection
 * left. Each table costs 8KB.
 */
private const val NODE_COUNT = 1024
private const val PAD = 2
private const val TABLE_SIZE = NODE_COUNT + 2 * PAD
private val LN_X_STEP = (ln(X_MAX) - ln(X_MIN)) / (NODE_COUNT - 1)
private val LN_X_BASE = ln(X_MIN) - PAD * LN_X_STEP

/**
 * Bracket for the direct solve. `P(1e-12, x)` is 1 to within 1e-11 and `P(1e4, 256)` underflows to
 * zero, so this brackets every level; 60 halvings of the 37-nat span resolve the shape to ~1e-15
 * relative, well past what the incomplete gamma itself carries.
 */
private const val SHAPE_FLOOR = 1e-12
private const val SHAPE_CEILING = 1e4
private const val SOLVE_ITERATIONS = 60

/**
 * Levels held at once. Seven distinct quantiles are drawn (the marker's median, the band's edges,
 * the fast estimate, and the trajectory view's density window and separators), so the working set
 * fits with room to spare. [ProbDistribution.mean]'s quadrature would sweep past it, which is why
 * eviction is round-robin and entries fill lazily: churn costs a solve, not a table.
 */
private const val CACHE_SIZE = 16

/**
 * Inverts the regularized incomplete gamma in its **shape**: given a level and an argument `x`,
 * the shape `a` with `P(a, x) = level`.
 *
 * [FirstPassageDistribution] needs this because its CDF varies the gamma's shape while holding its
 * argument fixed — it asks how far along the schedule a vehicle has got, and schedule time enters
 * as the shape. `P` is strictly decreasing in the shape, from 1 as `a -> 0` to 0 as `a -> inf`, so
 * the inverse exists and is single-valued.
 *
 * The curve is worth tabulating because it depends on **nothing else**: not the dispersion, not the
 * travel multiplier, not the schedule. Those all fold into `x` and into scaling the answer back out,
 * leaving one two-argument function shared by every vehicle on the map. So a lookup with a cubic
 * interpolation replaces a root search that cost twenty incomplete-gamma evaluations per quantile,
 * per vehicle, per frame.
 *
 * Tables fill lazily, one node at a time, so nothing pays for a range it never queries and there is
 * no build-time hitch on the first frame. The whole entry point is synchronized: the fill is a
 * write to shared state, and an uncontended monitor costs a rounding error against the search this
 * removes.
 */
internal object IncompleteGammaShape {

    /** Cache keys. Initialized to NaN, which never equals a level, so an empty slot cannot hit. */
    private val levels = DoubleArray(CACHE_SIZE) { Double.NaN }

    /** `ln(shape)` per node, NaN where not yet solved. Parallel to [levels]. */
    private val tables = arrayOfNulls<DoubleArray>(CACHE_SIZE)

    /** Next slot to overwrite. */
    private var nextSlot = 0

    /**
     * The shape `a` with `P(a, [x]) = [level]`, for `x >= 0`.
     *
     * A [level] at or outside `(0, 1)` has no solution — `P` never reaches either end — and comes
     * back clamped to the bracket, which is the limit the caller wants: shape 0 for a certainty and
     * an effectively unbounded shape for an impossibility.
     */
    @Synchronized
    fun shapeFor(level: Double, x: Double): Double {
        // P(a, 0) is 0 whatever the shape, so no positive level is attained; the limiting shape is 0.
        if (x <= 0.0) return 0.0
        if (x < X_MIN || x > X_MAX) return solve(level, x)

        val table = tableFor(level)
        val u = (ln(x) - LN_X_BASE) / LN_X_STEP
        val i = floor(u).toInt().coerceIn(1, TABLE_SIZE - 3)
        return exp(
            catmullRom(
                node(table, level, i - 1),
                node(table, level, i),
                node(table, level, i + 1),
                node(table, level, i + 2),
                u - i
            )
        )
    }

    /** [level]'s table, evicting round-robin on a miss. */
    private fun tableFor(level: Double): DoubleArray {
        for (slot in 0 until CACHE_SIZE) {
            if (levels[slot] == level) return tables[slot]!!
        }
        val fresh = DoubleArray(TABLE_SIZE) { Double.NaN }
        levels[nextSlot] = level
        tables[nextSlot] = fresh
        nextSlot = (nextSlot + 1) % CACHE_SIZE
        return fresh
    }

    /** `ln(shape)` at node [i], solving and storing it the first time it is asked for. */
    private fun node(table: DoubleArray, level: Double, i: Int): Double {
        val cached = table[i]
        if (!cached.isNaN()) return cached
        val solved = ln(solve(level, exp(LN_X_BASE + i * LN_X_STEP)))
        table[i] = solved
        return solved
    }

    /**
     * Bisects `ln(shape)` for `P(shape, [x]) = [level]`. Geometric rather than linear because the
     * answer spans twelve orders of magnitude across the tabulated range, and only its relative
     * precision matters — the caller scales it by theta.
     *
     * This is the cold path: it runs once per table node, and directly only for arguments outside
     * the tabulated range.
     */
    private fun solve(level: Double, x: Double): Double {
        var lo = ln(SHAPE_FLOOR)
        var hi = ln(SHAPE_CEILING)
        repeat(SOLVE_ITERATIONS) {
            val mid = (lo + hi) / 2
            if (GammaDistribution.regularizedGammaP(exp(mid), x) > level) lo = mid else hi = mid
        }
        return exp((lo + hi) / 2)
    }

    /**
     * Catmull-Rom through four consecutive nodes, at fraction [f] of the way from [p1] to [p2].
     * Chosen over linear interpolation because it costs a handful of multiplies and buys a couple
     * of hundred times the accuracy on this curve at the same node count.
     */
    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, f: Double): Double = p1 + 0.5 * f * ((p2 - p0) + f * ((2 * p0 - 5 * p1 + 4 * p2 - p3) + f * (3 * (p1 - p2) + p3 - p0)))
}
