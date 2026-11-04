Please make sure these boxes are checked before submitting your pull request - thanks!

- [ ] Formatted with `./gradlew spotlessApply`
- [ ] `./gradlew test check -x lint -PwarningsAsErrors=true` passes (this is the same strictness CI uses)
- [ ] If the change touches UI or device behavior: `./gradlew connectedObaGoogleDebugAndroidTest` passes, and screenshots are attached
- [ ] If the change relies on a heuristic (a magic threshold, a guessed unit, fuzzy matching): it's called out below for sign-off

See [Before you open a pull request](https://github.com/OneBusAway/onebusaway-android/blob/main/.github/CONTRIBUTING.md#before-you-open-a-pull-request) for what each of these catches.
