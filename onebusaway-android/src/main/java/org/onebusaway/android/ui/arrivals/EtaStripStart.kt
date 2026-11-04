package org.onebusaway.android.ui.arrivals

/**
 * Index of the first item that is still upcoming, so an ETA strip can open on it
 * with departed pills sitting one swipe to the left.
 *
 * Returns 0 when nothing is upcoming (all departed, or empty) so the strip opens
 * at its first pill.
 */
internal fun <T> firstUpcomingIndex(items: List<T>, isUpcoming: (T) -> Boolean): Int = items.indexOfFirst(isUpcoming).coerceAtLeast(0)
