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

import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers [utf8JsonMediaTypeFor], the whole of [JsonCharsetInterceptor]'s decision. */
class JsonCharsetInterceptorTest {

    /**
     * The case that motivated all this: the OBA "where" API's own header, verbatim. Its bytes are
     * UTF-8, so believing this is what turned "What’s happening in Seattle today" into mojibake.
     */
    @Test
    fun correctsTheObaApisIso88591Claim() {
        val corrected = utf8JsonMediaTypeFor("application/json;charset=ISO-8859-1".toMediaType())

        assertEquals(Charsets.UTF_8, corrected?.charset())
        assertEquals("application", corrected?.type)
        assertEquals("json", corrected?.subtype)
    }

    /** The `+json` structured-suffix family is JSON too, and equally bound to UTF-8. */
    @Test
    fun correctsAStructuredJsonSuffixType() {
        assertEquals(
            Charsets.UTF_8,
            utf8JsonMediaTypeFor("application/problem+json; charset=windows-1252".toMediaType())?.charset()
        )
    }

    /**
     * The common shape on every other host this app talks to. OkHttp already reads a charset-less body
     * as UTF-8, so rewriting it would be noise — the response must be passed through untouched.
     */
    @Test
    fun leavesAResponseThatStatesNoCharsetAlone() {
        assertNull(utf8JsonMediaTypeFor("application/json".toMediaType()))
    }

    @Test
    fun leavesAResponseAlreadyStatingUtf8Alone() {
        assertNull(utf8JsonMediaTypeFor("application/json; charset=utf-8".toMediaType()))
        assertNull(utf8JsonMediaTypeFor("application/json; charset=UTF-8".toMediaType()))
    }

    /**
     * The guard that keeps this from being a blanket re-encoding: only JSON is bound to UTF-8 by its
     * format, so a server's charset on anything else is the server's to state — an error page really
     * may be Latin-1.
     */
    @Test
    fun leavesNonJsonMediaTypesAlone() {
        assertNull(utf8JsonMediaTypeFor("text/html; charset=ISO-8859-1".toMediaType()))
        assertNull(utf8JsonMediaTypeFor("application/octet-stream".toMediaType()))
        // Not a false match on the `+json` suffix rule.
        assertNull(utf8JsonMediaTypeFor("application/jsonish; charset=ISO-8859-1".toMediaType()))
    }

    /** A body with no Content-Type at all: nothing is claimed, so there is nothing to correct. */
    @Test
    fun leavesAnAbsentContentTypeAlone() {
        assertNull(utf8JsonMediaTypeFor(null))
    }

    /**
     * The corruption this prevents, spelled out on the exact bytes the live API sends — so the test
     * names the failure rather than only the header rewrite that avoids it.
     */
    @Test
    fun theWireBytesAreUtf8AndAreMojibakeWhenReadAsLatin1() {
        val wire = byteArrayOf(0x57, 0x68, 0x61, 0x74, 0xE2.toByte(), 0x80.toByte(), 0x99.toByte(), 0x73)

        assertEquals("What’s", String(wire, Charsets.UTF_8))
        assertEquals("Whatâs", String(wire, Charsets.ISO_8859_1))
    }
}
