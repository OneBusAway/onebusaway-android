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
5. **Device and Android version:** What make and model device (e.g., Samsung Galaxy S3) did you encounter this on?  What Android version (e.g., Android 4.0 Ice Cream Sandwich) are you running?  Is it the stock version from the manufacturer or a custom ROM?
5. **Screenshots:** Can be created by pressing the Volume Down and Power Button at the same time on Android 4.0 and higher.
6. **Possible fixes**: If you can, link to the line of code that might be responsible for the problem.

## Pull requests

We welcome pull requests with fixes and improvements to OneBusAway for Android code, tests, and/or documentation. The features we would really like a pull request for are [open issues with the enhancements label](https://github.com/OneBusAway/onebusaway-android/issues?labels=enhancement&page=1&state=open).

### Pull request guidelines

If you can, please submit a pull request with the fix or improvements including tests. If you don't know how to fix the issue but can write a test that exposes the issue we will accept that as well. In general bug fixes that include a regression test are merged quickly while new features without proper tests are least likely to receive timely feedback. The workflow to make a pull request is as follows:

1. Fork the project on GitHub
2. Create a feature branch
3. Write tests and code
4. Run the unit tests with `gradlew connectedObaGoogleDebugAndroidTest` to make sure you didn't break anything
5. Format your changes with `./gradlew spotlessApply`.
6. Your PR branch may be a single squashed commit or a string of commits — whichever you prefer. The PR is squashed when it's merged to `main`, so you don't need to squash it yourself.
7. Push your commits to your fork
8. Submit a pull request with a motive for your change and the method you used to achieve it
9. [Search for issues](https://github.com/OneBusAway/onebusaway-android/search?q=&ref=cmdform&type=Issues) related to your pull request and mention them in the pull request description or comments

We will accept pull requests if:

* The code has proper tests and all tests pass (or it is a test exposing a failure in existing code)
* It can be merged without problems (if not please use: `git rebase master`)
* It doesn't break any existing functionality
* It's quality code that conforms to standard style guides and best practices
* The description includes a motive for your change and the method you used to achieve it
* It is not a catch all pull request but rather fixes a specific issue or implements a specific feature
* It keeps the OneBusAway for Android code base clean and well structured
* We think other users will benefit from the same functionality
* If it makes changes to the UI the pull request should include screenshots

## License

By contributing code to this project via pull requests, patches, or any other process, you are agreeing to license your contributions under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
