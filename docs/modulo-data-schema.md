# MODULO — Data Schema (`modulo-data.json`)

The agreed shape of MODULO's data. **Both the web (JavaScript) and the app (Kotlin)
read and write this same file**, so they must follow this contract exactly — same
field names, same types. If the two sides disagree on a field name, sync silently
breaks even though the file transfers fine.

- **File name:** `modulo-data.json`
- **Location:** Google Drive `appDataFolder` (and local device storage for local-only mode)
- **Format:** a single JSON object holding the entire app state
- **Current schema version:** `2`

> Rule of thumb when changing this: **adding** a new optional field is safe (old files
> still load). **Renaming or removing** a field requires updating web *and* app together.
> Always read defensively — default missing fields rather than assuming they exist.

---

## Full example

```json
{
  "schemaVersion": 2,
  "educationLevel": "university",
  "academicYear": "25/26",
  "semester": 2,
  "handbookSetup": true,
  "termStart": "2026-05-18",
  "termEnd": "2026-08-07",
  "breaks": [{ "start": "2026-06-29", "end": "2026-07-05" }],
  "updatedAt": "2026-06-03T09:35:06.606Z",
  "tasks": [
    {
      "id": 1717059306606,
      "module": "CS1101S",
      "title": "CS2040S tutorial",
      "due": "2026-06-10",
      "type": "tutorial",
      "done": false,
      "createdAt": "2026-06-03T09:30:00.000Z",
      "updatedAt": "2026-06-03T09:35:06.606Z"
    }
  ],
  "studySessions": [
    {
      "id": "9f1c3b2a-5e44-4c8a-9b1d-2f0a7e6d4c11",
      "start": "2026-06-24T13:00:00.000Z",
      "end": "2026-06-24T13:50:00.000Z",
      "durationMins": 50,
      "rating": 4,
      "createdAt": "2026-06-24T13:50:02.140Z"
    }
  ],
  "timetable": {
    "educationLevel": "university",
    "modules": [
      {
        "code": "CS2030S",
        "name": "",
        "slots": [
          { "day": "MON", "start": "12:00", "end": "14:00", "location": "E-Learning", "sessionType": "lecture", "classNo": "1", "week": "all" }
        ]
      }
    ]
  }
}
```

---

## Top-level fields

| Field            | Type              | Required | Description |
|------------------|-------------------|----------|-------------|
| `schemaVersion`  | number            | yes      | The structure version. Currently `2`. Bump when the structure changes. |
| `educationLevel` | string \| null    | yes      | The user's level: `primary`, `secondary`, `jc`, `poly`, `university`, or `null` if unset. Drives how the timetable parser reads the image. **Set once during handbook onboarding, then locked** (immutable) — it determines the timetable schema/editor rules. |
| `academicYear`   | string \| null    | no       | Academic year (Phase 13 handbook). **Format is level-aware:** tertiary (`university`/`poly`) spans two years → `"YY/YY"` e.g. `"25/26"`; school (`primary`/`secondary`/`jc`) is a single calendar year → `"YYYY"` e.g. `"2026"`. Drives the sidebar "HANDBOOK · …" header (rendered per level). `null` until onboarding. Web-only for now. |
| `semester`       | number \| null    | no       | Current semester, `1` or `2` (Phase 13 handbook). Drives the sidebar header. `null` until onboarding. Web-only for now. |
| `handbookSetup`  | boolean           | no       | `true` once the user has completed handbook onboarding (Phase 13). Gates the first-run setup modal. Defaults `false`; pre-Phase-13 files (no flag) are treated as set up iff `educationLevel` is already chosen. Web-only for now. |
| `termStart`      | string (`YYYY-MM-DD`) \| null | no | Week-1 Monday anchor for the dated weekly timetable view. `null` until the user sets it. Used to map the recurring weekly timetable onto real dates + derive odd/even week parity. Web-only for now; the app may ignore it. |
| `termEnd`        | string (`YYYY-MM-DD`) \| null | no | Last day of term. Weeks after it (and before `termStart`) are "outside term" — shown with dates but no week number/classes. `null` until set. Web-only for now. |
| `breaks`         | array of `{ start, end }` (each `YYYY-MM-DD`) | no | Recess/holiday date ranges. Any week overlapping a range is non-academic: skipped in week numbering (so parity continues across it) and shows no classes. Defaults to `[]`. Web-only for now. |
| `updatedAt`      | string (ISO 8601) | yes      | When the whole state was last saved. Key field for sync + local-save reconciliation. |
| `tasks`          | array of Task     | yes      | All of the user's tasks. May be empty (`[]`). |
| `studySessions`  | array of StudySession | no   | Recorded focus/study sessions (Phase 10). Defaults to `[]`. Used for daily/weekly/cumulative study-time totals + the calendar's per-day average rating; feeds the MS3 study game. |
| `hiddenModules`  | array of string   | no       | Module labels the user has hidden from the web dashboard + sidebar (Phase 12). Defaults to `[]`. Web-only (the app may ignore it). |
| `timetable`      | Timetable \| null | yes      | The parsed timetable, or `null` if none yet. |

