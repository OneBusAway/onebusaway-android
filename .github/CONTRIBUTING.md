# Contribute to OneBusAway for Android

This guide details how to use issues and pull requests (for new code) to improve OneBusAway for Android.

## Getting Started

Want to help, but don't know where to start?  Check out the issues we've tagged with the ["your first PR" label](https://github.com/OneBusAway/onebusaway-android/labels/your%20first%20PR).  These issues are a good place to get familiar with the OneBusAway code.  If you have questions, please comment on the issue, especially if you start working on a solution so that others don't duplicate your work.

## Individual Contributor License Agreement (ICLA)

To ensure that the app source code remains fully open-source under a common license, we require that contributors sign an electronic ICLA before contributions can be merged.  When you submit a pull request, you'll be prompted by the [CLA Assistant](https://cla-assistant.io/) to sign the ICLA.

## Code of Conduct

This project adheres to a [Code of Conduct](https://github.com/OneBusAway/onebusaway/blob/master/CODE_OF_CONDUCT.md). By participating, you are expected to honor this code.

## Code Style

Formatting is automated with [Spotless](https://github.com/diffplug/spotless) — no IDE setup or
manual reformatting required. It runs [ktlint](https://github.com/pinterest/ktlint) (Kotlin and
`*.gradle.kts`, in the `android_studio` style — see `.editorconfig`) and google-java-format (the
remaining Java files).

Before pushing, format your changes:

```bash
./gradlew spotlessApply    # rewrite files in place
./gradlew spotlessCheck    # verify only, without modifying files
```

**CI enforces `spotlessCheck`** — a PR with unformatted code fails the build, so run `spotlessApply`
first. The one-time bulk reformat is listed in `.git-blame-ignore-revs`; enable it locally with
`git config blame.ignoreRevsFile .git-blame-ignore-revs` so `git blame` skips it (GitHub does this
automatically).

## Before you open a pull request

CI holds every PR to a stricter standard than a plain local build. Here's what fails a build, and
how to catch it on your machine first.

### Run these locally

```bash
./gradlew spotlessApply                                  # format
./gradlew test check -x lint -PwarningsAsErrors=true     # unit tests + formatting + compile, CI-strict
```

If your change touches UI or anything device-dependent, also run the instrumented tests on a device
or emulator: `./gradlew connectedObaGoogleDebugAndroidTest`. CI runs them on an API 33 emulator
for every PR. A nightly job also boots the API 23 floor and runs the `@SmokeTest` subset there.

### What CI enforces

* **Zero Kotlin compiler warnings.** CI builds with `-PwarningsAsErrors=true`, so a deprecation, an
  unnecessary `!!`, or dead code fails the build. A plain local `./gradlew` only prints these as
  `w:` lines, so they're easy to miss. Fix a warning at its source. Use `@Suppress` only when the
  fix truly can't happen in your PR, and then add a one-line rationale and a link to a tracking
  issue at the suppression site.
* **Formatting** — `spotlessCheck`, see [Code Style](#code-style).
* **Android Lint, with no baseline.** Every lint warning is an error and nothing is grandfathered.
  Lint is slow (5–15 minutes), so it runs **nightly against `main`, not on pull requests**. A lint
  finding in your change will turn the nightly build red after your PR merges. If your change calls
  platform APIs or touches the manifest or resources, run
  `./gradlew :onebusaway-android:lintObaGoogleDebug -PwarningsAsErrors=true` before you push. Pay
  particular attention to `NewApi`. The app's `minSdk` is 23 (Android 6.0), and lint is the only
  gate that catches an API call newer than that.

The project also has custom lint checks in [`lint-rules/`](../lint-rules/README.md). They enforce
the rules below, so learning the rules first saves a round trip.

### Project rules the compiler and lint enforce

**Server time vs device time.** Any duration shown to a user and measured against a timestamp from
the OBA server (an ETA, a countdown, "updated N seconds ago" for a vehicle) must use the server's
clock as "now", never `System.currentTimeMillis()`. Otherwise device clock skew leaks straight into
the number. New time math uses the typed instants in `org.onebusaway.android.time` (`ServerTime`,
`WallTime`, `ElapsedTime`). Subtracting two values from different clocks won't compile. The lint
checks `RawClockArithmetic`, `UnwrappedClockValue`, and `PrematureUnwrap` flag raw clock `Long`s
that escape that system. Wrap a raw value into its type where it enters the app, and unwrap it only
where it's handed to a platform API. `WireTimeEscape` keeps raw time fields on the API's data
classes inside the adapter layer that converts them.

**No bare `!!` in production code.** The `ForcedNonNull` check flags it in `src/main`, `src/google`,
and `src/maplibre`. Handle the null (`?:`, an early return, `?.let`), or fix the type at the boundary
that made it nullable. If neither works, assert with a reason: `checkNotNull(x) { "why" }`. Test code
is exempt.

**Never swallow `CancellationException`.** In coroutine code, `runCatching` and a broad
`catch (e: Exception)` both catch cancellation, so cancelled work keeps running. Use
`runCatchingCancellable` (`org.onebusaway.android.util`) instead of `runCatching`. The
`SwallowedCancellation` lint check fails the build on a bare `runCatching` in a `suspend` function.
Before a broad `catch`, add `catch (e: CancellationException) { throw e }`.

### Project rules that review enforces

**No unsanctioned heuristics.** Don't guess at things the data should state, such as whether a
timestamp is in seconds or milliseconds based on its size, a magic threshold, or fuzzy matching where
an exact key exists. Resolve the fact at its source: normalize at the parse boundary, carry an
explicit field, or fail loudly. If a heuristic really is unavoidable, call it out in your PR
description so a maintainer can sign off. Then document the assumption and how it fails at the call
site.

**Strings that mention the app name** use a `%1$s` placeholder filled with
`getString(R.string.app_name)`, so rebranded builds can override just the name. See
[Rebranding](../docs/REBRANDING.md). Add new strings to `values/strings.xml` only. Translators handle
the other locales.

**New code is Kotlin**, even when Java calls it.

**Add tests with logic.** If a change has real branching logic, pull that logic into a pure function
and give it a JVM unit test in `src/test/`. Those tests run in seconds, and CI runs them on every PR.

For a map of the codebase, see [Code Architecture](../docs/CODE_ARCHITECTURE.md). The full
engineering notes, including the reasons behind each rule above, are in
[`CLAUDE.md`](../CLAUDE.md) at the repository root. It's written for AI coding assistants but is just
as useful to people.

## Closing policy for issues and pull requests

OneBusAway for Android is a popular project and the capacity to deal with issues and pull requests is limited. Out of respect for our volunteers, issues and pull requests not in line with the guidelines listed in this document may be closed without notice.

Please treat our volunteers with courtesy and respect, it will go a long way towards getting your issue resolved.

Issues and pull requests should be in English and contain appropriate language for audiences of all ages.

## Issue tracker

The [issue tracker](https://github.com/OneBusAway/onebusaway-android/issues) is only for obvious bugs, misbehavior, & feature requests in the latest stable or development release of OneBusAway for Android. When submitting an issue please conform to the issue submission guidelines listed below. Not all issues will be addressed and your issue is more likely to be addressed if you submit a pull request which partially or fully addresses the issue.

### Issue tracker guidelines

**[Search](https://github.com/OneBusAway/onebusaway-android/search?q=&ref=cmdform&type=Issues)** for similar entries before submitting your own, there's a good chance somebody else had the same issue or feature request. Show your support with `:+1:` and/or join the discussion. Please submit issues in the following format (as the first post) and feature requests in a similar format:

1. **Summary:** Summarize your issue in one sentence (what goes wrong, what did you expect to happen)
2. **Steps to reproduce:** How can we reproduce the issue?
3. **Expected behavior:** What did you expect the app to do?
4. **Observed behavior:** What did you see instead?  Describe your issue in detail here.
5. **Device and Android version:** What make and model of device (e.g., Pixel 8) did you see this on, and what Android version (e.g., Android 15) is it running? Which OneBusAway version (Settings → About)?
6. **Screenshots:** Press the Power and Volume Down buttons at the same time to take one.
7. **Possible fixes**: If you can, link to the line of code that might be responsible for the problem.

## Pull requests

We welcome pull requests with fixes and improvements to OneBusAway for Android code, tests, and/or documentation. The features we would really like a pull request for are [open issues with the enhancements label](https://github.com/OneBusAway/onebusaway-android/issues?labels=enhancement&page=1&state=open).

### Pull request guidelines

If you can, please submit a pull request with the fix or improvements including tests. If you don't know how to fix the issue but can write a test that exposes the issue we will accept that as well. In general bug fixes that include a regression test are merged quickly while new features without proper tests are least likely to receive timely feedback. The workflow to make a pull request is as follows:

1. Fork the project on GitHub
2. Create a feature branch
3. Write tests and code
4. Format your changes and run the checks in [Before you open a pull request](#before-you-open-a-pull-request)
5. Rebase onto the latest `main` if your branch has fallen behind
6. Your PR branch may be a single squashed commit or a string of commits — whichever you prefer. The PR is squashed when it's merged to `main`, so you don't need to squash it yourself.
7. Push your commits to your fork
8. Submit a pull request with a motive for your change and the method you used to achieve it
9. [Search for issues](https://github.com/OneBusAway/onebusaway-android/search?q=&ref=cmdform&type=Issues) related to your pull request and mention them in the pull request description or comments

We will accept pull requests if:

* The code has proper tests and all tests pass (or it is a test exposing a failure in existing code)
* It can be merged without problems (if not, rebase onto `main`)
* It doesn't break any existing functionality
* It's quality code that conforms to standard style guides and best practices
* The description includes a motive for your change and the method you used to achieve it
* It is not a catch all pull request but rather fixes a specific issue or implements a specific feature
* It keeps the OneBusAway for Android code base clean and well structured
* We think other users will benefit from the same functionality
* If it makes changes to the UI the pull request should include screenshots

## License

By contributing code to this project via pull requests, patches, or any other process, you are agreeing to license your contributions under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
