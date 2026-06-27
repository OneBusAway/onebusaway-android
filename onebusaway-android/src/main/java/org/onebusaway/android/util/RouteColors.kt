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
@file:JvmName("RouteColors")

package org.onebusaway.android.util

import android.graphics.Color

/**
 * Parses an OBA route hex color (a bare hex string like "FDB71A") to an Android ARGB int, or null
 * when absent or malformed. The single canonical parse used by the wire DTO color readers
 * ([org.onebusaway.android.api.colorArgb]).
 */
fun parseObaHexColor(hex: String?): Int? =
    hex?.takeIf { it.isNotEmpty() }?.let {
        try {
            Color.parseColor("#${it.trim()}")
        } catch (e: IllegalArgumentException) {
            null
        }
    }
