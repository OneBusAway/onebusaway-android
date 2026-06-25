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
package org.onebusaway.android.ui.mylists

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.onebusaway.android.R

/**
 * Shows the "clear all" confirmation dialog (the legacy ClearConfirmDialog styling) and runs
 * [onConfirm] when the user accepts. Shared by the My-tab list clear options-menu items (hosted by
 * both the `My*` tab activities and the Compose home screen).
 */
internal fun AppCompatActivity.confirmClear(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_DeleteDialog)
        .setTitle(titleRes)
        .setMessage(messageRes)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setPositiveButton(android.R.string.yes) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.no, null)
        .show()
}

/**
 * Shows the "Sort by" single-choice dialog over [optionsRes] and invokes [onPick] with the chosen
 * index when it differs from [currentOrder]. Shared by the starred lists and My Reminders.
 */
internal fun AppCompatActivity.chooseSortOrder(
    currentOrder: Int,
    @ArrayRes optionsRes: Int,
    onPick: (Int) -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.menu_option_sort_by)
        .setSingleChoiceItems(optionsRes, currentOrder) { dialog, which ->
            dialog.dismiss()
            if (which != currentOrder) onPick(which)
        }
        .show()
}
