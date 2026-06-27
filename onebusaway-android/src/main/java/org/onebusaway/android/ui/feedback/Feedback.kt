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
package org.onebusaway.android.ui.feedback

import org.onebusaway.android.ui.HomeActivity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.apache.commons.io.FileUtils
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.nav.NavigationService
import org.onebusaway.android.nav.NavigationUploadWorker
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * Launches the post-trip destination-reminder feedback screen.
 *
 * Feedback is a NavHost destination hosted by [HomeActivity] (see [NavRoutes.FEEDBACK] and
 * the [FeedbackScreen] / [FeedbackSubmitter] below); this is no longer an Activity but a launcher
 * facade. It keeps the companion extra-key + response constants so [NavigationService] keeps compiling
 * unchanged, and exposes a [makeIntent] that builds an explicit [HomeActivity] intent carrying the
 * feedback route (which HomeActivity's translator navigates to). Non-exported; reached only from the
 * post-trip notification's Yes/No PendingIntents.
 */
object FeedbackLauncher {

    const val TAG = "FeedbackLauncher"

    const val TRIP_ID = ".TRIP_ID"
    const val NOTIFICATION_ID = ".NOTIFICATION_ID"
    const val RESPONSE = ".RESPONSE"
    const val LOG_FILE = ".LOG_FILE"

    const val FEEDBACK_NO = 1
    const val FEEDBACK_YES = 2

    /**
     * Builds the explicit [HomeActivity] intent that opens the feedback destination. Mirrors the former
     * `new Intent(context, FeedbackLauncher.class)` + extras; here the extras become the feedback route's
     * nav-args. RESPONSE is required; the rest are optional.
     */
    @JvmStatic
    @JvmOverloads
    fun makeIntent(
        context: Context,
        response: Int,
        logFile: String? = null,
        tripId: String? = null,
        notificationId: Int = 0,
    ): Intent = HomeActivity.navIntent(
        context,
        NavRoutes.feedback(response, logFile, tripId, notificationId)
    )
}

/**
 * The submit/log glue formerly hosted by the FeedbackLauncher. Re-hosted here so the feedback NavHost
 * destination can run it on send: either append the feedback to the trip log and queue it for upload,
 * or delete the log and report the feedback to analytics only — matching the user's "share logs" choice.
 * Built with the application [Context], the [prefs] repository, and the trip's [logFile] (the
 * destination's nav-arg).
 */
class FeedbackSubmitter(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val logFile: String?,
) {

    fun shareLogsPref(): Boolean =
        prefs.getBoolean(R.string.preferences_key_user_share_destination_logs, true)

    fun setShareLogs(share: Boolean) {
        prefs.setBoolean(R.string.preferences_key_user_share_destination_logs, share)
    }

    fun submit(liked: Boolean, feedback: String) {
        prefs.setBoolean(NavigationService.FIRST_FEEDBACK, false)
        if (shareLogsPref()) {
            moveLog(liked, feedback)
        } else {
            deleteLog()
            logFeedback(liked, feedback)
        }
        Toast.makeText(
            context, context.getString(R.string.feedback_notify_confirmation), Toast.LENGTH_SHORT
        ).show()
    }

    /** Appends the feedback to the trip log and moves it to the upload folder for its response. */
    private fun moveLog(liked: Boolean, feedback: String) {
        val logFilePath = logFile ?: return
        val response = context.getString(
            if (liked) R.string.analytics_label_destination_reminder_yes
            else R.string.analytics_label_destination_reminder_no
        )
        try {
            val file = File(logFilePath)
            FileUtils.write(file, System.lineSeparator() + feedback, true)
            val destFolder = File(
                context.filesDir.absolutePath
                        + File.separator + NavigationService.LOG_DIRECTORY + File.separator + response
            )
            try {
                FileUtils.moveFileToDirectory(file, destFolder, true)
            } catch (e: Exception) {
                Log.e(FeedbackLauncher.TAG, "File move failed")
            }
            setupLogUploadTask()
        } catch (e: IOException) {
            Log.e(FeedbackLauncher.TAG, "File write failed: $e")
        }
    }

    private fun deleteLog() {
        val logFilePath = logFile ?: return
        val deleted = File(logFilePath).delete()
        Log.d(FeedbackLauncher.TAG, "Log deleted $deleted")
    }

    private fun setupLogUploadTask() {
        val uploadCheckWork = PeriodicWorkRequest
            .Builder(NavigationUploadWorker::class.java, 24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance().enqueue(uploadCheckWork)
    }

    private fun logFeedback(liked: Boolean, feedbackText: String) {
        ObaAnalytics.reportDestinationReminderFeedback(
            FirebaseAnalytics.getInstance(context), liked, feedbackText.ifEmpty { null }, null
        )
    }
}

@Composable
internal fun FeedbackScreen(
    initialLiked: Boolean,
    initialSendLogs: Boolean,
    onBack: () -> Unit,
    onSendLogsChanged: (Boolean) -> Unit,
    onSend: (liked: Boolean, text: String) -> Unit
) {
    var liked by rememberSaveable { mutableStateOf(initialLiked) }
    var text by rememberSaveable { mutableStateOf("") }
    var sendLogs by rememberSaveable { mutableStateOf(initialSendLogs) }
    Scaffold(
        topBar = {
            ObaTopAppBar(stringResource(R.string.feedback_label), onBack) {
                IconButton(onClick = { onSend(liked, text) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_social_send_now),
                        contentDescription = stringResource(R.string.report_problem_send),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.feedback_msg), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                ThumbButton(
                    selected = liked,
                    upvote = true,
                    onClick = { liked = true },
                    modifier = Modifier.weight(1f)
                )
                ThumbButton(
                    selected = !liked,
                    upvote = false,
                    onClick = { liked = false },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.feedback_freeText)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = sendLogs,
                    onCheckedChange = {
                        sendLogs = it
                        onSendLogsChanged(it)
                    }
                )
                Text(
                    stringResource(R.string.feedback_checkbox_text),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feedback_log_guide, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One half of the thumbs up / thumbs down pair; the selected side shows its filled icon. */
@Composable
private fun ThumbButton(
    selected: Boolean,
    upvote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when {
        upvote && selected -> R.drawable.ic_thumb_up_selected
        upvote -> R.drawable.ic_thumb_up
        selected -> R.drawable.ic_thumb_down_selected
        else -> R.drawable.ic_thumb_down
    }
    val description = stringResource(
        if (upvote) R.string.feedback_like_button_description
        else R.string.feedback_dislike_button_description
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = Color.Unspecified,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedbackPreview() {
    ObaTheme {
        FeedbackScreen(
            initialLiked = true,
            initialSendLogs = true,
            onBack = {},
            onSendLogsChanged = {},
            onSend = { _, _ -> }
        )
    }
}
