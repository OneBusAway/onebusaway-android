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
package org.onebusaway.android.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry

// Both ends must opt out: an outgoing fade also keeps the incoming map below RESUMED until
// it finishes. Null preserves the NavHost's normal transition for every other destination pair.
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.arrivalsMapEnterTransition(): EnterTransition? = if (isArrivalsMapTransition()) EnterTransition.None else null

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.arrivalsMapExitTransition(): ExitTransition? = if (isArrivalsMapTransition()) ExitTransition.None else null

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isArrivalsMapTransition(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return (from == NavRoutes.ARRIVALS && to == NavRoutes.HOME) ||
        (from == NavRoutes.HOME && to == NavRoutes.ARRIVALS)
}
