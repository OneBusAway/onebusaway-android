/*
 * Copyright 2012 University of South Florida
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
package org.onebusaway.android.directions.model

/**
 * @author Khoa Tran
 */
class Direction() {

    var icon = 0

    var directionIndex = 0

    var directionText: CharSequence? = null

    var service: CharSequence? = null

    var agency: CharSequence? = null

    var placeAndHeadsign: CharSequence? = null

    var extra: CharSequence? = null

    var oldTime: CharSequence? = null

    var newTime: CharSequence? = null

    var isTransit = false

    var subDirections: ArrayList<Direction>? = null

    var isRealTimeInfo = false

    constructor(
        icon: Int,
        service: CharSequence?,
        placeAndHeadsign: CharSequence?,
        oldTime: CharSequence?,
        newTime: CharSequence?,
        isTransit: Boolean,
    ) : this() {
        this.icon = icon
        this.service = service
        this.placeAndHeadsign = placeAndHeadsign
        this.oldTime = oldTime
        this.newTime = newTime
        this.isTransit = isTransit
    }

    constructor(icon: Int, directionText: CharSequence?) : this() {
        this.icon = icon
        this.directionText = directionText
    }
}
