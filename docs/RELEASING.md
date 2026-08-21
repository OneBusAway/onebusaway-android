# Releasing OneBusAway for Android

Releases go out through the **Release to Google Play** workflow
(`.github/workflows/release.yml`), which builds a signed `obaGoogle` release App Bundle and uploads
it with [gradle-play-publisher][gpp].

The workflow is **manual dispatch only**. Publishing is outward-facing and hard to undo, so nobody
ships by pushing a branch or mistyping a tag; a human opens Actions and presses Run workflow.

A release climbs the tracks one rung at a time, and every rung is a human decision:

| Track | Who gets it | How it's reached |
| --- | --- | --- |
| `alpha` | closed testing — `onebusaway-developers@googlegroups.com` | the workflow's default; where every release starts |
| `Closed beta` / `internal` | nobody yet — no testers are attached to either | the workflow, once a group is added in the Console |
| `beta` | **open testing** — anyone who ever joined the programme | the workflow, staged only (see below) |
| `production` | everyone | promoted by hand in the Play Console |

The rung that deserves care is `beta`. It is Play's *open* testing channel, and enrolment is
per-programme and sticky rather than per-release: everyone who has ever joined receives beta builds
as ordinary silent automatic updates, including people who joined years ago and have forgotten. So
a release starts closed, and graduating it to open testing is a separate, deliberate act.

[gpp]: https://github.com/Triple-T/gradle-play-publisher

## Version numbering

`versionName` is **`YY.RELEASE.PATCH`**: the two-digit year, then which release it is within that
year, then any patch on that release. `26.1.0` was the first release of 2026, `26.2.0` the second.

**The minor is not the month.** `26.1.0` shipped in March 2026, which rules that reading out — a
plausible enough guess that it has already been made once, and it is the reason 27.0.0 briefly
existed on the alpha track. A release in August is `26.N.0` for whatever N is next, not `26.8.0`.

The year rolls the major: the first release of 2027 is `27.1.0`. Nothing is derived automatically —
`versionName` is edited by hand, and `versionCode` comes from Play (see below), so the two carry no
relationship to each other. Don't infer the next number from the git tags either; see step 2.

This convention arrived undocumented — the switch from `2.14.9` to `26.1.0` rode along inside an
unrelated commit ("Add gradle-play-publisher for automated Play Store releases", 2026-03-19) with no
rationale — which is precisely how it came to be misread. Hence writing it down here.

## Cutting a release

1. **Pick the commit.** It should be a commit on `main` that is green in
   [Android CI Build](../.github/workflows/android.yml). The release workflow does not re-run the
   test suite — it takes ~20 minutes and needs an emulator — though the release build is itself a
   real compile gate, since it runs R8 and resource shrinking that the debug CI path never touches.
2. **Set `versionName`** in `onebusaway-android/build.gradle.kts`. This is the only version field
   you edit by hand: `versionCode` is auto-incremented from the highest code already on Play (the
   `resolutionStrategy = AUTO` in the `play {}` block), so the number checked into the file is only
   a fallback for builds without Play credentials.

   Check what's actually live before choosing a name. Tagging only became reliable at `v26.2.0` —
   earlier releases went out untagged, so `26.1.0` is live on Play as versionCode 154 with nothing
   in git to show for it. For anything older than `v26.2.0`, the Play Console's release dashboard
   (or `bootstrapObaGoogleReleaseListing`) is the authority, not the tags.
3. **Write the release notes**, in two places that must agree:
   - `onebusaway-android/src/oba/play/release-notes/en-US/default.txt` — the Play "What's new"
     text, capped at 500 characters. `en-US` is the only locale here because it is the only one the
     store listing declares; adding others would fail the publish. See the [README][playreadme].
   - `main_help_whatsnew` in `onebusaway-android/src/main/res/values/strings.xml` (and the
     `values-*` translations) — the in-app what's-new dialog. Same release, same audience.
