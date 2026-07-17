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

### `PrematureUnwrap` (warning)
The **elimination door** of the typed-time region, and the mirror of `UnwrappedClockValue`. After the
type-first migration the compiler owns everything *inside* the region — a cross-domain subtraction won't
compile — so the remaining job is to keep values from leaving the region too early. Fires when a
**domain-instant accessor read** — `ServerTime.epochMs` / `WallTime.epochMs` / `ElapsedTime.ms` — either
feeds arithmetic/comparison or comes to rest in a bare `Long`/`Int` slot, and stays silent when the read
is **passed straight through** as a call argument to a platform sink (formatting, `toPixelY`, an alarm) —
which is exactly where unwrapping is correct.

Scoped to the **instant** types only. `ScheduleTime`'s accessor already returns a typed
`kotlin.time.Duration`; schedule time reaches a bare `Long` only through `Duration`'s own eliminators
(`inWholeMilliseconds`, …), a domain-free stdlib quantity whose only hazard is units — a
Kotlin-ecosystem-wide concern, not this app's clock-domain discipline. Enrolling stdlib eliminators is a
**deliberate non-goal**: it would be noise far beyond that discipline, and is not to be re-litigated.

The check does **not** fire inside the domain package (`org.onebusaway.android.time`): that is where the
instant types *define* their own algebra, which necessarily touches the backing field (`minus` is
`(epochMs - other.epochMs).milliseconds`). Unwrapping to define the typed API is not "premature unwrap
into app logic" — the whole region the check guards is downstream of these definitions.

Implementation note: a Kotlin value-class property read (`serverTime.epochMs`) is a
`UQualifiedReferenceExpression`, not a getter call, and its receiver's UAST *type* is the inlined
`long` — so detection resolves the read and keys on the owner's `#property` (see
`TimeLintSupport.propertyKey`).

### `WireTimeEscape` (warning)
A virtual `internal` on the **wire→domain boundary**. The serialization DTOs in
`org.onebusaway.android.api.contract` keep their time fields as bare `Long`s by design; the adapter layer
mints them into the domain types (`ScheduleTime` / `ServerTime` / `Duration`) exactly once, at the one
place that knows which endpoint it is adapting. Fires when a curated wire time-field getter (e.g.
`StopTime.arrivalTime`, `TripStatus.serviceDate`, `ArrivalDeparture.predictedArrivalTime`) is read from a
file **outside** the adapter allowlist (`org.onebusaway.android.api.adapters`, `…api.data`). Read the
domain-typed model instead. New wire time fields are one line in `WIRE_TIME_FIELDS`.

Unlike the two clock checks there is deliberately **no pass-through exemption**: for a clock reading the
domain is a property of the *clock*, so minting can happen wherever it's read; for a wire field the *unit*
is a property of the *endpoint* — the two same-named `StopTime` (seconds) and `ScheduleStopTime` (epoch
millis) DTOs are the proof — so only the endpoint-aware adapter can mint correctly, and reading the raw
field elsewhere, even to pass it straight on, is the escape.

### `SwallowedCancellation` (warning)
Fires when `kotlin.runCatching` is called inside a `suspend` function. `runCatching` catches every
`Throwable`, so in a coroutine it also swallows the `CancellationException` a cancelled coroutine throws
from a suspend call — converting cancellation into an ordinary `Result.failure`, so the cancelled work
keeps running and callers read the cancellation as a normal error/empty state (structured concurrency
breaks). `CancellationException` is a plain `IllegalStateException` subtype, so nothing at the type level
catches this; it passes compilation and review and only misbehaves under real cancellation (screen closed,
search superseded by a keystroke, poll tick outrun). This is the #1908 / #1921 bug class.

Fix by using **`runCatchingCancellable`** (`org.onebusaway.android.util`) — behaviour-identical to
`runCatching` but it rethrows `CancellationException` (a real failure still resolves to `Result.failure`).
The wrapper is the single sanctioned path, so the check degrades to a symbol ban: any bare `runCatching`
in a suspend function is flagged. For a broad `try/catch (Exception)` in a suspend function, add a leading
`catch (e: CancellationException) { throw e }` — this check covers `runCatching` only.

