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
import org.onebusaway.android.ui.arrivals.AlertDetails

/**
 * Shows a service alert (situation) in a dialog and marks it read. The buttons mirror the legacy
 * SituationDialogFragment: Hide flags this alert hidden (with an Undo snackbar), Hide All hides
 * every current alert and sets the hide-new-alerts preference, Close just dismisses.
 *
 * The persistence of read/hidden state is delegated to the host via callbacks so this dialog stays
 * pure UI (no ContentProvider/preference access) — the host wires them to the ViewModel/repository.
 *
 * @param onMarkRead persist that the alert was read (invoked when the dialog opens)
 * @param onHide persist that this alert is hidden (the Hide button)
 * @param onUnhide persist that this alert is no longer hidden, and refresh (the Undo action)
 * @param onHideAll hide every current alert and suppress new ones (the Hide All button)
 * @param onDismiss called when the dialog closes; true when the alert was hidden by the user
 * @param showUndoSnackbar shows a snackbar (optionally with an undo action). The host supplies this so
 *   the dialog doesn't reach for a specific View — the standalone activity anchors it to its arrivals
 *   root, while the Compose hosts (home sheet, NavHost destination) drive a Compose `SnackbarHost`.
 */
fun showSituationDialog(
    activity: AppCompatActivity,
    alert: AlertDetails,
    onMarkRead: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onHideAll: () -> Unit,
    onDismiss: (isAlertHidden: Boolean) -> Unit,
    showUndoSnackbar: (messageRes: Int, actionRes: Int?, onAction: (() -> Unit)?) -> Unit
) {
    val dialog = MaterialAlertDialogBuilder(activity)
        .setTitle(alert.summary)
        .setMessage(buildMessage(activity, alert))
        .setPositiveButton(R.string.hide) { d, _ ->
            onHide()
            showUndoSnackbar(
                R.string.alert_hidden_snackbar_text,
                R.string.alert_hidden_snackbar_action
            ) {
                onUnhide()
            }
            d.dismiss()
            onDismiss(true)
        }
        .setNeutralButton(R.string.hide_all) { d, _ ->
            onHideAll()
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

    onMarkRead()
}

/**
 * The dialog body: the alert description (HTML rendered, bare URLs/phones/emails linkified, like
 * the legacy autoLink TextView) plus a "More details" link when the situation has a URL.
 */
private fun buildMessage(activity: AppCompatActivity, alert: AlertDetails): CharSequence {
    val description = alert.description.orEmpty()
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

    val url = alert.url
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
