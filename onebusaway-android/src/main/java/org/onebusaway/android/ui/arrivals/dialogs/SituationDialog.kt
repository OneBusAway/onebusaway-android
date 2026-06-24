/*
 * Copyright (C) 2012-2026 Paul Watts (paulcwatts@gmail.com), Open Transit Software Foundation
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
package org.onebusaway.android.ui.arrivals.dialogs

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.onebusaway.android.R
import org.onebusaway.android.io.elements.ObaSituation
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.util.PreferenceUtils

/**
 * Shows a service alert (situation) in a dialog and marks it read. The buttons mirror the legacy
 * SituationDialogFragment: Hide flags this alert hidden (with an Undo snackbar), Hide All hides
 * every current alert and sets the hide-new-alerts preference, Close just dismisses.
 *
 * @param onDismiss called when the dialog closes; true when the alert was hidden by the user
 * @param onUndo called when the user taps the Undo snackbar after hiding the alert
 * @param showUndoSnackbar shows a snackbar (optionally with an undo action). The host supplies this so
 *   the dialog doesn't reach for a specific View — the standalone activity anchors it to its arrivals
 *   root, while the Compose hosts (home sheet, NavHost destination) drive a Compose `SnackbarHost`.
 */
fun showSituationDialog(
    activity: AppCompatActivity,
    situation: ObaSituation,
    onDismiss: (isAlertHidden: Boolean) -> Unit,
    onUndo: () -> Unit,
    showUndoSnackbar: (messageRes: Int, actionRes: Int?, onAction: (() -> Unit)?) -> Unit
) {
    val situationId = situation.id

    val dialog = MaterialAlertDialogBuilder(activity)
        .setTitle(situation.summary)
        .setMessage(buildMessage(activity, situation))
        .setPositiveButton(R.string.hide) { d, _ ->
            // Update the database to indicate that this alert has been hidden
            ObaContract.ServiceAlerts.insertOrUpdate(situationId, ContentValues(), false, true)
            showUndoSnackbar(
                R.string.alert_hidden_snackbar_text,
                R.string.alert_hidden_snackbar_action
            ) {
                ObaContract.ServiceAlerts.insertOrUpdate(situationId, ContentValues(), false, false)
                onUndo()
            }
            d.dismiss()
            onDismiss(true)
        }
        .setNeutralButton(R.string.hide_all) { d, _ ->
            // Hide existing alerts in the database, and the preference hides new ones
            ObaContract.ServiceAlerts.hideAllAlerts()
            PreferenceUtils.saveBoolean(activity.getString(R.string.preference_key_hide_alerts), true)
            showUndoSnackbar(R.string.all_alert_hidden_snackbar_text, null, null)
            d.dismiss()
            onDismiss(true)
        }
        .setNegativeButton(R.string.close) { d, _ ->
            d.dismiss()
            onDismiss(false)
        }
        .show()

    // Make the description's links (both linkified URLs and "More details") tappable
    dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
        LinkMovementMethod.getInstance()

    // Update the database to indicate that this alert has been read
    ObaContract.ServiceAlerts.insertOrUpdate(situationId, ContentValues(), true, null)
}

/**
 * The dialog body: the alert description (HTML rendered, bare URLs/phones/emails linkified, like
 * the legacy autoLink TextView) plus a "More details" link when the situation has a URL.
 */
private fun buildMessage(activity: AppCompatActivity, situation: ObaSituation): CharSequence {
    val description = situation.description.orEmpty()
    val message = SpannableStringBuilder(
        if (description.isNotEmpty()) {
            @Suppress("DEPRECATION")
            Html.fromHtml(description.replace(Regex("\\r\\n|\\r|\\n"), "<br>"))
        } else {
            activity.getString(R.string.no_description_available)
        }
    )
    @Suppress("DEPRECATION")
    Linkify.addLinks(message, Linkify.ALL)

    val url = situation.url
    if (!url.isNullOrEmpty()) {
        message.append("\n\n")
        val start = message.length
        message.append(activity.getString(R.string.situation_more_details))
        message.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            start, message.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return message
}
