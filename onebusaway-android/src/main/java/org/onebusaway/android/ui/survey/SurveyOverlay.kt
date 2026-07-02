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
package org.onebusaway.android.ui.survey

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.models.SurveyQuestion
import org.onebusaway.android.ui.survey.utils.SurveyUtils

/**
 * Self-wiring survey feature module: collects [SurveyViewModel] state, builds its callbacks, carries
 * out its one-shot effects (open the external-survey web view / show a toast — STARTED-gated so they
 * don't fire while backgrounded), self-triggers its request, and renders [SurveyOverlay]. The host
 * just places this with its ViewModel + the [onNearby] gate; region-readiness is self-derived by the VM.
 *
 * The request fires when NEARBY is showing and a region has resolved — the survey needs a region to
 * build its study URL. [SurveyViewModel.maybeRequestSurvey] is idempotent and region-gated, so the
 * effect can re-run freely on tab / region changes (replacing the host's first-NEARBY + region-resolved
 * pokes).
 */
@Composable
fun SurveyFeature(
    viewModel: SurveyViewModel,
    onNearby: Boolean,
    onOpenSurvey: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val regionReady by viewModel.regionReady.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The effects collector outlives a recomposition, so read onOpenSurvey through the latest snapshot
    // rather than capturing the lambda from first composition.
    val currentOnOpenSurvey by rememberUpdatedState(onOpenSurvey)
    LaunchedEffect(onNearby, regionReady) {
        if (onNearby && regionReady) {
            viewModel.maybeRequestSurvey()
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is SurveyEffect.OpenExternalSurvey -> currentOnOpenSurvey(effect.url)
                    is SurveyEffect.ShowToast ->
                        Toast.makeText(context, effect.resId, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val callbacks = remember(viewModel) {
        SurveyCallbacks(
            onTextOrRadio = viewModel::setTextOrRadioAnswer,
            onToggleCheckbox = viewModel::toggleCheckbox,
            onSubmitHero = viewModel::submitHero,
            onOpenExternalWithoutHero = viewModel::openExternalWithoutHero,
            onSubmitSheet = viewModel::submitSheet,
            onRequestDismiss = viewModel::requestDismiss,
            onSkip = viewModel::skipSurvey,
            onRemindLater = viewModel::remindMeLater,
            onCancelDismiss = viewModel::cancelDismiss,
        )
    }
    SurveyOverlay(state, callbacks, modifier)
}

/** Callbacks the survey overlay reports back to the [org.onebusaway.android.ui.survey.SurveyViewModel]. */
class SurveyCallbacks(
    val onTextOrRadio: (questionId: Int, answer: String) -> Unit,
    val onToggleCheckbox: (questionId: Int, option: String, checked: Boolean) -> Unit,
    val onSubmitHero: () -> Unit,
    val onOpenExternalWithoutHero: () -> Unit,
    val onSubmitSheet: () -> Unit,
    val onRequestDismiss: () -> Unit,
    val onSkip: () -> Unit,
    val onRemindLater: () -> Unit,
    val onCancelDismiss: () -> Unit,
)

/**
 * The map survey, composed over the map (replacing the legacy `item_survey.xml` hero card +
 * `survey_questions_view.xml` bottom sheet + `SurveyAdapter`). Shows the hero question card, then the
 * remaining-questions bottom sheet, plus the skip/remind/cancel dialog. Driven entirely by
 * [SurveyUiState]; answers flow back through [callbacks].
 */
@Composable
fun SurveyOverlay(
    state: SurveyUiState,
    callbacks: SurveyCallbacks,
    modifier: Modifier = Modifier,
) {
    // The hero card shows while a survey is loaded and the remaining-questions sheet isn't up.
    val hero = state.heroQuestion
    if (hero != null && state.sheet == null) {
        SurveyHeroCard(state, hero, callbacks, modifier)
    }
    if (state.sheet != null) {
        SurveyQuestionsSheet(state, callbacks)
    }
    if (state.showDismissDialog) {
        SurveyDismissDialog(callbacks)
    }
}

@Composable
private fun SurveyHeroCard(
    state: SurveyUiState,
    hero: SurveyQuestion,
    callbacks: SurveyCallbacks,
    modifier: Modifier,
) {
    val external = state.heroMode == SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Box {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = questionLabel(hero),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = callbacks.onRequestDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.dismiss_survey),
                        )
                    }
                }
                if (!external) {
                    SurveyQuestionInput(hero, state, callbacks)
                }
                state.sharedInfo?.let { Text(it, Modifier.padding(top = 8.dp)) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (external) {
                        Button(onClick = callbacks.onOpenExternalWithoutHero) {
                            Text(stringResource(R.string.go))
                        }
                    } else {
                        Button(onClick = callbacks.onSubmitHero) {
                            Text(stringResource(R.string.pager_button_next))
                        }
                    }
                }
            }
            if (state.heroSubmitting) {
                ProgressScrim()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurveyQuestionsSheet(state: SurveyUiState, callbacks: SurveyCallbacks) {
    val sheet = state.sheet ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = callbacks.onRequestDismiss,
        sheetState = sheetState,
    ) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.onebusaway_survey, stringResource(R.string.app_name)),
                    fontWeight = FontWeight.Bold,
                )
                if (sheet.title.isNotEmpty()) {
                    Text(sheet.title, Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
                }
                if (sheet.description.isNotEmpty()) {
                    Text(sheet.description, Modifier.padding(top = 4.dp))
                }
                sheet.questions.forEachIndexed { index, question ->
                    Column(Modifier.padding(top = 16.dp)) {
                        Text(
                            "${index + 1}. ${questionLabel(question)}",
                            fontWeight = FontWeight.Medium,
                        )
                        SurveyQuestionInput(question, state, callbacks)
                    }
                }
                Button(
                    onClick = callbacks.onSubmitSheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.submit))
                }
            }
            if (sheet.submitting) {
                ProgressScrim()
            }
        }
    }
}

