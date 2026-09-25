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
package org.onebusaway.android.ui.home.help

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.arrivals.components.SampleArrivalRows
import org.onebusaway.android.ui.compose.components.PhonePreview
import org.onebusaway.android.ui.compose.components.StopRowContent
import org.onebusaway.android.ui.compose.theme.isDarkTheme
import org.onebusaway.android.ui.routeinfo.DirectionHeader
import org.onebusaway.android.ui.searchresults.SearchResultMode

/** Real stop and arrival rows, plus an offline map sample styled like the route map. */
@Composable
internal fun SearchWorkflowPreview(mode: SearchResultMode, onSelect: () -> Unit) {
    PhonePreview(onSelect, phoneWidth = 400.dp) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            if (mode == SearchResultMode.MAP) {
                SearchMapPreview()
                SampleArrivalRows(ArrivalDisplayMode.ROUTE, singleRoute = true)
            } else {
                DirectionHeader(stringResource(R.string.search_result_mode_sample_direction), expanded = true, onClick = {})
                for (stop in listOf(R.string.search_result_mode_sample_first_stop, R.string.search_result_mode_sample_stop)) {
                    StopRowContent(
                        name = stringResource(stop),
                        direction = "E",
                        isFavorite = false,
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }
}

/** Fixed sample streets avoid loading map tiles or requesting location during migration. */
@Composable
private fun SearchMapPreview() {
    val dark = MaterialTheme.colorScheme.isDarkTheme()
    val ground = if (dark) Color(0xFF24282C) else Color(0xFFF3F4F5)
    val street = if (dark) Color(0xFF50565C) else Color.White
    val streetEdge = if (dark) Color(0xFF343A40) else Color(0xFFD8DEE4)
    val water = if (dark) Color(0xFF163E4B) else Color(0xFF95D9E8)
    val park = if (dark) Color(0xFF354A35) else Color(0xFFDDEBCD)
    val ink = if (dark) Color(0xFFCFD4D8) else Color(0xFF545B62)
    val route = Color(0xFFE64391)
    val stop = Color(0xFF4CAF50)
    val rim = if (dark) Color(0xFFEEEEEE) else Color.White
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = ink, fontSize = 11.sp)
    val denny = measurer.measure(stringResource(R.string.search_result_mode_sample_street), labelStyle)
    val westlake = measurer.measure(stringResource(R.string.search_result_mode_sample_avenue), labelStyle)
    val lake = measurer.measure(stringResource(R.string.search_result_mode_sample_water), labelStyle.copy(fontSize = 10.sp))
    val bus = painterResource(R.drawable.ic_bus)
    Canvas(Modifier.fillMaxWidth().height(140.dp).background(ground)) {
        val w = size.width
        val h = size.height
        // A small shoreline and park make the map recognizable at thumbnail scale.
        val shore = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * .15f, 0f)
            lineTo(w * .11f, h * .35f)
            lineTo(w * .18f, h * .72f)
            lineTo(w * .1f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(shore, water)
        drawRect(park, Offset(w * .17f, h * .06f), Size(w * .14f, h * .23f))
        // Street casings and narrow cross streets follow the actual map's visual hierarchy.
        for (x in listOf(.34f, .5f, .66f, .82f, .98f)) {
            drawLine(streetEdge, Offset(w * x, 0f), Offset(w * x, h), 7.dp.toPx())
            drawLine(street, Offset(w * x, 0f), Offset(w * x, h), 4.dp.toPx())
        }
        for (y in listOf(.13f, .43f, .76f, .98f)) {
            drawLine(streetEdge, Offset(w * .18f, h * y), Offset(w, h * y), 8.dp.toPx())
            drawLine(street, Offset(w * .18f, h * y), Offset(w, h * y), 5.dp.toPx())
        }
        for (x in listOf(.26f, .42f, .58f, .74f, .9f)) {
            drawLine(streetEdge, Offset(w * x, h * .12f), Offset(w * x, h), 2.dp.toPx())
        }
        val routePath = Path().apply {
            moveTo(w * .26f, h)
            lineTo(w * .26f, h * .43f)
            lineTo(w, h * .43f)
        }
        drawPath(routePath, route, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        for ((x, y) in listOf(.34f to .13f, .66f to .13f, .82f to .13f, .34f to .76f, .5f to .76f, .82f to .76f, .66f to .98f)) {
            drawCircle(rim, 4.dp.toPx(), Offset(w * x, h * y))
            drawCircle(stop, 3.dp.toPx(), Offset(w * x, h * y))
        }
        for (x in listOf(.26f, .42f, .58f, .82f, .98f)) {
            drawCircle(route, 6.dp.toPx(), Offset(w * x, h * .43f))
            drawCircle(rim, 3.5.dp.toPx(), Offset(w * x, h * .43f))
        }
        drawText(denny, topLeft = Offset(w * .49f, h * .25f))
        rotate(-90f, pivot = Offset(w * .47f, h * .91f)) {
            drawText(westlake, topLeft = Offset(w * .47f, h * .91f))
        }
        rotate(-90f, pivot = Offset(w * .02f, h * .8f)) {
            drawText(lake, topLeft = Offset(w * .02f, h * .8f))
        }
        // Use the app's bus glyph and the familiar white-rimmed vehicle disc.
        val center = Offset(w * .72f, h * .43f)
        drawCircle(rim, 16.dp.toPx(), center)
        drawCircle(Color(0xFFFFA000), 14.dp.toPx(), center)
        val glyph = 20.dp.toPx()
        translate(center.x - glyph / 2, center.y - glyph / 2) {
            with(bus) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
}
