# Releasing OneBusAway for Android

Releases go out through the **Release to Google Play** workflow
(`.github/workflows/release.yml`), which builds a signed `obaGoogle` release App Bundle and uploads
it with [gradle-play-publisher][gpp]. The default track is **beta** — Play's open testing channel —
and production releases are promotions of a beta that has been out long enough to be trusted.

The workflow is **manual dispatch only**. Publishing is outward-facing and hard to undo, so nobody
ships by pushing a branch or mistyping a tag; a human opens Actions and presses Run workflow.

[gpp]: https://github.com/Triple-T/gradle-play-publisher

## Cutting a release

1. **Pick the commit.** It should be a commit on `main` that is green in
   [Android CI Build](../.github/workflows/android.yml). The release workflow does not re-run the
   test suite — it takes ~20 minutes and needs an emulator — though the release build is itself a
   real compile gate, since it runs R8 and resource shrinking that the debug CI path never touches.
2. **Set `versionName`** in `onebusaway-android/build.gradle.kts`. This is the only version field
   you edit by hand: `versionCode` is auto-incremented from the highest code already on Play (the
   `resolutionStrategy = AUTO` in the `play {}` block), so the number checked into the file is only
   a fallback for builds without Play credentials.
3. **Write the release notes**, in two places that must agree:
   - `onebusaway-android/src/oba/play/release-notes/en-US/default.txt` — the Play "What's new"
     text, capped at 500 characters per locale. See the [README][playreadme] there for why only
     `en-US` is checked in.
   - `main_help_whatsnew` in `onebusaway-android/src/main/res/values/strings.xml` (and the
     `values-*` translations) — the in-app what's-new dialog. Same release, same audience.
4. **Run the workflow.** Actions → Release to Google Play → Run workflow, on the commit you picked.
   - `track`: `beta` for a normal release. `internal` is the safe choice when you're testing the
     pipeline itself.
   - `release_status`: `completed` rolls the release out on that track. `draft` uploads the bundle
     but leaves it unpublished in the Play Console for a human to review and roll out — use it for
     the first run after any change to the signing or credential setup.
5. **Tag it.** Once the upload succeeds, tag the released commit and push the tag, so the bundle on
   Play maps to a commit:

       git tag -a v26.1.0 -m "26.1.0" <commit>
       git push origin v26.1.0

6. **Promote to production** when the beta has settled:

       ./gradlew promoteObaGoogleReleaseArtifact

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
> **Release manager** (or, more narrowly, *View app information*, *Manage testing tracks*, and
> *Manage production releases*) → Invite user.

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
