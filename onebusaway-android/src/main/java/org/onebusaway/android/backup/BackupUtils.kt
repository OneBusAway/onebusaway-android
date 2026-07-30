/* Copyright (C) 2018 University of South Florida */
package org.onebusaway.android.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import org.onebusaway.android.R
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.app.di.AnalyticsEntryPoint

object BackupUtils {
    fun restore(activityContext: Context, uri: Uri, onRestored: Runnable?) {
        MaterialAlertDialogBuilder(activityContext)
            .setMessage(R.string.preferences_db_restore_warning)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                doRestore(activityContext, uri, onRestored)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun doRestore(activityContext: Context, uri: Uri, onRestored: Runnable?) {
        val context = activityContext.applicationContext
        AnalyticsEntryPoint.get(context).reportUiEvent(
            PlausibleAnalytics.REPORT_BACKUP_EVENT_URL,
            context.getString(R.string.analytics_label_button_press_restore_preference),
            null
        )
        try {
            Backup.restore(context, uri)
            Toast.makeText(
                context,
                context.getString(R.string.preferences_db_restored, context.getString(R.string.app_name)),
                Toast.LENGTH_LONG
            ).show()
            onRestored?.run()
        } catch (error: IOException) {
            Toast.makeText(
                context,
                context.getString(R.string.preferences_db_restore_error, error.message),
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, error.toString())
        }
    }

    fun save(activityContext: Context, uri: Uri) {
        val context = activityContext.applicationContext
        AnalyticsEntryPoint.get(context).reportUiEvent(
            PlausibleAnalytics.REPORT_BACKUP_EVENT_URL,
            context.getString(R.string.analytics_label_button_press_save_preference),
            null
        )
        Backup.backup(context, uri)
    }

    fun buildCreateBackupFileIntent(): Intent = documentIntent(
        Intent.ACTION_CREATE_DOCUMENT,
        Backup.FILE_NAME
    )

    fun buildSelectBackupFileIntent(): Intent = documentIntent(
        Intent.ACTION_OPEN_DOCUMENT,
        Backup.FILE_NAME
    )

    private fun documentIntent(action: String, title: String): Intent = Intent(action).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_TITLE, title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                "content://com.android.externalstorage.documents/document/primary:Documents".toUri()
            )
        }
    }

    private const val TAG = "BackupUtils"
}
