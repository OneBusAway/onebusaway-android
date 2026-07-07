/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.realtime

import android.os.Bundle
import androidx.work.Data

/**
 * Round-trip helpers between the simplified `CHECK_TRIP_TIME` [Bundle] and WorkManager [Data].
 *
 * The check bundle is produced by [RealtimeChecker.getSimplifiedBundle], which puts only primitives,
 * strings, and string arrays (no Serializable/Parcelable) — exactly the value types [Data] supports —
 * so it survives the hop into [RealtimeCheckWorker] intact. Value types outside that set are dropped
 * (they don't occur in the simplified bundle); the conversion is generic over the keys present rather
 * than hard-coding the OTP keys, so it stays decoupled from the bundle's schema.
 */
// Bundle.get(key) is deprecated in favor of typed getters, but reading heterogeneous keys generically
// (without knowing each value's type up front) is exactly what it's for and has no typed alternative.
@Suppress("DEPRECATION")
internal fun Bundle.toWorkData(): Data {
    val builder = Data.Builder()
    for (key in keySet()) {
        when (val value = get(key)) {
            is Boolean -> builder.putBoolean(key, value)
            is Int -> builder.putInt(key, value)
            is Long -> builder.putLong(key, value)
            is Float -> builder.putFloat(key, value)
            is Double -> builder.putDouble(key, value)
            is String -> builder.putString(key, value)
            is Array<*> -> {
                val strings = value.filterIsInstance<String>()
                if (strings.size == value.size) builder.putStringArray(key, strings.toTypedArray())
            }
        }
    }
    return builder.build()
}

/** Reconstruct a [Bundle] from the [Data] produced by [toWorkData], preserving each key's type. */
internal fun Data.toBundle(): Bundle {
    val bundle = Bundle()
    for ((key, value) in keyValueMap) {
        when (value) {
            is Boolean -> bundle.putBoolean(key, value)
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is Float -> bundle.putFloat(key, value)
            is Double -> bundle.putDouble(key, value)
            is String -> bundle.putString(key, value)
            is Array<*> -> {
                val strings = value.filterIsInstance<String>()
                if (strings.size == value.size) bundle.putStringArray(key, strings.toTypedArray())
            }
        }
    }
    return bundle
}
