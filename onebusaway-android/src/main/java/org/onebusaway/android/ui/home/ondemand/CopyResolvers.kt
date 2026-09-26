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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.DistanceText
import org.onebusaway.android.ondemand.PluralSpec
import org.onebusaway.android.ondemand.TextSpec

/** Resolves a [TextSpec] against resources, flattening nested specs and distances. */
@Composable
fun TextSpec.resolve(): String = stringResource(res, *args.resolved())

@Composable
fun PluralSpec.resolve(): String = pluralStringResource(res, count, *args.resolved())

/**
 * Resolves whatever a copy function returned: a [TextSpec], a [PluralSpec] (e.g. `addressLine`), a
 * [DistanceText], or a plain `String` / `Int`.
 */
@Composable
fun Any.resolveCopy(): String = when (this) {
    is TextSpec -> resolve()
    is PluralSpec -> resolve()
    is DistanceText -> stringResource(R.string.ondemand_distance, value, stringResource(unit.abbreviationRes))
    is String -> this
    else -> toString()
}

// Nested specs and distances become strings; a number stays a number so a `%d` placeholder still formats.
@Composable
private fun Any.resolveArgument(): Any = when (this) {
    is TextSpec, is PluralSpec, is DistanceText -> resolveCopy()
    else -> this
}

@Composable
private fun List<Any>.resolved(): Array<Any> = map { it.resolveArgument() }.toTypedArray()
