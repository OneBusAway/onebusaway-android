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
 * generation (#2168). The small dot and the four unlabelled big pins (bikes/scooters × vehicle/dock)
 * are built once and reused; a labelled pin is built per distinct label and cached by [RentalBitmaps]
 * itself, since its text is per-vehicle.
 */
class RentalIcons(private val context: Context) {

    val small: BitmapDescriptor = BitmapDescriptorFactory.fromBitmap(RentalBitmaps.small(context))

    private val big = HashMap<Pair<RentalLayer, RentalKind>, BitmapDescriptor>()

    fun big(layer: RentalLayer, kind: RentalKind): BitmapDescriptor = big.getOrPut(layer to kind) {
        BitmapDescriptorFactory.fromBitmap(RentalBitmaps.big(context, layer, kind))
    }

    /**
     * A big pin with [label] beneath it, plus the vertical anchor that keeps the pin's tip on the
     * point (the label hangs below the bitmap's bottom edge otherwise).
     */
    fun labelled(layer: RentalLayer, kind: RentalKind, label: String): LabelledRentalIcon {
        val icon = RentalBitmaps.labelled(
            context,
            RentalBitmaps.big(context, layer, kind),
            label,
            cacheKey = "$layer/$kind/$label"
        )
        return LabelledRentalIcon(BitmapDescriptorFactory.fromBitmap(icon.bitmap), icon.anchorV)
    }
}

/** A rental icon and the vertical anchor fraction its pin tip sits at. */
data class LabelledRentalIcon(val descriptor: BitmapDescriptor, val anchorV: Float)
