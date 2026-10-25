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
package org.onebusaway.android.api.net

import kotlin.time.Duration

/**
 * Whether the OBA server is rate-limiting this client right now, read from [ObaApiProvider]'s
 * [RateLimitGate] (#2410). For a screen that refreshes on a fixed period, so it can try again as soon
 * as a 429 cooldown ends rather than a whole period later.
 */
fun interface ObaRateLimit {
    /** How long until the current deployment may be asked again after a 429, or null when it may be asked now. */
    fun rateLimitedFor(): Duration?
}
