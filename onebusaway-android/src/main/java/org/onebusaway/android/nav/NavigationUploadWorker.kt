/*
 * Copyright (C) 2019-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics

interface NavigationLogUploader {
    suspend fun uploadPending(): Boolean
}

internal class FirebaseNavigationLogUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analytics: ObaAnalytics
) : NavigationLogUploader {
    override suspend fun uploadPending(): Boolean = runCatching {
        // Authentication belongs to upload execution, never navigation startup.
        FirebaseAuth.getInstance().signInAnonymously().awaitTask()
        uploadResponse(context.getString(R.string.analytics_label_destination_reminder_yes), true)
        uploadResponse(context.getString(R.string.analytics_label_destination_reminder_no), false)
    }.onFailure { Log.e(TAG, "Navigation log upload failed", it) }.isSuccess

    private suspend fun uploadResponse(response: String, liked: Boolean) {
        val directory = File(File(context.filesDir, NavigationService.LOG_DIRECTORY), response)
        directory.listFiles().orEmpty().filter(File::isFile).forEach { file ->
            val feedback = file.useLines { lines -> lines.lastOrNull().orEmpty() }
            val reference = FirebaseStorage.getInstance().reference
                .child("android/destination_reminders/$response/${file.name}")
            val metadata = StorageMetadata.Builder()
                .setCustomMetadata("Response", response)
                .setCustomMetadata("FeedbackText", feedback)
                .build()
            reference.putFile(Uri.fromFile(file), metadata).awaitTask()
            val downloadUrl = reference.downloadUrl.awaitTask().toString()
            analytics.reportDestinationReminderFeedback(liked, feedback.ifEmpty { null }, downloadUrl)
            if (!file.delete()) Log.w(TAG, "Uploaded but could not delete ${file.name}")
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

    private companion object {
        const val TAG = "NavigationLogUploader"
    }
}

@HiltWorker
class NavigationUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val uploader: NavigationLogUploader
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result = if (uploader.uploadPending()) Result.success() else Result.retry()

    companion object {
        const val TAG = "NavigationUploadWorker"
        const val UNIQUE_WORK_NAME = "navigation_log_upload"
    }
}
