/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.onebusaway.android.map.layout

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Flavor-neutral screen geometry used by the route-badge layout. */
data class ScreenPoint(val x: Float, val y: Float)

data class ScreenSize(val width: Float, val height: Float)

data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun inset(horizontal: Float, vertical: Float): ScreenRect? {
        val result = ScreenRect(left + horizontal, top + vertical, right - horizontal, bottom - vertical)
        return result.takeIf { it.width >= 0f && it.height >= 0f }
    }
}

/** One badge and all projected screen-space paths that can anchor it. Input order is layout priority. */
data class RouteBadgeLayoutInput(
    val routeId: String,
    val size: ScreenSize,
    val paths: List<List<ScreenPoint>>,
)

/** The chosen badge center. [overlaps] is true only when every visible candidate conflicted. */
data class RouteBadgePlacement(
    val routeId: String,
    val center: ScreenPoint,
    val overlaps: Boolean,
)

/**
 * Places route badges greedily in [viewport]. Each route's candidates are sampled from its visible
 * path and ranked by distance to the viewport edge. The first non-overlapping candidate wins; when
 * none exists, the candidate with the least overlap wins so every visible route still gets a badge.
 *
 * This deliberately solves only the small real-time viewport problem, not global cartographic label
 * optimization: the general network-map labeling problem is NP-hard, while a ranked finite candidate
 * set + rectangle conflicts is cheap, deterministic, and independently testable.
 */
fun layoutRouteBadges(
    viewport: ScreenRect,
    inputs: List<RouteBadgeLayoutInput>,
    edgeMarginPx: Float = 8f,
    badgeGapPx: Float = 4f,
    candidateSpacingPx: Float = 32f,
    maxCandidatesPerRoute: Int = 256,
): List<RouteBadgePlacement> {
    if (viewport.width <= 0f || viewport.height <= 0f) return emptyList()
    val occupied = mutableListOf<ScreenRect>()
    return buildList {
        for (input in inputs) {
            val centerBounds = viewport.inset(
                input.size.width / 2f + edgeMarginPx,
                input.size.height / 2f + edgeMarginPx,
            ) ?: continue
            val candidates = routeCandidates(
                input.paths,
                viewport,
                centerBounds,
                candidateSpacingPx.coerceAtLeast(1f),
                maxCandidatesPerRoute.coerceAtLeast(1),
            )
            if (candidates.isEmpty()) continue

            val ranked = candidates.sortedBy { distanceToEdge(it, centerBounds) }
            val rectangles = ranked.map { badgeRect(it, input.size, badgeGapPx) }
            var chosen = rectangles.indexOfFirst { candidate -> occupied.none(candidate::overlaps) }
            val forcedOverlap = chosen < 0
            if (forcedOverlap) {
                chosen = rectangles.indices.minWithOrNull(
                    compareBy<Int> { index ->
                        occupied.sumOf { other -> rectangles[index].overlapArea(other).toDouble() }
                    }.thenBy { index -> distanceToEdge(ranked[index], centerBounds) }
                ) ?: continue
            }
            occupied += rectangles[chosen]
            add(RouteBadgePlacement(input.routeId, ranked[chosen], forcedOverlap))
        }
    }
}

private fun routeCandidates(
    paths: List<List<ScreenPoint>>,
    viewport: ScreenRect,
    centerBounds: ScreenRect,
    spacing: Float,
    limit: Int,
): List<ScreenPoint> {
    val result = ArrayList<ScreenPoint>(min(limit, 64))
    fun addDistinct(point: ScreenPoint) {
        if (result.size >= limit) return
        if (result.none { hypot((it.x - point.x).toDouble(), (it.y - point.y).toDouble()) < 2.0 }) {
            result += point
        }
    }
    for (path in paths) {
        for (index in 0 until path.lastIndex) {
            val clipped = clipSegment(path[index], path[index + 1], viewport) ?: continue
            val dx = clipped.second.x - clipped.first.x
            val dy = clipped.second.y - clipped.first.y
            val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val pieces = max(1, ceil(length / spacing).toInt())
            for (piece in 0..pieces) {
                val fraction = piece.toFloat() / pieces
                addDistinct(
                    ScreenPoint(
                        (clipped.first.x + dx * fraction).coerceIn(centerBounds.left, centerBounds.right),
                        (clipped.first.y + dy * fraction).coerceIn(centerBounds.top, centerBounds.bottom),
                    )
                )
                if (result.size >= limit) return result
            }
        }
    }
    return result
}

/** Liang–Barsky clipping; returns the visible segment inside [bounds], including boundary points. */
private fun clipSegment(
    start: ScreenPoint,
    end: ScreenPoint,
    bounds: ScreenRect,
): Pair<ScreenPoint, ScreenPoint>? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    var entry = 0f
    var exit = 1f

    fun clip(p: Float, q: Float): Boolean {
        if (p == 0f) return q >= 0f
        val ratio = q / p
        if (p < 0f) {
            if (ratio > exit) return false
            entry = max(entry, ratio)
        } else {
            if (ratio < entry) return false
            exit = min(exit, ratio)
        }
        return true
    }

    if (!clip(-dx, start.x - bounds.left) ||
        !clip(dx, bounds.right - start.x) ||
        !clip(-dy, start.y - bounds.top) ||
        !clip(dy, bounds.bottom - start.y) ||
        entry > exit
    ) return null

    return ScreenPoint(start.x + entry * dx, start.y + entry * dy) to
        ScreenPoint(start.x + exit * dx, start.y + exit * dy)
}

private fun distanceToEdge(point: ScreenPoint, bounds: ScreenRect): Float = min(
    min(point.x - bounds.left, bounds.right - point.x),
    min(point.y - bounds.top, bounds.bottom - point.y),
)

private fun badgeRect(center: ScreenPoint, size: ScreenSize, gap: Float): ScreenRect {
    val halfWidth = size.width / 2f + gap
    val halfHeight = size.height / 2f + gap
    return ScreenRect(
        center.x - halfWidth,
        center.y - halfHeight,
        center.x + halfWidth,
        center.y + halfHeight,
    )
}

private fun ScreenRect.overlaps(other: ScreenRect): Boolean =
    left < other.right && right > other.left && top < other.bottom && bottom > other.top

private fun ScreenRect.overlapArea(other: ScreenRect): Float =
    max(0f, min(right, other.right) - max(left, other.left)) *
        max(0f, min(bottom, other.bottom) - max(top, other.top))
