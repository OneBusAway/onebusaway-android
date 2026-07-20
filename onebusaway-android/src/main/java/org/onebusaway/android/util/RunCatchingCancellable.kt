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
package org.onebusaway.android.util

import kotlinx.coroutines.CancellationException

/**
 * [runCatching], but coroutine-safe. Kotlin's [runCatching] catches every [Throwable] — including the
 * [CancellationException] a cancelled coroutine throws from a suspend call — and captures it into the
 * returned [Result]. In coroutine code that is a bug: the cancellation is converted into an ordinary
 * `Result.failure`, so the cancelled coroutine keeps running and its callers see the cancellation as a
 * normal error/empty state (structured concurrency breaks). See #1908/#1921 for the class of bug this
 * caused across the api/repository layer.
 *
 * This wrapper is behaviour-identical to [runCatching] except it **rethrows** [CancellationException]
 * instead of capturing it, so a real failure still resolves to `Result.failure` while cancellation
 * propagates normally. Use it in any `suspend` function / coroutine in place of bare `runCatching`; the
 * `SwallowedCancellation` lint check (module `:lint-rules`) fails the build on a bare `runCatching`
 * inside a `suspend` function to keep this the only path.
 *
 * It is `inline` (mirroring `kotlin.runCatching`), so [block] may call suspend functions when invoked
 * from a suspend context and the cancellation guard costs nothing.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = runCatching(block).onFailure { if (it is CancellationException) throw it }
