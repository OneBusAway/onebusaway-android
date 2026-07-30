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

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * Launches the post-trip destination-reminder feedback screen.
 *
 * Feedback is a NavHost destination hosted by [HomeActivity] (see [NavRoutes.FEEDBACK] and
 * the [FeedbackScreen] / [FeedbackSubmitter] below); this is no longer an Activity but a launcher
 * facade. [makeIntent] builds an explicit [HomeActivity] intent carrying the feedback route, reached
 * only from the post-trip notification's Yes/No actions.
 */
object FeedbackLauncher {

    const val FEEDBACK_NO = 1
    const val FEEDBACK_YES = 2

    /** Builds the explicit [HomeActivity] intent that opens the feedback destination. */
    fun makeIntent(context: Context, response: Int): Intent = HomeActivity.navIntent(context, NavRoutes.feedback(response))
}

/** Reports post-trip feedback without collecting or attaching rider location data. */
class FeedbackSubmitter(private val context: Context) {
    fun submit(liked: Boolean, feedback: String) {
        AnalyticsEntryPoint.get(context).reportDestinationReminderFeedback(
            liked,
            feedback.ifEmpty { null }
        )
        Toast.makeText(
            context,
            context.getString(R.string.feedback_notify_confirmation),
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
internal fun FeedbackScreen(
    initialLiked: Boolean,
    onBack: () -> Unit,
    onSend: (liked: Boolean, text: String) -> Unit
) {
    var liked by rememberSaveable { mutableStateOf(initialLiked) }
    var text by rememberSaveable { mutableStateOf("") }
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
        }
    }
}

/** Affirmative green shown on the selected (active) thumb; neutral thumbs use the theme foreground. */
private val FeedbackSelectedColor = Color(0xFF7EC34A)

/** One half of the thumbs up / thumbs down pair; the selected side is tinted [FeedbackSelectedColor]. */
@Composable
private fun ThumbButton(
    selected: Boolean,
    upvote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = if (upvote) R.drawable.ic_thumb_up else R.drawable.ic_thumb_down
    val tint = if (selected) FeedbackSelectedColor else MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(
        if (upvote) {
            R.string.feedback_like_button_description
        } else {
            R.string.feedback_dislike_button_description
        }
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
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
            onBack = {},
            onSend = { _, _ -> }
        )
    }
}