## Task object

| Field       | Type              | Required | Description |
|-------------|-------------------|----------|-------------|
| `id`        | number            | yes      | Unique id (web uses `Date.now()`). |
| `module`    | string            | yes      | The Module `code`, or Module `name` for Primary, Secondary and JC. |
| `title`     | string            | yes      | Task name. |
| `due`       | string (`YYYY-MM-DD`) \| `""` | yes | Due date, empty if none. |
| `type`      | string (enum)     | yes      | One of: `assignment`, `tutorial`, `quiz`, `exam`. |
| `done`      | boolean           | yes      | Whether completed. |
| `createdAt` | string (ISO 8601) | yes      | When created. |
| `updatedAt` | string (ISO 8601) | yes      | When last changed. |

## StudySession object

One recorded focus/study session. Stored at a fine granularity (one row per session) so
daily / weekly / cumulative totals can be **summed** later and the per-day average rating
**averaged** — totals are never pre-stored, always derived from these rows.

| Field          | Type                | Required | Description |
|----------------|---------------------|----------|-------------|
| `id`           | string              | yes      | Unique id (web uses `crypto.randomUUID()`). |
| `start`        | string (ISO 8601)   | yes      | When the session began. Its date is what groups a session into a day/week. |
| `end`          | string (ISO 8601)   | yes      | When the session ended. |
| `durationMins` | number              | yes      | Minutes actually studied (excludes paused time). Summed for totals. |
| `rating`       | number (1–5) \| null| yes      | User's self-rating of the session, or `null` if skipped. The calendar shows each day's rounded **average** of non-null ratings. (Will be shown as emoji later; the stored value stays 1–5.) |
| `createdAt`    | string (ISO 8601)   | yes      | When the record was saved. |

---

## Timetable object (FINALIZED — level-aware)

One unified structure across all education levels. The PARSER fills the fields relevant
to the level and leaves the rest as `""`; the STORAGE shape is identical for every level.
"module" = a subject for school levels, a module/course for tertiary levels.

```json
"timetable": {
  "educationLevel": "university",
  "modules": [
    {
      "code": "CS2030S",
      "name": "Programming Methodology II",
      "slots": [
        {
          "day": "MON",
          "start": "12:00",
          "end": "14:00",
          "location": "COM4-02-04",
          "sessionType": "lecture",
          "classNo": "1",
          "week": "all"
        }
      ]
    }
  ]
}
```
| Field (Timetable) | Type   | Description |
|-------------------|--------|-------------|
| `modules`         | array of Module | All subjects taken in this semester. |


| Field (Module) | Type   | Description |
|----------------|--------|-------------|
| `code`         | string | Module code (e.g. "CS2030S"). Empty `""` for primary/secondary/jc. |
| `name`         | string | Subject or module name. May be `""` if not visible in the image. |
| `slots`        | array  | The recurring sessions for this module/subject. |

| Field (slot)  | Type   | Description |
|---------------|--------|-------------|
| `day`         | string | `MON`–`SUN`. |
| `start`       | string | Start time, `HH:MM` (24h). |
| `end`         | string | End time, `HH:MM` (24h). |
| `location`    | string | Venue. May be `""`. |
| `sessionType` | string | Normalized category — see per-level values below. |
| `classNo`     | string | Class/group number (e.g. "1", "31B"). `""` for school levels. |
| `week`        | string | Which weeks this session runs: `"all"` (every week — the default), `"odd"`, or `"even"`. For alternating-week timetables. Read defensively: treat a missing `week` as `"all"`. |

