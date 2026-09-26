package org.onebusaway.android.ondemand

import kotlin.math.pow
import org.onebusaway.android.models.OnDemandService

/** Spec §2.3: the fixed fallback palette for colour collisions, in order. */
val ONDEMAND_FALLBACK_PALETTE: List<Int> = listOf(0xFF3B82F6, 0xFFD97706, 0xFF7C3AED, 0xFFDB2777, 0xFF0891B2, 0xFF65A30D).map { it.toInt() }

/** The docked bar's colour when the probe point is outside every zone (spec §3.4). */
const val ONDEMAND_OUTSIDE_GRAY: Int = 0xFF636366.toInt()

private const val OPAQUE = 0xFF000000.toInt()
private const val MIN_CONTRAST = 4.5

/**
 * One opaque colour per service id: the route colour, else [brand]; when two services in the same
 * list resolve to the same colour, the second and later ones (by service id order, so the answer is
 * stable across refreshes) take the first palette colours no earlier service already has.
 */
fun resolveServiceColors(services: List<OnDemandService>, brand: Int): Map<String, Int> {
    val taken = mutableSetOf<Int>()
    val resolved = mutableMapOf<String, Int>()
    for (service in services.distinctBy { it.id }.sortedBy { it.id }) {
        val wanted = (service.routeColor ?: brand) or OPAQUE
        val colour = if (wanted in taken) ONDEMAND_FALLBACK_PALETTE.firstOrNull { it !in taken } ?: wanted else wanted
        taken += colour
        resolved[service.id] = colour
    }
    return resolved
}

/**
 * Spec §5: the route's own text colour when it has one; otherwise white or black, whichever reaches
 * a 4.5:1 contrast ratio against [background] (white when both do, so the outside gray reads white).
 */
fun readableTextColor(background: Int, preferred: Int?): Int {
    if (preferred != null) return preferred or OPAQUE
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    return if (contrastRatio(white, background) >= MIN_CONTRAST) {
        white
    } else if (contrastRatio(black, background) >= MIN_CONTRAST) {
        black
    } else {
        white
    }
}

private fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a) + 0.05
    val lb = relativeLuminance(b) + 0.05
    return maxOf(la, lb) / minOf(la, lb)
}

private fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
