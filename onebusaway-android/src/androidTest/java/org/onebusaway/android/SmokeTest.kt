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
package org.onebusaway.android

/**
 * Marks an instrumented test as part of the **API-23 floor smoke subset** — a small, stable set of
 * tests the nightly `smoke-api23` CI leg runs on a minSdk-floor emulator to answer "does the app boot
 * and do the core flows run on the oldest platform we support?" (issue #1818).
 *
 * `minSdk` is 23 but the PR/nightly grid otherwise only *executes* the app at API 33+, so runtime-only
 * floor risks — core-library desugaring correctness, pre-26 platform behavioural shims (notification
 * channels, JobScheduler/WorkManager), Compose on a 2015-era runtime, and startup itself — go unbooted.
 * The `smoke-api23` job in `.github/workflows/android-nightly.yml` selects exactly the tests carrying
 * this annotation via the runner's `annotation` argument:
 *
 * ```
 * -Pandroid.testInstrumentationRunnerArguments.annotation=org.onebusaway.android.SmokeTest
 * ```
 *
 * Keep the tagged set **small and stable** — this leg exists to catch a floor regression within a day,
 * not to re-run the whole suite. Device-flaky tests must stay out of it. Tag at the **class** level when
 * the class is small and cohesive (every method guards the same floor behaviour); for a large or
 * mixed-concern class, tag only the focused method(s) that exercise the floor risk so the subset stays
 * small — the runner's `annotation` filter selects method-level tags too.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class SmokeTest
