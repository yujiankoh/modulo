# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository context

This directory (`app/`) is the **Android client** of the MODULO monorepo. The git repo
root is one level up (`../`), and also contains:

- `../web/` — a parallel JavaScript web client (vanilla HTML/CSS/JS, no framework)
- `../server/` — an Express proxy (`server/index.js`) that calls the Gemini Vision API
  for timetable parsing
- `../docs/` — **shared** documentation read by both clients, most importantly
  `../docs/modulo-data-schema.md` (the `modulo-data.json` contract) and
  `../docs/testing.md` (web/proxy testing approach)
- `../tests/` — Node test suite for `web/` and `server/` (`npm test` from repo root)

The Android app and the web app are independent implementations of the **same product**
and read/write the **same synced JSON file** (`modulo-data.json`, stored in Google Drive's
`appDataFolder` or locally). When changing anything in `AppData.kt` or the other
`@Serializable` model classes, check `../docs/modulo-data-schema.md` first — it is the
source of truth for the shape both clients must agree on, and it has a running change log.
kotlinx.serialization is configured with `ignoreUnknownKeys = true` (see `syncJsonParser`
in `AppData.kt`) specifically so each client tolerates fields the other has added.

Note: `../docs/modulo-data-schema.md` describes some fields (`handbookId` defaulting to a
generated UUID, `handbookSetup`) that this Kotlin app does not yet implement identically —
treat the doc as the aspirational contract and the Kotlin code as ground truth for what
Android currently does.

## Commands

All commands below are run from this directory (`app/`), which contains `gradlew`/`gradlew.bat`.

```bash
# Build the debug APK
./gradlew.bat build

# Run all JVM unit tests (app/src/test)
./gradlew.bat test

# Run a single test class
./gradlew.bat testDebugUnitTest --tests "com.example.modulo.GpaHelperTest" --console=plain

# Run a single test method
./gradlew.bat testDebugUnitTest --tests "com.example.modulo.AppViewModelTest.someTestName" --console=plain

# Run several test classes together
./gradlew.bat testDebugUnitTest --tests "com.example.modulo.SystemFlowTest" --tests "com.example.modulo.TimetableIntegrationTest" --console=plain

# Instrumented tests (app/src/androidTest, needs an emulator/device)
./gradlew.bat connectedAndroidTest
```

There is no lint/format command configured beyond Android Studio's built-in inspections.

## Architecture

### Single source of truth: `AppViewModel`

`AppViewModel.kt` is the app's only `ViewModel` and owns all app state as `StateFlow`s
(`appData`, `syncState`, `startupState`, `timetableState`, plus timer state for study
sessions). There is no repository layer or dependency-injection framework — the ViewModel
directly constructs its helper classes (`LocalSaveHelper`, `NetworkHelper`) and lazily
constructs `SyncingHelper` once a user signs in. All mutation of `AppData` goes through the
private `updateData { currentData -> ... }` function, which:

1. Applies the transform and stamps a fresh `updatedAt`.
2. Persists to local disk via `LocalSaveHelper` (always, synchronously).
3. If Drive sync is enabled and there's internet, debounces (1s) then uploads to Drive.

When adding a new mutation (e.g. a new "upsert X" function), follow the existing pattern:
one public function per operation, going through `updateData`, mirroring `addTask` /
`upsertGrade` / `saveHandbook` etc. — don't bypass `updateData` directly on `_appData`.

### Startup flow (`StartupState`)

App boot is a state machine driven by `_startupState`, checked in `startUpChecks()`:
`LOADING → TUTORIAL → SIGN_IN → AUTHENTICATE → HANDBOOK → READY` (see the `StartupState`
enum in `AppData.kt`). `NavigationController.kt`'s `RootNavigation` observes this flow via
`LaunchedEffect(startupState)` and drives actual navigation — it is the only place that
reacts to `startupState` changes. Local-save-only users skip Google auth entirely; Drive
users go through `AuthenticationHelper` (Credential Manager sign-in + separate Drive
`appdata` scope authorization) before syncing.

### Sync model (`SyncState`)

