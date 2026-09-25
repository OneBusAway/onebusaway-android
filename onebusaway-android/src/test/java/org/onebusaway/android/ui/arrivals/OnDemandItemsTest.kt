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
package org.onebusaway.android.ui.arrivals

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind

class OnDemandItemsTest {

    private fun service(id: String, phone: String?) = OnDemandService(
        id = id,
        agencyId = "5088",
        routeId = id,
        name = "Service $id",
        kind = OnDemandServiceKind.ZONE,
        rules = listOf(AvailabilityRule(emptyList(), emptyList(), null, null, null, emptyList(), 2, 2, "b", null, null, null)),
        bookingRules = mapOf("b" to BookingRule("b", BookingType.REAL_TIME, null, null, null, null, null, null, null, null, null, null, phone, null, null))
    )

    @Test
    fun `items keep pointer order and drop failures`() = runTest {
        val cache = mutableMapOf<String, OnDemandServiceItem>()
        val items = loadOnDemandItems(listOf("a", "b", "c"), cache) { id ->
            if (id == "b") OnDemandResult.Failed(IOException("slow")) else OnDemandResult.Loaded(service(id, "555"))
        }
        assertEquals(listOf("a", "c"), items.map { it.id })
        assertEquals("555", items[0].phoneNumber)
        assertEquals(setOf("a", "c"), cache.keys)
    }

    @Test
    fun `a cached item is not fetched again`() = runTest {
        val cache = mutableMapOf("a" to OnDemandServiceItem("a", "Cached", null))
        var fetches = 0
        val items = loadOnDemandItems(listOf("a"), cache) {
            fetches++
            OnDemandResult.Loaded(service("a", null))
        }
        assertEquals(0, fetches)
        assertEquals("Cached", items.single().name)
    }

    @Test
    fun `pointers are fetched concurrently and still come back in pointer order`() = runTest {
        val pending = mapOf("a" to CompletableDeferred<OnDemandResult<OnDemandService>>(), "b" to CompletableDeferred())
        val requested = mutableListOf<String>()
        val load = async {
            loadOnDemandItems(listOf("a", "b"), mutableMapOf()) { id ->
                requested += id
                pending.getValue(id).await()
            }
        }
        runCurrent()

        assertEquals(listOf("a", "b"), requested)
        assertFalse(load.isCompleted)

        // Complete out of order: the result must still follow the pointer order.
        pending.getValue("b").complete(OnDemandResult.Loaded(service("b", null)))
        pending.getValue("a").complete(OnDemandResult.Loaded(service("a", null)))
        assertEquals(listOf("a", "b"), load.await().map { it.id })
    }
}
