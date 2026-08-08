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
package org.onebusaway.android.map.googlemapsv2.compose

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import java.util.IdentityHashMap
import org.onebusaway.android.map.render.RentalBitmaps
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalLayer

/**
 * The rental marker icons as Google [BitmapDescriptor]s, wrapping the shared [RentalBitmaps]
 * generation (#2168). The small dot is built once; the big badges are cached per (layer, kind,
 * charge) — `RentalBitmaps` quantizes the charge, so that stays bounded rather than growing with the
 * fleet.
 */
class RentalIcons(private val context: Context) {

    val small: BitmapDescriptor = BitmapDescriptorFactory.fromBitmap(RentalBitmaps.small(context))

    private val big = IdentityHashMap<Bitmap, BitmapDescriptor>()

    /**
     * The big badge for a place, with [chargeFraction] filling its ring.
     *
     * Keyed by the **bitmap** rather than by (layer, kind, charge): `RentalBitmaps` quantizes the
     * charge before caching, so a bucket's worth of distinct readings all return the same bitmap
     * instance — while a key built from the raw fraction would mint a separate `BitmapDescriptor` for
     * each of them, wrapping one bitmap over and over and growing this map without bound as vehicles
     * report new charge levels. Identity, not equality: the shared cache's instance is the key, so the
     * two caches cannot disagree about what counts as the same icon.
     */
    fun big(layer: RentalLayer, kind: RentalKind, chargeFraction: Float?): BitmapDescriptor {
        val bitmap = RentalBitmaps.big(context, layer, kind, chargeFraction)
        return big.getOrPut(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap) }
    }
}
