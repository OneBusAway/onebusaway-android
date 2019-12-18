/*
 * Copyright (C) 2018 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.nav.model

/**
 * A model class that holds path information for a user's journey from an origin to a destination,
 * which can include multiple transfers between routes (i.e., GTFS "trips").
 * A [Path] consists of one or more [PathLink].
 */
data class Path

(val pathLinks: ArrayList<PathLink>)