4. **Run the workflow.** Actions → Release to Google Play → Run workflow, on the commit you picked.
   - `track`: leave it at `alpha`. That is closed testing, and the only track with an audience
     already attached.
   - `release_status`: `completed` is right for a closed track — the closed audience is itself the
     staging mechanism. Use `draft` instead for the first run after any change to the signing or
     credential setup: it uploads the bundle without publishing it, proving the pipeline without
     anything reaching a device.
   - `user_fraction`: ignored unless `release_status` is `inProgress`.
5. **Tag it.** Once the upload succeeds, tag the released commit and push the tag, so the bundle on
   Play maps to a commit. Do this every time — the tags fell out of sync with Play once already,
   which is why step 2 warns you not to trust them:

       git tag -a v26.2.0 -m "26.2.0" <commit>
       git push origin v26.2.0

   If a release is superseded before it goes anywhere — a wrong version number caught on a closed
   track, say — delete the tag along with it, so no tag points at a build that was never graduated:
   `git push origin :refs/tags/vX.Y.Z && git tag -d vX.Y.Z`.

6. **Let the closed track sit.** Watch Crashlytics and the Play Console's vitals. This is the whole
   point of the closed rung — it is the only audience that can be surprised cheaply.
7. **Graduate to open testing** when it looks clean: run the workflow again with `track: beta` and
   `release_status: inProgress`, starting at a small `user_fraction`. A full rollout to `beta` is
   refused by a guard step in the workflow, because that track updates its users silently; widen
   the rollout from the Play Console once it has proven itself.
8. **Promote to production** last. This is deliberately *not* something the CI credential can do:
   the service account holds testing-track permission only, so `promoteObaGoogleReleaseArtifact`
   returns 403 for it and production promotion is a human action in the Play Console. If you do
   want to promote from a workstation, it needs a credential with production rights, and both
   tracks must be named explicitly — the defaults will otherwise promote a track onto itself:

       ./gradlew promoteObaGoogleReleaseArtifact --from-track beta --promote-track production

## Staged rollouts

An `inProgress` release is offered to only `user_fraction` of the track and can be halted from the
Play Console, so it is the required shape on `beta` and `production` — the workflow refuses a
`completed` release to either. Raising the fraction, halting, and resuming are all Console actions
on the existing release; none of them needs another build.

On a closed track a stage is usually pointless: the audience is small enough that 10% of it may be
nobody, and the closed track is already the staging mechanism.

Users do at least get an explanation on the first launch after an update, whichever track they are
on — `HelpViewModel.maybeAutoShowWhatsNew` shows the what's-new dialog once when the stored
`whatsNewVer` is below the running `VERSION_CODE`. That is an explanation after the fact, though,
not consent before it.

[playreadme]: ../onebusaway-android/src/oba/play/README.md

## Repository secrets

The workflow reads six secrets. They are set on the repository (Settings → Secrets and variables →
Actions) and are readable only by workflow runs.

| Secret | What it is | Where it comes from |
| --- | --- | --- |
| `RELEASE_KEYSTORE_BASE64` | The upload keystore, base64-encoded | `base64 -i onebusaway-android/joulespersecond-release-key.keystore \| tr -d '\n'` |
| `RELEASE_KEY_ALIAS` | Key alias inside that keystore | `key.alias` in `onebusaway-android/secure.properties` |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password | `key.storepassword` in the same file |
| `RELEASE_KEY_PASSWORD` | Key password | `key.keypassword` in the same file |
| `PELIAS_API_KEY_OBA` | Pelias geocoding key for the `oba` brand | `Pelias_oba` in `onebusaway-android/gradle.properties` |
| `ANDROID_PUBLISHER_CREDENTIALS` | Google Play service-account JSON | see below |

The keystore and `secure.properties` are gitignored and live only on release managers' machines and
in these secrets. **Losing the keystore is unrecoverable** if the app is not on Play App Signing —
keep an offline backup.

