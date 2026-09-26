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
package org.onebusaway.android.ui.home.directions

import org.onebusaway.android.ui.tripplan.TripPlanError

/** Spec §3.8: the "On-demand options" action appears for a no-route or schedule failure with two coordinates on a server not known unsupported. */
internal fun showsOnDemandOptions(category: TripPlanError.Category, fromHasCoordinates: Boolean, toHasCoordinates: Boolean, onDemandSupported: Boolean): Boolean = onDemandSupported &&
    (category == TripPlanError.Category.NO_ROUTE || category == TripPlanError.Category.SCHEDULE) &&
    fromHasCoordinates &&
    toHasCoordinates
