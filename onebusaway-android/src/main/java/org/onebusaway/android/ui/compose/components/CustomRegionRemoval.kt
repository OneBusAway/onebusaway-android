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

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.onebusaway.android.R

/**
 * The remove-a-custom-region affordance, shared by the app's two region lists (#2027): the
 * forced-choice picker (`ui/home/RegionPickerHost`) and the Settings region screen
 * (`ui/regions/RegionsScreen`).
 *
 * The two lists render rows very differently — one is bare text inside an `AlertDialog`, the other a
 * full row with a check mark and a distance — so the row composables stay separate. What lives here is
 * the part that must not differ between them: the gesture rule and the confirmation copy. They were
 * briefly duplicated in both files, which is how the two paths started to drift.
 */

/**
 * Makes a region row tappable, and long-pressable to remove when [isRemovable].
 *
 * A null `onLongClick` behaves exactly like `clickable`, so a directory region has nothing to mis-hit.
 * [removeHint] is the accessibility label for the long press — without it the gesture is invisible to a
 * screen reader, for which it is the only route to removal. Hoist [removeHint] out of the row: at most
 * one row in a list is custom, so reading the string per row is wasted work.
 */
fun Modifier.regionRowClickable(
    isRemovable: Boolean,
    removeHint: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
): Modifier = combinedClickable(
    onClick = onClick,
    onLongClickLabel = removeHint.takeIf { isRemovable },
    onLongClick = if (isRemovable) onRemove else null
)

/**
 * Confirms removing the custom region named [regionName]. Long press is easy to trigger by accident and
 * this is destructive, so it asks — and says plainly that the directory regions in the same list are
 * untouched.
 */
@Composable
fun RemoveCustomRegionDialog(regionName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.region_remove_custom_title, regionName)) },
        text = { Text(stringResource(R.string.region_remove_custom_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.region_remove_custom_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
