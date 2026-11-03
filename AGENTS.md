# AGENTS.md

Android SMS client (`com.sms.messages`) — Kotlin, Jetpack Compose, Room, minSdk 26,
targetSdk 35.

## Build

The Gradle daemon is enabled; keep it warm. Do not pass `--no-daemon`.

```bash
bash tools/gbuild.sh :app:assembleDebug :app:testDebugUnitTest
```

`tools/gbuild.sh` runs Gradle with `--parallel --build-cache`, waits for the log to
stop changing using `inotifywait` (no fixed sleeps), then prints only the error,
warning and task-failure lines. Override the log path with `LOG=`.

Environment expected by the scripts:

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export GRADLE_USER_HOME=$HOME/.gradle
```

## Check the whole project before building

```bash
bash tools/audit.sh
```

One pass over the entire tree: imports referenced nowhere, icons used without an
import, `R.string` names missing from `values/`, English strings missing from
`values-ar/`, strings defined but never referenced, hardcoded UI text, Compose icons
that do not exist in the icon jars, and `R.drawable` references with no file.

Run this and fix everything it reports **before** starting a build. Batch the edits —
do not rebuild after each individual file.

## Conventions

- All user-visible text goes through `stringResource(R.string.…)`. Adding a string to
  `values/strings.xml` means adding it to `values-ar/strings.xml` too;
  `LocalizationTest` fails otherwise.
- Compose icons: `Icons.AutoMirrored.Filled.*` for directional glyphs. `Unblock` does
  not exist — use `LockOpen`. Verify against the icon jars rather than guessing.
- `PhoneNumbers.normalize` collapses local, `+966` and `00` forms to E.164 so one
  contact maps to one conversation.
- `SmsRepository` is the single entry point for conversation bookkeeping. Receivers,
  schedulers and screens all go through it.
- Database bootstrap is EF-style `EnsureCreated` plus idempotent migration services —
  there are no Room migrations to write for schema changes.
- Keep comments for non-obvious invariants only.

## Tests

`app/src/test/` — plain JVM JUnit tests (phone normalisation, time formatting,
resource parity). `testOptions.unitTests.isReturnDefaultValues = true` is set, so
`android.*` calls in unit tests return defaults rather than throwing.