@Composable
private fun SurveyQuestionInput(
    question: SurveyQuestion,
    state: SurveyUiState,
    callbacks: SurveyCallbacks,
) {
    val id = question.id
    when (question.content.type) {
        SurveyUtils.RADIO_BUTTON_QUESTION -> {
            val selected = state.textRadioAnswers[id]
            Column {
                question.content.options.orEmpty().forEach { option ->
                    OptionRow(option, onClick = { callbacks.onTextOrRadio(id, option) }) {
                        RadioButton(
                            selected = selected == option,
                            onClick = { callbacks.onTextOrRadio(id, option) },
                        )
                    }
                }
            }
        }

        SurveyUtils.CHECK_BOX_QUESTION -> {
            val selected = state.checkboxAnswers[id].orEmpty()
            Column {
                Text(
                    stringResource(R.string.you_can_select_multiple_options_for_this_pool),
                    Modifier.padding(bottom = 4.dp),
                )
                question.content.options.orEmpty().forEach { option ->
                    OptionRow(
                        option,
                        onClick = { callbacks.onToggleCheckbox(id, option, option !in selected) },
                    ) {
                        Checkbox(
                            checked = option in selected,
                            onCheckedChange = { callbacks.onToggleCheckbox(id, option, it) },
                        )
                    }
                }
            }
        }

        SurveyUtils.TEXT_QUESTION -> {
            OutlinedTextField(
                value = state.textRadioAnswers[id].orEmpty(),
                onValueChange = { callbacks.onTextOrRadio(id, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.write_your_answer_here)) },
            )
        }

        else -> { /* label / external: the label text already shows via questionLabel */ }
    }
}

/** A clickable option row: a leading [control] (radio button / checkbox) + its [text] label. */
@Composable
private fun OptionRow(text: String, onClick: () -> Unit, control: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Text(text)
    }
}

@Composable
private fun SurveyDismissDialog(callbacks: SurveyCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onCancelDismiss,
        title = { Text(stringResource(R.string.dismiss_survey_dialog_title)) },
        text = { Text(stringResource(R.string.dismiss_survey_dialog_body)) },
        confirmButton = {
            TextButton(onClick = callbacks.onCancelDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = callbacks.onSkip) {
                    Text(stringResource(R.string.survey_dismiss_dialog_skip_this_survey))
                }
                TextButton(onClick = callbacks.onRemindLater) {
                    Text(stringResource(R.string.remind_me_latter))
                }
            }
        },
    )
}

@Composable
private fun ProgressScrim() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** The question label, with a trailing asterisk for required questions (the legacy formatting). */
private fun questionLabel(question: SurveyQuestion): String {
    val text = question.content.labelText.orEmpty().trim()
    return if (question.isRequired == true) "$text *" else text
}
