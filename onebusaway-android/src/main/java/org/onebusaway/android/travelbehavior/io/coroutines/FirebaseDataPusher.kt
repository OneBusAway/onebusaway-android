/*
 * Copyright (C) 2022 University of South Florida
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
package org.onebusaway.android.travelbehavior.io.coroutines

import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorUtils

const val TAG = "FirebaseDataPusher"

class FirebaseDataPusher {

    /**
     * Forces a push of data to the Firebase server. A dialog using [context] is shown while data
     * is being pushed. A dialog using [context] with the result is shown after the attempt finishes.
     */
    fun push(context: Context) {
        if (!TravelBehaviorUtils.isUserParticipatingInStudy()) {
            showUserNotEnrolledDialog(context)
            Log.d(TAG, "User not enrolled in study - do nothing")
            return
        }
        val waitDialog = ProgressDialog(context)
        waitDialog.apply {
            setTitle(R.string.push_firebase_data_dialog_title)
            setMessage(Application.get().getString(R.string.push_firebase_data_dialog_summary))
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setIndeterminate(true)
        }

        val db = FirebaseFirestore.getInstance()
        db.enableNetwork() // This seems to be required to avoid an NPE in Firebase (forces internal check to ensureClientConfigured())
        db.waitForPendingWrites().addOnCompleteListener {
            Log.d(TAG, "Pushed to Firebase successfully")
            waitDialog.hide()
            showSuccessDialog(context)
        }.addOnCanceledListener {
            Log.e(TAG, "Push to Firebase canceled")
            waitDialog.hide()
            showCanceledDialog(context)
        }.addOnFailureListener {
            Log.e(TAG, "Push to Firebase failed")
            waitDialog.hide()
            showFailedDialog(context)
        }
        waitDialog.show()
    }

    private fun showUserNotEnrolledDialog(context: Context) {
        showAlertDialog(
            context,
            R.string.push_firebase_data_dialog_user_not_enrolled_title,
            R.string.push_firebase_data_dialog_user_not_enrolled_summary
        )
    }

    private fun showSuccessDialog(context: Context) {
        showAlertDialog(
            context,
            R.string.push_firebase_data_dialog_success_title,
            R.string.push_firebase_data_dialog_success_summary
        )
    }

    private fun showFailedDialog(context: Context) {
        showAlertDialog(
            context,
            R.string.push_firebase_data_dialog_failed_title,
            R.string.push_firebase_data_dialog_failed_summary
        )
    }

    private fun showCanceledDialog(context: Context) {
        showAlertDialog(
            context,
            R.string.push_firebase_data_dialog_canceled_title,
            R.string.push_firebase_data_dialog_canceled_summary
        )
    }

    private fun showAlertDialog(context: Context, @StringRes title: Int, @StringRes summary: Int) {
        val dialog = MaterialAlertDialogBuilder(context)
        dialog.apply {
            setTitle(title)
            setMessage(
                Application.get()
                    .getString(summary)
            )
            setPositiveButton(R.string.ok) { _, _ -> }
        }
        dialog.show()
    }
}