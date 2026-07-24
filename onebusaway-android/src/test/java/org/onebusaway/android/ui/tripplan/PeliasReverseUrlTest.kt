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
package org.onebusaway.android.ui.tripplan

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the Pelias `/reverse` query the geocode repository builds by hand — the vendored client
 * library models only the text-query endpoints, whose builders hard-code `?text=`, so this query shape
 * has no library to keep it honest.
 */
class PeliasReverseUrlTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `builds the reverse query for a point`() {
        assertEquals(
            "https://api.geocode.earth/v1/reverse?point.lat=47.6&point.lon=-122.3&api_key=abc123&size=1&layers=address,venue,street",
            peliasReverseUrl("https://api.geocode.earth/v1/reverse", "abc123", 47.6, -122.3)
        )
    }

    @Test
    fun `coordinates keep a dot decimal separator in a comma-decimal locale`() {
        // A locale-formatted "47,6" would silently split the query parameter.
        Locale.setDefault(Locale.GERMANY)
        assertEquals(
            "https://pelias.example/v1/reverse?point.lat=47.6&point.lon=-122.3&api_key=k&size=1&layers=address,venue,street",
            peliasReverseUrl("https://pelias.example/v1/reverse", "k", 47.6, -122.3)
        )
    }
}
