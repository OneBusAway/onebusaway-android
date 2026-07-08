# lint-rules

Custom [Android Lint](https://developer.android.com/studio/write/lint) checks for onebusaway-android.
A plain JVM module: checks are UAST detectors packaged into a jar; nothing here runs on device.

## Checks

### `RawClockArithmetic` (warning)
Fires when arithmetic or a comparison has an operand that is a **raw time reading** — a direct call to
a known time producer — instead of a value minted into one of the domain types in
`org.onebusaway.android.time` (`ServerTime` / `WallTime` / `ElapsedTime`). Recognized producers:

- device clocks: `System.currentTimeMillis()`, `System.nanoTime()`,
  `SystemClock.elapsedRealtime()` / `elapsedRealtimeNanos()` / `uptimeMillis()` /
  `currentThreadTimeMillis()`
- location fix times: `Location.getTime()` / `getElapsedRealtimeNanos()`
- object-carried epochs: `Date.getTime()`, `Calendar.getTimeInMillis()`, `Instant.toEpochMilli()` /
  `getEpochSecond()`, `Clock.millis()`, `{Zoned,Offset,Local}DateTime.toEpochSecond()`

New producers are one line in `CLOCK_SOURCES`.

### `UnwrappedClockValue` (warning)
Fires when a producer reading comes to **rest in a bare `Long`/`Int` slot** — a local, a
field/property, a return value, or a default parameter — instead of a domain type. This is the
capture-at-birth companion to `RawClockArithmetic`: rather than trace a bare `Long` one hop (or N hops)
to wherever it's later used in arithmetic, it forbids the reading from resting untyped in the first
place. If a time can't rest in a bare `Long`, there's no untyped time downstream to trace — so
binding-capture subsumes local-variable tracing entirely.

A reading **passed straight through** to a consuming API — a DAO/prefs write, an alarm, a domain-type
constructor — is deliberately **not** flagged: the value never rests, so no clock domain is lost. Only
resting untyped is the problem.

It's the lint complement to `TypedTime.kt`: the value classes make *new typed* code safe by
construction (a cross-domain subtraction won't compile); this check closes the gap where a raw clock
call never got minted and flows straight into math — the #27 / #1612 bug class
(`serverTimestamp − deviceNow`). A bare `Long` has no domain, so the check deliberately keys on the
*boundary* clock calls rather than trying to guess the domain of an already-laundered `Long`.

Fix at each site by minting (`WallTime.now()`, `ElapsedTime.now()`, `ServerTime(...)`) and doing the
math through the typed API — or, for a genuinely sanctioned device-clock local timer, wrap it anyway so
the domain is explicit, or suppress with a rationale.

## Status: enforced

Wired into the app via `lintChecks project(':lint-rules')` in `onebusaway-android/build.gradle`, so a
**new** violation of either issue fails the build under the strict `-PwarningsAsErrors` gate (verified).
The pre-existing sites present when the checks landed are grandfathered in
`onebusaway-android/lint-baseline.xml` — **13** `RawClockArithmetic` + **11** `UnwrappedClockValue`.
Nearly all are same-domain / sanctioned device-clock timers or conversion helpers; one —
`ReminderUtils.java:86` (`departTime − System.currentTimeMillis()`, a scheduled/predicted departure
minus device now) — is a genuine #1612 candidate flagged for a dedicated fix (it needs a server clock
threaded to the call site). Drive the baseline down by minting each site; don't add new entries.

## Develop

```bash
./gradlew :lint-rules:test        # run the detector unit tests (lint-tests harness, no device)
```
