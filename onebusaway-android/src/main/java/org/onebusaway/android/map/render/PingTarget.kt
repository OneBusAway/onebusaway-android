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

import org.onebusaway.android.time.WallTime

/**
 * A renderer that can play a one-shot ping ripple on a vehicle (#1764). [startPing] arms a ripple for the
 * vehicle on trip [tripId]; [tickPing] advances it one frame (at the frame's wall-clock [now]), centering
 * it on that vehicle's live marker position, and returns false once the ripple is done (or the vehicle is
 * gone); [cancelPing] removes any in-flight ripple (so a superseded ping doesn't linger). Each flavor's map
 * renderer implements this so the flavor-neutral `drivePings` loop can drive the animation without knowing
 * the SDK draw primitive.
 */
interface PingTarget {
    fun startPing(tripId: String)
    fun tickPing(now: WallTime): Boolean
    fun cancelPing()
}
