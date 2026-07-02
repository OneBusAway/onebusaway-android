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
package org.onebusaway.android.database.oba

import android.util.Log
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.onebusaway.android.app.di.AppScope

/**
 * Readiness gate for the one-time legacy-ContentProvider → Room import (storage-modernization). The
 * import must finish before the first Room read/write of the migrated tables, or a reader would see an
 * empty table and a writer's row could be wiped by the importer's clear-then-insert. Every repository
 * awaits [awaitReady] as the first line of each migrated read AND write; Room-backed flows gate via
 * `onStart { gate.awaitReady() }`.
 *
 * The import runs exactly once: the [Deferred] is started lazily on first [awaitReady] (or kicked
 * eagerly from `Application.onCreate` so it overlaps startup) and every caller awaits that same job.
 *
 * CRASH-IMMUNITY: an import failure must NEVER prevent the app from opening. Each import is wrapped so
 * [awaitReady] always completes normally — a caller can't crash on the gate. A failed import leaves the
 * done-flag unset and the legacy DB file in place (the importer only advances those on success), so the
 * app simply opens without the migrated data AND self-heals: the import is retried on the next launch,
 * and a later app version that fixes the cause recovers the data. Failing to open would be
 * unrecoverable; opening-without-data is not.
 *
 * IMPORTANT: never await this from inside a DAO — the importer itself writes via [LegacyImportDao], so
 * gating a DAO method would deadlock the import on itself.
 */
@Singleton
class ImportGate @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val importer: LegacyDataImporter,
) {
    private val ready: Deferred<Unit> by lazy {
        appScope.async {
            // Independent + individually guarded: a failure of either import must not crash the app or
            // block the other. On failure nothing is marked done, so the next launch retries.
            guarded("Legacy data import") { importer.importIfNeeded() }
            guarded("Survey DB import") { importer.importSurveyDbIfNeeded() }
        }
    }

    /** Runs [block], letting cancellation propagate but swallowing any other failure (crash-immunity). */
    private suspend fun guarded(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "$label failed; opening without it (will retry next launch)", e)
        }
    }

    /** Suspends until the one-time import has completed (starting it on first call). Never throws. */
    suspend fun awaitReady() = ready.await()

    /**
     * Eagerly begins the one-time import (fire-and-forget), so it overlaps app startup instead of
     * first blocking a repository read. Idempotent — the [ready] job still runs exactly once.
     */
    fun start() {
        ready.start()
    }

    private companion object {
        const val TAG = "ImportGate"
    }
}