### Which fields each level populates

| Field         | Primary | Secondary | JC | Poly | Uni |
|---------------|---------|-----------|----|----|-----|
| `code`        | —       | —         | —  | ✓  | ✓   |
| `name`        | ✓       | ✓         | ✓  | ✓  | ✓   |
| `sessionType` | lesson / cca | lesson / cca | lecture / tutorial | lecture / tutorial / lab / practical | lecture / tutorial / lab / recitation / seminar |
| `classNo`     | —       | —         | (if shown) | ✓ | ✓ |
| `location`    | (if shown) | (if shown) | ✓ | ✓ | ✓ |
| `week`        | all/odd/even | all/odd/even | all/odd/even | all/odd/even | all/odd/even |

> `color` for module colour-coding is assigned client-side in the UI, not by the parser,
> so it is not part of this contract.

> `week` defaults to `"all"` and only differs when a timetable has an odd/even split. Sec/JC
> usually publish **two separate** Odd/Even Week images (parse each, slots tagged from the title);
> Uni/Poly use **per-cell labels** in one image (e.g. "Even Weeks", odd week-number lists). Week
> ranges / specific-week lists are collapsed to `all`/`odd`/`even` (finer granularity isn't stored).

---

## Time fields, sync, and local save

Two storage locations: Google Drive `appDataFolder` (when linked) and local device storage
(local-only mode). The top-level `updatedAt` makes them reconcilable:
- **Last-write-wins**: each save overwrites the whole file; `updatedAt` records when.
- **On open, load fresh** so you're not editing a stale copy.
- **If both a local and a Drive copy exist**, compare top-level `updatedAt`, keep the newer.

Per-task `createdAt`/`updatedAt` aren't needed for basic sync; they're there for possible
finer-grained merging later. Do NOT build conflict resolution yet — last-write-wins is fine.

---

## Reference implementations

### Web (JavaScript) — in-memory state

```javascript
let appState = {
  schemaVersion: 2,
  educationLevel: null,   // "primary" | "secondary" | "jc" | "poly" | "university" — set once, then locked
  academicYear: null,     // level-aware: "YY/YY" (uni/poly) or "YYYY" (school) — Phase 13 handbook
  semester: null,         // 1 | 2 (Phase 13 handbook) — sidebar header
  handbookSetup: false,   // true once onboarding done (Phase 13) — gates first-run modal
  termStart: null,        // "YYYY-MM-DD" Week-1 Monday anchor, or null
  termEnd: null,          // "YYYY-MM-DD" last day of term, or null
  breaks: [],             // ["YYYY-MM-DD", ...] recess/holiday week Mondays
  updatedAt: null,
  tasks: [],
  studySessions: [],      // [{ id, start, end, durationMins, rating, createdAt }] — Phase 10
  hiddenModules: [],      // module labels hidden from the dashboard/sidebar (web-only) — Phase 12
  timetable: null,        // { educationLevel, modules: [...] }
};
```

### App (Kotlin) — matching `@Serializable` data classes

Field names **must match the JSON keys exactly**. Defaults make reading old/partial files safe.
Use `Json { ignoreUnknownKeys = true }`.

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class ModuloData(
    val schemaVersion: Int = 2,
    val educationLevel: String? = null,
    val academicYear: String? = null,  // level-aware: "YY/YY" (uni/poly) or "YYYY" (school) (Phase 13; web-only)
    val semester: Int? = null,         // 1 or 2 (Phase 13 handbook; web-only for now)
    val handbookSetup: Boolean = false, // true once web onboarding done (Phase 13; web-only for now)
    val termStart: String? = null,     // "YYYY-MM-DD" Week-1 Monday anchor (web-only for now)
    val termEnd: String? = null,       // "YYYY-MM-DD" last day of term (web-only for now)
    val breaks: List<Break> = emptyList(), // recess/holiday date ranges (web-only for now)
    val updatedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val studySessions: List<StudySession> = emptyList(), // Phase 10; default keeps old files loading
    val hiddenModules: List<String> = emptyList(),       // Phase 12; web UI preference (hidden module labels)
    val timetable: Timetable? = null
)

@Serializable
data class StudySession(
    val id: String,
    val start: String,          // ISO 8601
    val end: String,            // ISO 8601
    val durationMins: Int,      // minutes studied (excludes paused time)
    val rating: Int? = null,    // 1–5, or null if skipped
    val createdAt: String? = null
)

@Serializable
data class Break(
    val start: String,   // "YYYY-MM-DD"
    val end: String      // "YYYY-MM-DD"
)

@Serializable
data class Task(
    val id: Long,
    val module: String = "",       // module code, or name for primary/secondary/jc
    val title: String,
    val due: String = "",
    val type: String,              // assignment | tutorial | quiz | exam
    val done: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class Timetable(
    val educationLevel: String = "",
    val modules: List<Module> = emptyList()
)

@Serializable
data class Module(
    val code: String = "",
    val name: String = "",
    val slots: List<Slot> = emptyList()
)

@Serializable
data class Slot(
    val day: String,
    val start: String,
    val end: String,
    val location: String = "",
    val sessionType: String = "",  // lecture | tutorial | lab | recitation | seminar | practical | lesson | cca
    val classNo: String = "",
    val week: String = "all"       // all | odd | even  (default all; for alternating-week timetables)
)
```

---

## Change log

| Version | Date       | Change |
|---------|------------|--------|
| 1       | 2026-05-30 | Initial schema: `tasks`, provisional `timetable`, time fields. |
| 2       | 2026-06-03 | Added `educationLevel`. Finalized the level-aware `timetable` structure: `slots` now use `sessionType` + `classNo` + `teacher` (replacing the provisional `type`). |
| 2       | 2026-06-16 | Dropped the unused `teacher` field from `Slot` — the proxy never emitted it and we decided not to capture teacher info; `location` is the room/venue only. `schemaVersion` stays 2. Ling Song informed. |
| 2       | 2026-06-18 | Added `week` (`all`/`odd`/`even`, default `all`) to `Slot` for alternating-week timetables. Additive + defaulted, so old files still load; `schemaVersion` stays 2. |
| 2       | 2026-06-18 | Added `module` to `Task` (the module code, or name for primary/secondary/jc, that the task belongs to). |
| 2       | 2026-06-23 | Added top-level `termStart` (`YYYY-MM-DD` Week-1 Monday, default `null`) for the dated weekly timetable view + odd/even parity (Phase 8). Additive + defaulted, so old files still load; `schemaVersion` stays 2. Web-only for now; app may ignore it. |
| 2       | 2026-06-23 | Added top-level `termEnd` (`YYYY-MM-DD`, default `null`) and `breaks` (recess/holiday `{ start, end }` date ranges, default `[]`) for term bounds + non-academic weeks (Phase 8). Any week overlapping a break range is skipped in numbering. Additive + defaulted; `schemaVersion` stays 2. Web-only for now. |
| 2       | 2026-06-24 | Added top-level `studySessions` (array of StudySession `{ id, start, end, durationMins, rating, createdAt }`, default `[]`) for the Phase 10 study timer. Feeds daily/weekly/cumulative study-time totals + the calendar's per-day average rating (and the MS3 study game). Additive + defaulted, so old files still load; `schemaVersion` stays 2. **Ling Song: heads-up — additive contract change.** |
| 2       | 2026-06-25 | Added top-level `hiddenModules` (array of strings, default `[]`) — module labels the web app hides from the dashboard + sidebar (Phase 12). Additive + defaulted; `schemaVersion` stays 2. Web-only (app may ignore). |
| 2       | 2026-06-27 | Added top-level `academicYear` (string `"YY/YY"`, default `null`), `semester` (number `1`/`2`, default `null`), and `handbookSetup` (boolean, default `false`) for the Phase 13 handbook/onboarding. Drive the sidebar header + gate the first-run setup modal. Additive + defaulted, so old files still load; `schemaVersion` stays 2. Web-only for now. **Ling Song: heads-up — additive contract change.** |
