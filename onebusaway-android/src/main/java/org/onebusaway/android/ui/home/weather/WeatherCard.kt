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
package org.onebusaway.android.ui.home.weather

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint

/**
 * Self-wiring weather feature module: collects [WeatherViewModel] state, re-reads the hide-weather
 * preference on resume, and renders the [WeatherCard] when the chip should show ([onNearby] +
 * not-hidden + a forecast loaded). The tap toasts the forecast summary. The host just places this with
 * its ViewModel + whether the map's NEARBY tab is showing.
 */
@Composable
fun WeatherFeature(viewModel: WeatherViewModel, onNearby: Boolean, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val data = state.data
    if (onNearby && !state.hidden && data != null) {
        WeatherCard(
            iconRes = weatherIconRes(data.icon),
            tempText = formatTemperature(context, data.temperatureF),
            // Fog/wind icons are shown unscaled rather than center-cropped.
            fitIcon = data.icon == "fog" || data.icon == "wind",
            onClick = {
                data.summary?.let { Toast.makeText(context, it.trim(), Toast.LENGTH_SHORT).show() }
            },
            modifier = modifier,
        )
    }
}

/**
 * The small weather chip overlaid at the top of the map (current icon + temperature), replacing the
 * XML `weatherView` CardView. Tapping it surfaces the forecast summary (the host shows a toast).
 * State is supplied by [WeatherFeature] from [WeatherViewModel].
 */
@Composable
fun WeatherCard(
    iconRes: Int,
    tempText: String,
    fitIcon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight().padding(horizontal = 6.dp)
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                contentScale = if (fitIcon) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(tempText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The weather icon drawable for a forecast condition string (e.g. "partly-cloudy-day"). */
private fun weatherIconRes(condition: String): Int = when (condition.replace("-", "_")) {
    "clear_night" -> R.drawable.clear_night
    "rain" -> R.drawable.rain
    "snow" -> R.drawable.snow
    "sleet" -> R.drawable.sleet
    "wind" -> R.drawable.wind
    "fog" -> R.drawable.fog
    "cloudy" -> R.drawable.cloudy
    "partly_cloudy_day" -> R.drawable.partly_cloudy_day
    "partly_cloudy_night" -> R.drawable.partly_cloudy_night
    else -> R.drawable.clear_day
}

/** Formats a Fahrenheit forecast temperature into the user's preferred unit, localized, e.g. "29°C". */
private fun formatTemperature(context: Context, tempF: Double): String {
    val automatic = context.getString(R.string.preferences_preferred_units_option_automatic)
    val preferred = PreferencesEntryPoint.get(context)
        .getString(R.string.preference_key_preferred_temperature_units, automatic)

    // "Automatic" follows the locale default (Fahrenheit only in the US and a few others);
    // otherwise honor the explicit Celsius/Fahrenheit choice.
    val useCelsius = if (preferred == automatic) {
        when (Locale.getDefault().country) {
            "US", "BS", "KY", "LR" -> false
            else -> true
        }
    } else {
        preferred == context.getString(R.string.celsius)
    }

    val displayTemp = (if (useCelsius) (tempF - 32) * 5 / 9 else tempF).toInt()

    // Let ICU render the value + degree unit per locale (digits, symbol, spacing) rather than
    // hand-concatenating. MeasureFormat is API 24+, so fall back to a plain format on API 23.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val unit = if (useCelsius) MeasureUnit.CELSIUS else MeasureUnit.FAHRENHEIT
        return MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
            .format(Measure(displayTemp, unit))
    }
    return "$displayTemp°${if (useCelsius) "C" else "F"}"
}
