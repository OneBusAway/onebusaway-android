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
package org.onebusaway.android.ui.home.ondemand

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.PluralSpec
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.service

class PickerCopyTest {

    @Test
    fun `named areas are joined and unnamed ones fall back to a zone count`() {
        val named = service(areas = listOf(area(id = "a", name = "Boyne City"), area(id = "b", name = null), area(id = "c", name = "Petoskey")))
        assertEquals("Boyne City, Petoskey", pickerAreasText(named))
        val unnamed = service(areas = listOf(area(id = "a", name = null), area(id = "b", name = null)))
        assertEquals(PluralSpec(R.plurals.ondemand_detail_zone_count, 2, listOf(2)), pickerAreasText(unnamed))
    }
}
