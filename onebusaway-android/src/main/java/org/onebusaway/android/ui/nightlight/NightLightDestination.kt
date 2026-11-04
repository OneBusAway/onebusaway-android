/*
 * Copyright 2013-2026 Colin McDonough, University of South Florida, Sean J. Barbeau,
 * Open Transit Software Foundation
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
package org.onebusaway.android.ui.nightlight

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.onebusaway.android.R
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.common.Shortcuts
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.PreferenceUtils

/** The dimmed "off" color between flashes (a dark scrim over the theme background). */
internal val COLOR_DARK = Color(0xCC000000)

/** Amount of time the light is left on for a single flash, in milliseconds. */
private const val FLASH_TIME_ON = 75L

/** Amount of time between flashes, in milliseconds — two quick blinks then a beat. */
private val WAIT_TIMES = longArrayOf(100, 100, 400)

private const val PREFERENCE_SHOWED_DIALOG = "showed_night_light_dialog"

/**
 * The night-light NavHost destination: a flashing light riders show at night to flag
 * bus drivers. Re-hosts the former [NightLightLauncher]'s window-level concerns — keep-screen-on,
 * full brightness, portrait lock — on the single host activity for as long as this destination is on
 * screen (a [DisposableEffect] adds them on enter and restores them on exit), shows the one-time
 * epilepsy intro, and drives [NightLightScreen]. [onBack] pops the back stack.
 */
// The night-light screen doubles as a reading light, so it deliberately pins portrait (with the
// screen kept on at full brightness) while visible; the DisposableEffect below restores the caller's
// prior orientation on exit. The lock is the intended UX, not an accessibility oversight, so we
// suppress SourceLockedOrientationActivity here rather than drop it.
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun NightLightRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val window = activity.window

    // Window/orientation concerns live as long as the destination is on screen.
    DisposableEffect(Unit) {
        val previousOrientation = activity.requestedOrientation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = previousOrientation
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    // Whether the user has accepted the epilepsy intro (now or on an earlier visit). Both full
    // brightness and the flashing wait for it: the intro dialog doesn't take the activity out of
    // RESUMED, so nothing else keeps the strobe from running behind the warning (#2358).
    var accepted by remember { mutableStateOf(PreferenceUtils.getBoolean(PREFERENCE_SHOWED_DIALOG, false)) }
    LaunchedEffect(accepted) {
        if (accepted) {
            val lp = window.attributes
            lp.screenBrightness = 1.0f
            window.attributes = lp
        }
    }

    // One-time intro dialog (gated by a pref); "start" accepts, "cancel" leaves without a flash.
    // Cancel also hides the dialog at once, rather than leaving it up through the pop transition.
    var cancelled by remember { mutableStateOf(false) }
    if (!accepted && !cancelled) {
        NightLightIntroDialog(
            onStart = {
                PreferenceUtils.saveBoolean(PREFERENCE_SHOWED_DIALOG, true)
                accepted = true
            },
            onCancel = {
                cancelled = true
                onBack()
            }
        )
    }

    NightLightScreen(accepted = accepted, onBack = onBack, onCreateShortcut = {
        val shortcut = Shortcuts.makeShortcutInfo(
            activity,
            activity.getString(R.string.stop_info_option_night_light),
            HomeActivity.navIntent(activity, NavRoutes.NIGHT_LIGHT),
            R.drawable.lightbulb_2
        )
        ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null)
    })
}

/**
 * The one-time intro: what the night light is for, then the photosensitive-epilepsy warning set apart
 * on an error-container surface so it can't be skimmed past as part of the pitch. Not dismissible by
 * back or an outside tap — the user has to choose Start or Cancel.
 */
@Composable
private fun NightLightIntroDialog(onStart: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.night_light_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.night_light_dialog_message))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(stringResource(R.string.night_light_dialog_warning), Modifier.padding(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStart) { Text(stringResource(R.string.night_light_start)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.night_light_cancel)) }
        }
    )
}

/**
 * The flashing screen: white / theme-color / white blinks with a pause between rounds, matching
 * the legacy flash thread. It stays dark until [accepted]; after that, tapping the screen pauses
 * and resumes the flashing.
 */
@Composable
private fun NightLightScreen(accepted: Boolean, onBack: () -> Unit, onCreateShortcut: () -> Unit) {
    // Remembered: NightLightScreen recomposes on every flash tick (~10x/sec) as displayColor changes.
    val themeColor = colorResource(R.color.theme_primary)
    val flashColors = remember(themeColor) { listOf(Color.White, themeColor, Color.White) }
    var paused by remember { mutableStateOf(false) }
    val displayColor by rememberFlashColor(accepted = accepted, paused = paused, flashColors = flashColors)

    Scaffold(
        topBar = {
            ObaTopAppBar(stringResource(R.string.stop_info_option_night_light), onBack) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.night_light_create_shortcut)) },
                            onClick = {
                                expanded = false
                                onCreateShortcut()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(displayColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { paused = !paused }
        )
    }
}

/**
 * The screen color over time: once the user has [accepted] the epilepsy intro and while not [paused],
 * cycles [flashColors] (on for [FLASH_TIME_ON], then the dark scrim for the next [WAIT_TIMES] gap), and
 * only while the lifecycle is RESUMED — replacing the legacy background thread. Otherwise it holds
 * [COLOR_DARK]. Consent is a required input of the loop itself, not a gate a caller can forget: the
 * intro dialog leaves the activity RESUMED, so nothing else stops the strobe behind it (#2358).
 */
@Composable
internal fun rememberFlashColor(accepted: Boolean, paused: Boolean, flashColors: List<Color>): State<Color> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enabled = accepted && !paused
    return produceState(COLOR_DARK, enabled, flashColors) {
        if (!enabled) {
            value = COLOR_DARK
            return@produceState
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var counter = 0
            while (isActive) {
                value = flashColors[counter % flashColors.size]
                delay(FLASH_TIME_ON)
                value = COLOR_DARK
                delay(WAIT_TIMES[counter % WAIT_TIMES.size])
                counter++
            }
        }
    }
}
