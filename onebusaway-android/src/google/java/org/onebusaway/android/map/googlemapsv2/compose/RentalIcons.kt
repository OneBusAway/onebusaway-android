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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import org.onebusaway.android.map.render.RentalBitmaps
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalLayer

/**
 * The rental marker icons as Google [BitmapDescriptor]s, wrapping the shared [RentalBitmaps]
 * generation (#2168). The small dot is built once; the big discs are cached per (layer, kind, charge)
 * and the labelled ones per (…, label) — `RentalBitmaps` quantizes the charge, so both stay bounded
 * rather than growing with the fleet.
 */
class RentalIcons(private val context: Context) {

    val small: BitmapDescriptor = BitmapDescriptorFactory.fromBitmap(RentalBitmaps.small(context))

    private val big = HashMap<String, LabelledRentalIcon>()

    /**
     * The big disc for a place, centred on its point ([LabelledRentalIcon.anchorV] is 0.5), with
     * [chargeFraction] filling its ring. Cached per (layer, kind, charge) — `RentalBitmaps` quantizes
     * the charge, so the distinct-key count stays bounded.
     */
    fun big(layer: RentalLayer, kind: RentalKind, chargeFraction: Float?): LabelledRentalIcon = big.getOrPut("$layer/$kind/$chargeFraction") {
        LabelledRentalIcon(
            BitmapDescriptorFactory.fromBitmap(RentalBitmaps.big(context, layer, kind, chargeFraction)),
            anchorV = 0.5f
        )
    }

    /**
     * A big disc with [label] beneath it, plus the vertical anchor that keeps the *disc* over the
     * point — the label hangs below it, so the anchor is no longer the bitmap's centre.
     */
    fun labelled(
        layer: RentalLayer,
        kind: RentalKind,
        chargeFraction: Float?,
        label: String
    ): LabelledRentalIcon {
        val icon = RentalBitmaps.labelled(
            context,
            RentalBitmaps.big(context, layer, kind, chargeFraction),
            label,
            cacheKey = "$layer/$kind/$chargeFraction/$label"
        )
        return LabelledRentalIcon(BitmapDescriptorFactory.fromBitmap(icon.bitmap), icon.anchorV)
    }
}

/** A rental icon and the vertical anchor fraction its point sits at. */
data class LabelledRentalIcon(val descriptor: BitmapDescriptor, val anchorV: Float)
