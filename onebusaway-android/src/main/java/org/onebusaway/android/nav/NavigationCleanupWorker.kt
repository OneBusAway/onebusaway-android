/*
 * Copyright (C) 2019-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class NavigationCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        File(applicationContext.filesDir, NavigationService.LOG_DIRECTORY)
            .walkTopDown()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { file ->
                if (!file.delete()) Log.w(TAG, "Unable to delete expired navigation log ${file.name}")
            }
        return Result.success()
    }

    companion object {
        const val TAG = "NavigationCleanupWorker"
        const val UNIQUE_WORK_NAME = "navigation_log_cleanup"
        private const val RETENTION_MS = 24L * 60L * 60L * 1_000L
    }
}
