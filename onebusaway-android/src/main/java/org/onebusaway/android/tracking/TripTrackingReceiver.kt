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
package org.onebusaway.android.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the tracking notification's "stop tracking" action and its swipe-away.
 *
 * The write goes to [TrackedRouteStore], not to [TripTrackingService]: the store is the source of
 * truth, so the tap is honoured even if the service is between restarts, and the running service
 * picks it up on its next emission of the tracked list — which is immediate, since it collects the
 * store rather than polling it.
 */
@AndroidEntryPoint
class TripTrackingReceiver : BroadcastReceiver() {

    @Inject lateinit var store: TrackedRouteStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNTRACK) return
        val rowId = intent.getStringExtra(EXTRA_ROW_ID) ?: return
        store.untrackById(rowId)
    }

    companion object {
        const val ACTION_UNTRACK = "org.onebusaway.android.tracking.UNTRACK"
        const val EXTRA_ROW_ID = "org.onebusaway.android.tracking.ROW_ID"
    }
}
