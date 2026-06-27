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
package org.onebusaway.android.ui.home

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The Activity's queue of external launch intents (deep links, FCM, launcher shortcuts) awaiting the
 * NavHost. Backed by an UNLIMITED-buffered [Channel] so:
 *  - a [submit] made before the NavHost composes (the cold-launch intent staged in `onCreate`) isn't
 *    lost, and
 *  - rapid, distinct back-to-back intents are each delivered exactly once, in order, rather than one
 *    overwriting another before [items] is collected (#1582 — the bug a conflating latch reintroduces).
 *
 * Generic in [T] purely so the no-drop / in-order contract is unit-testable on the JVM without an Android
 * `Intent`; the Activity uses `LaunchIntentChannel<Intent>`. [items] is a cold [receiveAsFlow] with a
 * single intended collector: the NavHost's `LaunchIntentEffect`.
 */
class LaunchIntentChannel<T> {

    private val channel = Channel<T>(Channel.UNLIMITED)

    /**
     * Enqueue [item] for delivery once [items] is collected. The UNLIMITED buffer never rejects, so the
     * [Channel.trySend] result is moot — it is always accepted.
     */
    fun submit(item: T) {
        channel.trySend(item)
    }

    /** The queued items, in submission order, each delivered exactly once. Collected once, by the host. */
    val items: Flow<T> = channel.receiveAsFlow()
}