Scope is the **nearest enclosing named function** being `suspend` (matching detekt's
`SuspendFunSwallowedCancellation`), detected via the `Continuation` parameter on its light method — so
`async { runCatching { … } }` inside a suspend function is caught (lambdas are transparent), while a
`runCatching` in a coroutine-builder lambda inside a *non-suspend* function is deliberately out of scope
(the boundary isn't visible without heuristics; guard those by hand). A `runCatching` around purely
non-suspending work in a suspend function is still flagged — the wrapper is a strict superset, so it's
never wrong, and the rule stays a simple ban rather than a fragile "does this lambda suspend" analysis.

## Lifecycle

Each check names its reason to exist and its reason to die. Regenerate the baseline after landing a
check, then only ever shrink it.

| Check                 | Guards                                    | Dies when                                             |
| --------------------- | ----------------------------------------- | ----------------------------------------------------- |
| `RawClockArithmetic`  | foreign clock namespace, in math          | never (can't own `java.lang`)                         |
| `UnwrappedClockValue` | foreign clock namespace, coming to rest   | never (can't forbid `Long`)                           |
| `PrematureUnwrap`     | the elimination door of the typed region  | a real module boundary makes `.epochMs` cross-module  |
| `WireTimeEscape`      | adapter-only consumption of wire DTOs     | `:api` becomes a Gradle module with `internal` DTOs   |
| `SwallowedCancellation` | `runCatching` swallowing cancellation in suspend code | Kotlin/lint ships an equivalent built-in check |

**Enrollment sweep — nothing left to enroll.** The plan anticipated adding the legacy Jackson
`io/elements` getters (`ObaArrivalInfo#getScheduledArrivalTime`, …) to `CLOCK_SOURCES`. That surface has
already been retired by the api-modernization (there is no `io/elements`), so its demolition condition is
already met; the wire DTOs it became are covered by `WireTimeEscape`, not `CLOCK_SOURCES`.

**Governance.** Suppressing any of these time checks (`@Suppress` / lint `//noinspection` / a baseline
entry added by hand) requires the same one-line-rationale-plus-tracking-issue standard as `@Suppress` in
CLAUDE.md. The checks are a latch discipline, not a suggestion.

## Status: enforced

Wired into the app via `lintChecks(project(":lint-rules"))` in `onebusaway-android/build.gradle.kts`, so a
**new** violation of any of the five issues fails the build under the strict `-PwarningsAsErrors` gate
(verified). There is **no lint baseline** — the app is kept clean under the full catalog, so every
sanctioned pre-existing site is handled *at the site*, not grandfathered in a file:

- **0** `RawClockArithmetic` / `UnwrappedClockValue` baselined. The sites that once were: the genuine
  #1612 candidate `ReminderUtils` (`departTime − System.currentTimeMillis()`) was fixed by threading the
  clock to the call site (`getReminderTimes` now takes a same-domain `nowMs` param and reads no clock
  itself); the remaining same-domain / sanctioned device-clock timers and conversion helpers carry an
  inline `@Suppress("RawClockArithmetic")` / `@Suppress("UnwrappedClockValue")` with a one-line rationale.
- **0** `PrematureUnwrap` — a skeptical pass retired every one of the 18 initial findings rather than
  baselining them: the `TypedTime` operators are now rule-exempt (the domain defining its own algebra);
  the `.epochMs > 0` "is this instant set / did the server predict" sentinels became **nullable**
  instants (`TripState.anchorTimeMs`/`anchorLocalTimeMs`, `ArrivalData.predicted*Time`) decoded at the
  boundary; and only the genuinely-irreducible server↔device crossings (the `TripState` skew bridge,
  `TripMonitorDecider.hasDeparted`) remain, as inline `@Suppress("PrematureUnwrap")` with a rationale —
  visible at the site, not hidden in this file. Keep it empty.
- **0** `WireTimeEscape` — the migration left no wire-field read in app logic; the check starts empty and
  exists to keep it that way.
- **0** `SwallowedCancellation` — every bare `runCatching` in a suspend function was migrated to
  `runCatchingCancellable` (#1908 / #1921 audit); the check starts empty and keeps new coroutine code on
  the sanctioned wrapper.

Keep all five checks at zero: mint each new site (or inline-`@Suppress` a genuinely-sanctioned one with a
rationale) — don't reintroduce a baseline.

## Develop

```bash
./gradlew :lint-rules:test        # run the detector unit tests (lint-tests harness, no device)
```
