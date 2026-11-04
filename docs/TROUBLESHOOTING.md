# Troubleshooting

Having trouble building the project? Check the known issues and fixes below. If you're a user of
the app, see our [FAQ](FAQ.md) instead.

### Gradle fails with "Unsupported class file major version" or asks for a newer Java

Gradle needs **JDK 17 or newer** to run. Check which one it's using with `./gradlew --version`. On
the command line, point `JAVA_HOME` at a suitable JDK. In Android Studio, set it under
**Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**. The bundled
"jbr" JDK works.

### Gradle can't find the Android SDK, or a platform is missing

Make sure Android Studio and the command line use the same SDK. Android Studio stores the SDK
location in `local.properties` (`sdk.dir=…`) at the project root. On the command line, set
`ANDROID_HOME` to that same directory. If the build says a platform isn't installed, install the
`compileSdk` level from [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) through
**Tools → SDK Manager**.

### Unresolved `R.*` references or KSP errors that make no sense

A stale build cache can leave generated code (`R` classes, Hilt/Room KSP output) inconsistent with
the source. Clean, then rebuild without the cache:

```bash
./gradlew clean
./gradlew installObaGoogleDebug --no-build-cache
```

Don't run two Gradle builds in the same checkout at once. They share one `build/` directory and can
corrupt each other's generated output.

### My PR passed locally but fails in CI

First run the checks in the
[Contributing Guide](../.github/CONTRIBUTING.md#before-you-open-a-pull-request). If those pass, keep
in mind that CI compiles your branch **merged into the latest `main`**. An error you can't reproduce
locally often comes from something that landed on `main` after you branched, so rebase and build
again.

### The build is slow

Build the one variant you need (`installObaGoogleDebug`) rather than `assemble`, which builds
every brand and platform. Skip lint (5–15 minutes) unless you need it.
