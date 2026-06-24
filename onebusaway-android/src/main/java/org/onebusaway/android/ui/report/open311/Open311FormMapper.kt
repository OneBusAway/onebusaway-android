/*
 * Copyright (C) 2014 University of South Florida,
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
import edu.usf.cutr.open311client.models.Open311AttributePair
import edu.usf.cutr.open311client.models.ServiceDescription

/**
 * Translates between the Open311 client library's [ServiceDescription] / [Open311Attribute] and the
 * JVM-pure form models, replacing the legacy `createServiceDescriptionUI` plus the name↔key and
 * code↔view maps. The library types are plain JARs, so this whole object is unit-testable without
 * Android (Android-only checks like the stop-id keyword lookup are injected as predicates).
 */
object Open311FormMapper {

    /** The read-only description lines plus the dynamic fields and their initial values. */
    data class MappedForm(
        val descriptionLines: List<String>,
        val fields: List<Open311Field>,
        val values: Map<Int, FieldValue>
    )

    /**
     * Builds the form from a service description. Variable attributes become [Open311Field]s;
     * non-variable attributes become read-only description lines, preceded by [serviceDescription].
     */
    fun mapForm(
        description: ServiceDescription,
        serviceDescription: String?,
        isTransitService: Boolean,
        stopCode: String?,
        isStopIdField: (String) -> Boolean
    ): MappedForm {
        val descriptionLines = mutableListOf<String>()
        serviceDescription?.takeIf { it.isNotEmpty() }?.let { descriptionLines.add(it) }

        val fields = mutableListOf<Open311Field>()
        val values = mutableMapOf<Int, FieldValue>()

        for (attribute in description.attributes.orEmpty()) {
            if (!"true".equals(attribute.variable, ignoreCase = true)) {
                attribute.description?.takeIf { it.isNotEmpty() }?.let { descriptionLines.add(it) }
                continue
            }
            val field = attribute.toField(isTransitService, isStopIdField) ?: continue
            fields.add(field)
            values[field.code] = field.initialValue(stopCode)
        }
        return MappedForm(descriptionLines, fields, values)
    }

    /** Inverse mapping: the user's answers as Open311 attribute pairs to submit. */
    fun toAttributePairs(
        fields: List<Open311Field>,
        values: Map<Int, FieldValue>
    ): List<Open311AttributePair> {
        val pairs = mutableListOf<Open311AttributePair>()
        for (field in fields) {
            when (field.type) {
                Open311FieldType.TEXT, Open311FieldType.NUMBER, Open311FieldType.DATETIME -> {
                    val text = (values[field.code] as? FieldValue.Text)?.value.orEmpty()
                    pairs.add(Open311AttributePair(field.code, text, field.rawDataType))
                }

                Open311FieldType.SINGLE_CHOICE ->
                    (values[field.code] as? FieldValue.SingleChoice)?.selectedKey?.let { key ->
                        pairs.add(Open311AttributePair(field.code, key, field.rawDataType))
                    }

                Open311FieldType.MULTI_CHOICE ->
                    (values[field.code] as? FieldValue.MultiChoice)?.selectedKeys.orEmpty().forEach { key ->
                        pairs.add(Open311AttributePair(field.code, key, field.rawDataType))
                    }
            }
        }
        return pairs
    }

    private fun Open311Attribute.toField(
        isTransitService: Boolean,
        isStopIdField: (String) -> Boolean
    ): Open311Field? {
        val rawType = datatype ?: return null
        val type = when (rawType) {
            Open311DataType.STRING, Open311DataType.TEXT -> Open311FieldType.TEXT
            Open311DataType.NUMBER -> Open311FieldType.NUMBER
            Open311DataType.DATETIME -> Open311FieldType.DATETIME
            Open311DataType.SINGLEVALUELIST -> Open311FieldType.SINGLE_CHOICE
            Open311DataType.MULTIVALUELIST -> Open311FieldType.MULTI_CHOICE
            else -> return null
        }
        val label = description.orEmpty()
        val options = if (type == Open311FieldType.SINGLE_CHOICE || type == Open311FieldType.MULTI_CHOICE) {
            parseOptions(values)
        } else {
            emptyList()
        }
        // The legacy UI skipped choice attributes that carried no options.
        if (options.isEmpty() &&
            (type == Open311FieldType.SINGLE_CHOICE || type == Open311FieldType.MULTI_CHOICE)
        ) {
            return null
        }
        return Open311Field(
            code = code,
            label = label,
            type = type,
            required = required == true,
            rawDataType = rawType,
            options = options,
            isStopIdField = isTransitService && label.isNotEmpty() && isStopIdField(label)
        )
    }

    private fun parseOptions(values: List<Any?>?): List<Open311Option> =
        values.orEmpty().mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val key = map[Open311Attribute.KEY]?.toString()
            val name = map[Open311Attribute.NAME]?.toString()
            if (key == null || name == null) null else Open311Option(key, name)
        }

    private fun Open311Field.initialValue(stopCode: String?): FieldValue = when (type) {
        Open311FieldType.TEXT, Open311FieldType.NUMBER, Open311FieldType.DATETIME ->
            FieldValue.Text(if (isStopIdField) stopCode.orEmpty() else "")

        Open311FieldType.SINGLE_CHOICE -> FieldValue.SingleChoice(null)
        Open311FieldType.MULTI_CHOICE -> FieldValue.MultiChoice(emptySet())
    }
}
