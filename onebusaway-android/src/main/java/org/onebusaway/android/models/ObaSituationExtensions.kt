/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.models

/**
 * A key over a situation's rider-visible content (summary, description, advice, reason, url,
 * severity). Two situations that present identically to the rider share a key even if their id,
 * creation time, or active windows differ — which is how republished-duplicate alerts are collapsed
 * (see #1593). Fields are joined with a NUL separator so adjacent values can't run together and
 * collide across field boundaries.
 */
val ObaSituation.contentKey: String
    get() = listOf(summary, description, advice, reason, url, severity)
        .joinToString("\u0000") { it.orEmpty() }
