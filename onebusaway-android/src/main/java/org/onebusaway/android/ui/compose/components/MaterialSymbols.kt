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
package org.onebusaway.android.ui.compose.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Material Symbols used by secondary-action menus. Each path is transcribed by hand from the Material
 * Symbols set at **24dp, weight 400, grade 0, optical size 24, "outlined" style** — record any icon's
 * source glyph + those axes here when adding one, so the paths can be regenerated or verified. Prefer a
 * `baseline_*_24.xml` vector drawable (the mechanism used elsewhere in this module) for new icons; keep
 * these inline `ImageVector`s only where a menu needs them without a drawable round-trip.
 */
internal object MaterialSymbols {
    val Schedule: ImageVector by lazy {
        symbol("schedule") {
            moveTo(15.3f, 16.7f)
            lineToRelative(1.4f, -1.4f)
            lineTo(13f, 11.6f)
            verticalLineTo(7f)
            horizontalLineTo(11f)
            verticalLineToRelative(5.4f)
            lineToRelative(4.3f, 4.3f)
            close()
            moveTo(12f, 22f)
            quadTo(9.93f, 22f, 8.1f, 21.21f)
            quadTo(6.28f, 20.43f, 4.93f, 19.08f)
            quadTo(3.58f, 17.73f, 2.79f, 15.9f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            reflectiveQuadToRelative(3.9f, 0.79f)
            reflectiveQuadToRelative(3.17f, 2.14f)
            quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
            quadTo(22f, 9.92f, 22f, 12f)
            reflectiveQuadToRelative(-0.79f, 3.9f)
            reflectiveQuadToRelative(-2.14f, 3.17f)
            quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
            reflectiveQuadTo(12f, 22f)
            close()
            moveTo(12f, 12f)
            close()
            moveToRelative(0f, 8f)
            quadToRelative(3.33f, 0f, 5.66f, -2.34f)
            reflectiveQuadTo(20f, 12f)
            quadTo(20f, 8.67f, 17.66f, 6.34f)
            reflectiveQuadTo(12f, 4f)
            quadTo(8.68f, 4f, 6.34f, 6.34f)
            reflectiveQuadTo(4f, 12f)
            reflectiveQuadToRelative(2.34f, 5.66f)
            reflectiveQuadTo(12f, 20f)
            close()
        }
    }

    val TripStatus: ImageVector by lazy {
        symbol("list") {
            moveTo(7f, 9f)
            verticalLineTo(7f)
            horizontalLineTo(21f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveToRelative(0f, 4f)
            verticalLineTo(11f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(7f)
            close()
            moveToRelative(0f, 4f)
            verticalLineTo(15f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(7f)
            close()
            moveTo(4f, 9f)
            quadTo(3.58f, 9f, 3.29f, 8.71f)
            reflectiveQuadTo(3f, 8f)
            quadTo(3f, 7.57f, 3.29f, 7.29f)
            reflectiveQuadTo(4f, 7f)
            reflectiveQuadTo(4.71f, 7.29f)
            reflectiveQuadTo(5f, 8f)
            quadTo(5f, 8.42f, 4.71f, 8.71f)
            reflectiveQuadTo(4f, 9f)
            close()
            moveToRelative(0f, 4f)
            quadTo(3.58f, 13f, 3.29f, 12.71f)
            quadTo(3f, 12.43f, 3f, 12f)
            reflectiveQuadTo(3.29f, 11.29f)
            reflectiveQuadTo(4f, 11f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(5f, 12f)
            reflectiveQuadTo(4.71f, 12.71f)
            reflectiveQuadTo(4f, 13f)
            close()
            moveToRelative(0f, 4f)
            quadTo(3.58f, 17f, 3.29f, 16.71f)
            quadTo(3f, 16.43f, 3f, 16f)
            reflectiveQuadTo(3.29f, 15.29f)
            reflectiveQuadTo(4f, 15f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(5f, 16f)
            reflectiveQuadTo(4.71f, 16.71f)
            reflectiveQuadTo(4f, 17f)
            close()
        }
    }

    val AddReminder: ImageVector by lazy {
        symbol("add_alert") {
            moveTo(11f, 15f)
            horizontalLineToRelative(2f)
            verticalLineTo(13f)
            horizontalLineToRelative(2f)
            verticalLineTo(11f)
            horizontalLineTo(13f)
            verticalLineTo(9f)
            horizontalLineTo(11f)
            verticalLineToRelative(2f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(4f, 19f)
            verticalLineTo(17f)
            horizontalLineTo(6f)
            verticalLineTo(10f)
            quadTo(6f, 7.93f, 7.25f, 6.31f)
            reflectiveQuadTo(10.5f, 4.2f)
            verticalLineTo(3.5f)
            quadToRelative(0f, -0.63f, 0.44f, -1.06f)
            reflectiveQuadTo(12f, 2f)
            reflectiveQuadToRelative(1.06f, 0.44f)
            reflectiveQuadTo(13.5f, 3.5f)
            verticalLineTo(4.2f)
            quadToRelative(2f, 0.5f, 3.25f, 2.11f)
            reflectiveQuadTo(18f, 10f)
            verticalLineToRelative(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveToRelative(8f, -7.5f)
            close()
            moveTo(12f, 22f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            reflectiveQuadTo(10f, 20f)
            horizontalLineToRelative(4f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(12f, 22f)
            close()
            moveTo(8f, 17f)
            horizontalLineToRelative(8f)
            verticalLineTo(10f)
            quadTo(16f, 8.35f, 14.83f, 7.18f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadTo(9.18f, 7.18f)
            reflectiveQuadTo(8f, 10f)
            verticalLineToRelative(7f)
            close()
        }
    }

    val Report: ImageVector by lazy {
        symbol("report") {
            moveTo(12f, 17f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(13f, 16.43f, 13f, 16f)
            reflectiveQuadTo(12.71f, 15.29f)
            reflectiveQuadTo(12f, 15f)
            reflectiveQuadToRelative(-0.71f, 0.29f)
            reflectiveQuadTo(11f, 16f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(12f, 17f)
            close()
            moveTo(11f, 13f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineTo(11f)
            verticalLineToRelative(6f)
            close()
            moveTo(8.25f, 21f)
            lineTo(3f, 15.75f)
            verticalLineTo(8.25f)
            lineTo(8.25f, 3f)
            horizontalLineToRelative(7.5f)
            lineTo(21f, 8.25f)
            verticalLineToRelative(7.5f)
            lineTo(15.75f, 21f)
            horizontalLineTo(8.25f)
            close()
            moveTo(9.1f, 19f)
            horizontalLineToRelative(5.8f)
            lineTo(19f, 14.9f)
            verticalLineTo(9.1f)
            lineTo(14.9f, 5f)
            horizontalLineTo(9.1f)
            lineTo(5f, 9.1f)
            verticalLineToRelative(5.8f)
            lineTo(9.1f, 19f)
            close()
            moveTo(12f, 12f)
            close()
        }
    }
}

private fun symbol(name: String, pathData: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathData
    )
}.build()
