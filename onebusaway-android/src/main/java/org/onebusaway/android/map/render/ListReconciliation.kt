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
package org.onebusaway.android.map.render

/** Index plan for retaining equal native render objects while reconciling an ordered model list. */
data class ListReconciliation(
    val previousIndexForNext: List<Int?>,
    val removedPreviousIndices: Set<Int>,
)

/**
 * Match each [next] item to one equal, not-yet-used [previous] item. Duplicate values are handled as
 * a multiset; unmatched previous indices are removed and unmatched next indices are newly rendered.
 */
fun <T> reconcileEqualItems(previous: List<T>, next: List<T>): ListReconciliation {
    val available = previous.indices.toMutableList()
    val retained = next.map { item ->
        val availableIndex = available.indexOfFirst { previous[it] == item }
        if (availableIndex < 0) null else available.removeAt(availableIndex)
    }
    return ListReconciliation(retained, available.toSet())
}
