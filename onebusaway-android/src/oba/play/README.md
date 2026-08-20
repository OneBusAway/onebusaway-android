# Play Store metadata for the `oba` brand

gradle-play-publisher reads this tree when publishing `obaGoogle*Release`. It lives under
`src/oba/` — not `src/main/` — so the sample white-label brands (`agencyX`, `agencyY`) and the
third-party `kiedybus` brand don't inherit OneBusAway's store listing.

## Release notes

`release-notes/<play-locale>/default.txt` is the "What's new" text for a release. `default.txt`
(rather than `beta.txt`) means the same text carries through when a beta release is promoted to
production, which is how this app ships.

Keep it in sync with the in-app what's-new dialog — `main_help_whatsnew` in
`src/main/res/values/strings.xml` — they describe the same release to the same people. Play caps
each locale at **500 characters**.

## Only `en-US` is present, on purpose

The app translates `main_help_whatsnew` into es, fi, it, and pl, and those translations are ready
to copy here. They aren't checked in yet because the Play API rejects release notes for any locale
the store listing doesn't declare, which would fail the publish. Before adding them, run

    ./gradlew bootstrapObaGoogleReleaseListing

which downloads the live listing (including its supported locales) into this directory, then add
`release-notes/<locale>/default.txt` for each locale that actually appears. Note that bootstrap
overwrites this tree, so review its diff rather than committing it wholesale.
