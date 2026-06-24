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
package org.onebusaway.android.ui.report.infrastructure

import edu.usf.cutr.open311client.models.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ServiceListMappingTest {

    private fun service(
        name: String,
        code: String? = null,
        group: String? = null,
        type: String? = null
    ) = Service().apply {
        setService_name(name)
        setService_code(code)
        setGroup(group)
        setType(type)
    }

    private fun items(vararg services: Service) =
        ServiceListMapper.toItems(services.toList(), "Choose a Problem", "Others", "Transit")

    /** A compact label for each row, for order assertions. */
    private fun ServiceListItem.label() = when (this) {
        is ServiceListItem.Hint -> "hint:$label"
        is ServiceListItem.Section -> "section:$title"
        is ServiceListItem.Category -> "item:$name"
    }

    @Test
    fun `an empty service list yields just the hint`() {
        assertEquals(listOf("hint:Choose a Problem"), items().map { it.label() })
    }

    @Test
    fun `transit section comes first, remaining sections are alphabetical`() {
        val rows = items(
            service("Pothole", "p1", "Streets", "static"),
            service("Graffiti", "g1", "Streets", "static"),
            service("Animal", "a1", "Animals", "static"),
            service("Stop Problem", code = null, group = "Transit", type = "stop"),
            service("Bus stop issue", "b1", "Transit", "dynamic_stop"),
            service("Loose service", "l1", group = null, type = "x")
        )

        assertEquals(
            listOf(
                "hint:Choose a Problem",
                "section:Transit",
                "item:Stop Problem",
                "item:Bus stop issue",
                "section:Animals",
                "item:Animal",
                "section:Others",
                "item:Loose service",
                "section:Streets",
                "item:Pothole",
                "item:Graffiti"
            ),
            rows.map { it.label() }
        )
    }

    @Test
    fun `a null group falls under the Others section`() {
        val rows = items(service("Mystery", "m1", group = null, type = "x"))

        assertEquals(
            listOf("hint:Choose a Problem", "section:Others", "item:Mystery"),
            rows.map { it.label() }
        )
    }

    @Test
    fun `category carries the service fields and the raw library service`() {
        val pothole = service("Pothole", "p1", "Streets", "static")

        val category = items(pothole).filterIsInstance<ServiceListItem.Category>().single()

        assertEquals("p1", category.code)
        assertEquals("Pothole", category.name)
        assertEquals("Streets", category.group)
        assertEquals("static", category.type)
        assertSame(pothole, category.raw)
    }
}