`OFFLINE → UNSYNCED → SYNCING → SYNCED`. Conflict resolution (`resolveConflict` in
`AppViewModel`) is **last-write-wins by `updatedAt` timestamp**, compared between the local
file and the Drive file — whichever is newer replaces the other. `NetworkHelper` exposes
both a one-shot `isConnected()` check and a `Flow<Boolean>` used by `autoSync()` to
re-trigger sync when connectivity returns.

### Handbooks = one semester's data

A `Handbook` bundles everything scoped to a single semester (education level, academic
year, term dates, breaks, tasks, grades, hidden modules, timetable). `AppData`'s top-level
fields are always the **active** handbook; `otherHandbooks` holds the rest. Switching
(`swapHandbook`) snapshots the current flat fields into `otherHandbooks` and promotes the
target handbook's fields to the top level — it's one atomic `updateData` call, not two.
`studySessions` and `city` are **global** — they deliberately live outside the handbook
object and must never be touched by a handbook switch (see the schema doc's "Global"
callouts) — this is why `saveSession`/`reconcileCity` mutate them directly on `AppData`
rather than through any handbook-scoped path.

### Helpers (`helpers/` package)

Each helper is a narrow, mostly-stateless wrapper around one concern, constructed by
`AppViewModel` rather than injected via a DI framework:

- `LocalSaveHelper` — reads/writes `modulo-data.json` to app-private internal storage.
- `SyncingHelper` — Drive REST calls (`files.list`/`create`/`update`/`get` scoped to
  `appDataFolder`) for the same filename. Constructed per-session via
  `SyncingHelper.getSyncService(context, email)` once a user is authenticated.
- `AuthenticationHelper` — Credential Manager Google sign-in, then a *separate*
  Play Services `Identity.getAuthorizationClient` step to grant the Drive `appdata`
  scope (two distinct steps: authenticate, then authorize).
- `NetworkHelper` — connectivity polling via `ConnectivityManager`.
- `ParsingHelper` — POSTs a base64-encoded timetable photo to the Node proxy
  (`../server/index.js`, deployed at the hardcoded `PROXY_URL`) and parses the JSON
  `Timetable` response. Injected into `AppViewModel` via constructor default
  (`ParsingHelper = ParsingHelper()`) specifically so tests can substitute a mock —
  see `AppViewModelTestBase`.
- `CityLogicHelper` — pure functions for the gamified "study city" grid (upgrade
  earning/placement math). Deliberately has no Android dependencies so it's trivially
  unit-testable; mirrors `../web/logic/growth.js`.
- `GpaHelper` — pure GPA computation per education-level grading scheme; mirrors
  `../web/logic/gpa.js`. Any change to rounding/exclusion rules must stay in sync with
  the web implementation and the schema doc's "Grade object" section.

### Navigation (`navigation/NavigationController.kt`)

Type-safe Navigation Compose using `@Serializable object`/`data class` route definitions
(no string routes). Three nested graphs: `StartupGraph` (onboarding/auth), a flat set of
"global" screens registered directly on the root `NavHost` (settings, GPA, timetable,
handbook create/edit — reachable from multiple places), and `AppGraph` (the bottom-tab
app shell with its own nested `NavHost` for Home/Calendar/AddTask/AllTasks/StudySession).
`NavigationBottomBar.kt`'s `navigateBottom` extension implements the standard
single-top/restore-state pattern for tab switches.

### Testing conventions (`app/src/test`)

Unit tests subclass `AppViewModelTestBase` (in turn using `MainDispatcherRule` to swap in
`UnconfinedTestDispatcher`), which mocks `Application`, `SavedStateHandle`,
`ConnectivityManager`, and the DataStore-backed prefs via MockK, and constructs a real
`AppViewModel` with an injected mock `ParsingHelper`. Override `buildPrefs()` or
`networkConnected` in a subclass to change onboarding/connectivity state; override
`onBeforeViewModelCreated()`/`onTearDown()` for extra mocking (e.g. mocking
`AuthenticationHelper`). Tests are organized by scope: `*HelperTest`/`ModelUnitTest`/
`SerializationUnitTest` for pure logic and model (de)serialization, `*IntegrationTest` for
a helper plus the ViewModel together, and `SystemFlowTest` for full multi-step user
journeys through `AppViewModel` end to end. `testOptions.unitTests.isReturnDefaultValues =
true` is set in `app/build.gradle.kts` so un-mocked Android framework calls return defaults
instead of throwing.