The workflow reconstructs `secure.properties` on the runner from the four signing secrets, points
Gradle at it with `-Psecure.properties=…`, and deletes both it and the decoded keystore in an
`if: always()` step at the end.

## The Play service account

`ANDROID_PUBLISHER_CREDENTIALS` is a Google Cloud service-account key with permission to publish
this app. gradle-play-publisher reads it straight from that environment variable (see the `play {}`
block in `onebusaway-android/build.gradle.kts`, which also accepts a `PLAY_STORE_JSON_KEY` property
naming a local file, for publishing from a workstation).

The current account is **`play-publisher@oba-android-publish.iam.gserviceaccount.com`**, in the
Google Cloud project `oba-android-publish`. To recreate it from scratch:

    gcloud projects create oba-android-publish --name="OBA Android Publishing"
    gcloud services enable androidpublisher.googleapis.com --project=oba-android-publish
    gcloud iam service-accounts create play-publisher \
      --project=oba-android-publish \
      --display-name="Google Play publisher (GitHub Actions)"
    gcloud iam service-accounts keys create key.json \
      --iam-account=play-publisher@oba-android-publish.iam.gserviceaccount.com \
      --project=oba-android-publish
    gh secret set ANDROID_PUBLISHER_CREDENTIALS -R OneBusAway/onebusaway-android < key.json
    rm key.json   # the secret is the only copy that needs to exist

The service account needs **no IAM roles in the Cloud project** — its permissions come from the Play
Console, not from Cloud IAM. Grant them there, once, by hand:

> Play Console → Users and permissions → Invite new users → enter the service-account email →
> under **App permissions** add OneBusAway (`com.joulespersecond.seattlebusbot`) → grant
> *View app information* and *Manage testing tracks* → Invite user.

Grant those two and **not** *Release manager* or *Manage production releases*. This credential sits
in a repository secret and is readable by any workflow run, so it should not be able to reach
production at all — CI's job ends at the testing tracks. That restriction is load-bearing rather
than decorative: it is the reason `promoteObaGoogleReleaseArtifact` returns 403 from CI, which is
what keeps production promotion a human decision made in the Console. Promoting from a workstation
needs a separate, more privileged credential.

To check that the credential works before trusting a release to it, run a read-only Play task
locally against the key:

    ./gradlew bootstrapObaGoogleReleaseListing -PPLAY_STORE_JSON_KEY=/path/to/key.json

It downloads the live store listing. A 401/403 means the Play Console invitation hasn't landed or
hasn't propagated yet — Google warns this can take **up to 36 hours**, so don't conclude the key is
wrong on the first failure. Review the diff rather than committing what bootstrap writes; it
overwrites the whole `src/oba/play` tree.

### Rotating the key

Service-account keys do not expire, so rotate on staff change or suspected exposure:

    gcloud iam service-accounts keys list --iam-account=play-publisher@oba-android-publish.iam.gserviceaccount.com
    # create a new key and set the secret as above, then delete the old one:
    gcloud iam service-accounts keys delete <KEY_ID> --iam-account=play-publisher@oba-android-publish.iam.gserviceaccount.com

## Publishing from a workstation

CI is the normal path. Publishing by hand — the same GPP tasks, plus the local `secure.properties`
and `PLAY_STORE_JSON_KEY` setup they need — is documented in [BUILD.md][build]; use it when CI is
unavailable or when you're promoting a track.

One trap worth repeating: **any** `assembleObaGoogleRelease` consults Play, not just the `publish*`
tasks, because `resolutionStrategy = AUTO` queries the current `versionCode` on every release
assemble. Without credentials the `play {}` block falls back to `IGNORE` and uses the `versionCode`
checked into `build.gradle.kts` — so the build succeeds, but the bundle it produces will collide
with Play's numbering and must not be uploaded.

[build]: BUILD.md#automated-publishing-with-gradle-play-publisher
