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
package org.onebusaway.android.ui.report.open311

import edu.usf.cutr.open311client.constants.Open311DataType
import edu.usf.cutr.open311client.models.Open311Attribute
import edu.usf.cutr.open311client.models.ServiceDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the [Open311FormMapper] both ways: ServiceDescription -> form fields, and the user's
 * answers -> Open311 attribute pairs. The Open311 library types are plain JARs, so this runs on a
 * bare JVM with no Android.
 */
class Open311FormMappingTest {

    private fun attribute(
        code: Int,
        datatype: String,
        description: String = "Question $code",
        variable: Boolean = true,
        required: Boolean = false,
        options: List<Pair<String, String>> = emptyList()
    ) = Open311Attribute().apply {
        setCode(code)
        setDatatype(datatype)
        setDescription(description)
        setVariable(variable.toString())
        setRequired(required)
        if (options.isNotEmpty()) {
            // Each option is a LinkedHashMap of NAME -> display and KEY -> submit value.
            setValues(options.map { (name, key) -> linkedMapOf("name" to name, "key" to key) })
        }
    }

    private fun describedBy(vararg attributes: Open311Attribute) = ServiceDescription().apply {
        setAttributes(attributes.toList())
    }

    private val neverStopId: (String) -> Boolean = { false }

    @Test
    fun `each datatype maps to its field type`() {
        val description = describedBy(
            attribute(1, Open311DataType.STRING),
            attribute(2, Open311DataType.TEXT),
            attribute(3, Open311DataType.NUMBER),
            attribute(4, Open311DataType.DATETIME),
            attribute(5, Open311DataType.SINGLEVALUELIST, options = listOf("Yes" to "yes")),
            attribute(6, Open311DataType.MULTIVALUELIST, options = listOf("A" to "a"))
        )

        val fields = Open311FormMapper.mapForm(description, null, false, null, neverStopId).fields

        assertEquals(
            listOf(
                Open311FieldType.TEXT,
                Open311FieldType.TEXT,
                Open311FieldType.NUMBER,
                Open311FieldType.DATETIME,
                Open311FieldType.SINGLE_CHOICE,
                Open311FieldType.MULTI_CHOICE
            ),
            fields.map { it.type }
        )
    }

    @Test
    fun `choice options parse NAME and KEY in order`() {
        val description = describedBy(
            attribute(
                7, Open311DataType.SINGLEVALUELIST,
                options = listOf("Broken" to "broken_key", "Missing" to "missing_key")
            )
        )

        val field = Open311FormMapper.mapForm(description, null, false, null, neverStopId).fields.single()

        assertEquals(
            listOf(Open311Option("broken_key", "Broken"), Open311Option("missing_key", "Missing")),
            field.options
        )
    }

    @Test
    fun `required flag is carried through`() {
        val description = describedBy(
            attribute(8, Open311DataType.STRING, required = true),
            attribute(9, Open311DataType.STRING, required = false)
        )

        val fields = Open311FormMapper.mapForm(description, null, false, null, neverStopId).fields

        assertTrue(fields[0].required)
        assertFalse(fields[1].required)
    }

    @Test
    fun `non-variable attributes become description lines after the service description`() {
        val description = describedBy(
            attribute(10, Open311DataType.STRING, description = "Please read this", variable = false),
            attribute(11, Open311DataType.STRING, description = "Your name")
        )

        val mapped = Open311FormMapper.mapForm(description, "Service intro", false, null, neverStopId)

        assertEquals(listOf("Service intro", "Please read this"), mapped.descriptionLines)
        assertEquals(listOf("Your name"), mapped.fields.map { it.label })
    }

    @Test
    fun `stop-id field is pre-filled with the stop code for transit services`() {
        val description = describedBy(attribute(12, Open311DataType.STRING, description = "Stop ID"))

        val mapped = Open311FormMapper.mapForm(
            description, null, isTransitService = true, stopCode = "55555"
        ) { it == "Stop ID" }

        val field = mapped.fields.single()
        assertTrue(field.isStopIdField)
        assertEquals(FieldValue.Text("55555"), mapped.values[12])
    }

    @Test
    fun `choice attributes without options are skipped`() {
        val description = describedBy(
            attribute(13, Open311DataType.SINGLEVALUELIST, options = emptyList()),
            attribute(14, Open311DataType.STRING)
        )

        val fields = Open311FormMapper.mapForm(description, null, false, null, neverStopId).fields

        assertEquals(listOf(14), fields.map { it.code })
    }

    @Test
    fun `initial values are empty for text and choice fields`() {
        val description = describedBy(
            attribute(15, Open311DataType.STRING),
            attribute(16, Open311DataType.SINGLEVALUELIST, options = listOf("A" to "a")),
            attribute(17, Open311DataType.MULTIVALUELIST, options = listOf("A" to "a"))
        )

        val values = Open311FormMapper.mapForm(description, null, false, null, neverStopId).values

        assertEquals(FieldValue.Text(""), values[15])
        assertEquals(FieldValue.SingleChoice(null), values[16])
        assertEquals(FieldValue.MultiChoice(emptySet()), values[17])
    }

    @Test
    fun `attribute pairs preserve raw datatype and submitted keys`() {
        val fields = listOf(
            Open311Field(1, "Text", Open311FieldType.TEXT, false, Open311DataType.STRING),
            Open311Field(
                2, "Pick one", Open311FieldType.SINGLE_CHOICE, false, Open311DataType.SINGLEVALUELIST,
                options = listOf(Open311Option("a", "A"))
            ),
            Open311Field(
                3, "Pick many", Open311FieldType.MULTI_CHOICE, false, Open311DataType.MULTIVALUELIST,
                options = listOf(Open311Option("x", "X"), Open311Option("y", "Y"))
            )
        )
        val values = mapOf(
            1 to FieldValue.Text("hello"),
            2 to FieldValue.SingleChoice("a"),
            3 to FieldValue.MultiChoice(setOf("x", "y"))
        )

        val pairs = Open311FormMapper.toAttributePairs(fields, values)

        assertEquals(4, pairs.size)
        assertEquals("hello", pairs[0].value)
        assertEquals(Open311DataType.STRING, pairs[0].open311DataType)
        assertEquals("a", pairs[1].value)
        assertEquals(setOf("x", "y"), pairs.drop(2).map { it.value }.toSet())
        assertTrue(pairs.drop(2).all { it.open311DataType == Open311DataType.MULTIVALUELIST })
    }

    @Test
    fun `an unselected single choice emits no pair while text always does`() {
        val fields = listOf(
            Open311Field(1, "Text", Open311FieldType.TEXT, false, Open311DataType.STRING),
            Open311Field(
                2, "Pick one", Open311FieldType.SINGLE_CHOICE, false, Open311DataType.SINGLEVALUELIST,
                options = listOf(Open311Option("a", "A"))
            )
        )
        val values = mapOf(
            1 to FieldValue.Text(""),
            2 to FieldValue.SingleChoice(null)
        )

        val pairs = Open311FormMapper.toAttributePairs(fields, values)

        assertEquals(1, pairs.size)
        assertEquals(1, pairs.single().code)
        assertEquals("", pairs.single().value)
        assertNull(values[2].let { (it as FieldValue.SingleChoice).selectedKey })
    }
}
