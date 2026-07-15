# MODULO - Data Schema (`modulo-data.json`)

The agreed shape of MODULO's data. **Both the web (JavaScript) and the app (Kotlin)
read and write this same file**.

- **File name:** `modulo-data.json`
- **Location:** Google Drive `appDataFolder` (and local device storage for local-only mode)
- **Format:** a single JSON object holding the entire app state
- **Current schema version:** `2`

---

## Full example

```json
{
  "schemaVersion": 2,
  "handbookId": "9f1c3b2a-5e44-4c8a-9b1d-2f0a7e6d4c99",
  "educationLevel": "university",
  "academicYear": "25/26",
  "semester": 2,
  "handbookSetup": true,
  "termStart": "2026-05-18",
  "termEnd": "2026-08-07",
  "breaks": [{ "start": "2026-06-29", "end": "2026-07-05" }],
  "otherHandbooks": [],
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
  "grades": [
    {
      "id": "3c7d9e1f-8a25-4b6c-9d0e-5f4a3b2c1d88",
      "module": "CS2030S",
      "credits": 4,
      "grade": "A-",
      "su": false
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
  "city": { "buildings": [{ "x": 0, "y": 0, "floors": 3 }, { "x": -1, "y": 1, "floors": 1 }] },
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
| `handbookId`     | string \| null    | no       | **Phase 13.5.** Id (`crypto.randomUUID()`) of the **active handbook** — i.e. of the per-handbook flat fields below. `null`/missing in pre-13.5 files (the web generates one on load). |
| `educationLevel` | string \| null    | yes      | The user's level: `primary`, `secondary`, `jc`, `poly`, `university`, or `null` if unset. Drives how the timetable parser reads the image. **Chosen when a handbook is created, then locked for that handbook** (Phase 13.5) — different handbooks may have different levels. |
| `academicYear`   | string \| null    | no       | Academic year (Phase 13 handbook). **Format is level-aware:** tertiary (`university`/`poly`) spans two years → `"YY/YY"` e.g. `"25/26"`; school (`primary`/`secondary`/`jc`) is a single calendar year → `"YYYY"` e.g. `"2026"`. Drives the sidebar "HANDBOOK · …" header (rendered per level). `null` until onboarding. |
| `semester`       | number \| null    | no       | Current semester, `1` or `2` (Phase 13 handbook). Drives the sidebar header. `null` until onboarding. |
| `handbookSetup`  | boolean           | no       | `true` once the user has completed handbook onboarding (Phase 13). Gates the first-run setup modal. Defaults `false`; pre-Phase-13 files (no flag) are treated as set up iff `educationLevel` is already chosen. |
| `termStart`      | string (`YYYY-MM-DD`) \| null | no | Week-1 Monday anchor for the dated weekly timetable view. `null` until the user sets it. Used to map the recurring weekly timetable onto real dates + derive odd/even week parity. |
| `termEnd`        | string (`YYYY-MM-DD`) \| null | no | Last day of term. Weeks after it (and before `termStart`) are "outside term" — shown with dates but no week number/classes. `null` until set. |
| `breaks`         | array of `{ start, end }` (each `YYYY-MM-DD`) | no | Recess/holiday date ranges. Any week overlapping a range is non-academic: skipped in week numbering (so parity continues across it) and shows no classes. Defaults to `[]` |
| `updatedAt`      | string (ISO 8601) | yes      | When the whole state was last saved. Key field for sync + local-save reconciliation. |
| `tasks`          | array of Task     | yes      | All of the user's tasks. May be empty (`[]`). |
| `grades`         | array of Grade    | no       | **Phase 16 (grade calculator).** The active handbook's grade rows — one per module, `{ id, module, credits, grade }`. Defaults to `[]`. **Per-handbook** (rides a handbook switch, like `tasks`). GPAs are always **derived** from these rows, never stored. See "Grade object" below. |
| `studySessions`  | array of StudySession | no   | Recorded focus/study sessions (Phase 10). Defaults to `[]`. Used for daily/weekly/cumulative study-time totals + the calendar's per-day average rating |
| `city`           | City object       | no       | **Phase 14 (study city).** The generative city grid: `{ buildings: [{ x, y, floors }] }` — one entry per occupied plot, `x`/`y` centre-origin integer offsets, `floors ≥ 1`. **Stored** because upgrade placement is random (not re-derivable); every count (earned/applied/pending upgrades, land size) is **derived** per `study-city-growth.md`. Defaults to `{ "buildings": [] }`. **GLOBAL like `studySessions`** — never part of a handbook; a handbook switch must not touch it. |
| `hiddenModules`  | array of string   | no       | Module labels the user has hidden from the web dashboard + sidebar (Phase 12). Defaults to `[]`. |
| `otherHandbooks` | array of Handbook | no       | **Phase 13.5.** The **inactive** handbooks (previous/other semesters). Defaults to `[]`. See "Handbook object" below. |
| `timetable`      | Timetable \| null | yes      | The parsed timetable, or `null` if none yet. |

## Handbook object (Phase 13.5 — multiple handbooks)

A **handbook = one semester's context**. The **top-level flat fields are the ACTIVE
handbook** (unchanged from before — existing readers keep working); `otherHandbooks[]`
holds the inactive ones. Each entry has an `id` plus exactly the per-handbook fields:

| Field | Same as top-level field |
|-------|-------------------------|
| `id`  | (the handbook's identity — the active one's id is the top-level `handbookId`) |
| `educationLevel`, `academicYear`, `semester`, `handbookSetup`, `termStart`, `termEnd`, `breaks`, `timetable`, `tasks`, `grades`, `hiddenModules` | identical types/meaning to the top-level fields of the same name |

**Switching handbooks** (web): the flat fields are snapshotted into `otherHandbooks` and
the chosen entry's fields are copied out into the flat fields — one atomic save.
**`studySessions` and `city` are GLOBAL** — never part of a handbook — so streaks,
cumulative study time, and the study city span semesters.

## Task object

| Field       | Type              | Required | Description |
|-------------|-------------------|----------|-------------|
| `id`        | number            | yes      | Unique id (`Date.now()`). |
| `module`    | string            | yes      | The Module `code`, or Module `name` for Primary, Secondary and JC. |
| `title`     | string            | yes      | Task name. |
| `due`       | string (`YYYY-MM-DD`) \| `""` | yes | Due date, empty if none. |
| `type`      | string (enum)     | yes      | One of: `assignment`, `tutorial`, `quiz`, `exam`. |
| `done`      | boolean           | yes      | Whether completed. |
| `createdAt` | string (ISO 8601) | yes      | When created. |
| `updatedAt` | string (ISO 8601) | yes      | When last changed. |

## Grade object (Phase 16 — grade calculator)

One module's result for a semester. **Per-handbook** (inside the flat fields / each
`otherHandbooks` entry). GPAs are **never stored** — both clients derive them live from
these rows (web: `web/logic/gpa.js`), so they can't drift.

| Field     | Type   | Required | Description |
|-----------|--------|----------|-------------|
| `id`      | string | yes      | Unique id (`crypto.randomUUID()`). |
| `module`  | string | yes      | Module code (e.g. `"CS2030S"`), or name for school levels. |
| `credits` | number | yes      | Credit weight (MCs/units). Must be > 0 to count toward a GPA. |
| `grade`   | string | yes      | Uppercase grade string — a scheme key or an excluded grade (below). |
| `su`      | boolean | no      | **S/U election** (university scheme only): `true` = the module is S/U'd — `grade` KEEPS the real letter the student received, but the row is **excluded** from every GPA. Missing/absent = `false`. Only the literal boolean `true` elects (defensive rule, both clients). The literal `"S"`/`"U"` grade values also remain valid and excluded (pre-election rows). |

**Grading schemes** (chosen by the handbook's locked `educationLevel`):

- `university` → **5.0 scale** (NUS/NTU-style): `A+`/`A` 5.0, `A-` 4.5, `B+` 4.0, `B` 3.5,
  `B-` 3.0, `C+` 2.5, `C` 2.0, `D+` 1.5, `D` 1.0, `F` 0.0. **Excluded** (valid, never
  counted in numerator or denominator): `S`, `U`, `CS`, `CU`. *(Known limitation: SMU's
  4.3-max scale is NOT supported — "university" means the 5.0-scale schools.)*
- `poly` → **4.0 scale** (SP's published core table, shared across the polys): `DIST`/`A`
  4.0, `B+` 3.5, `B` 3.0, `C+` 2.5, `C` 2.0, `D+` 1.5, `D` 1.0, `F` 0.0. **Excluded:** `P`.
- `jc` / `secondary` / `primary` → **no GPA computed** (rank points / L1R5 aren't
  credit-weighted averages; stubbed "not yet supported").

**GPA rules (both clients must match):** GPA = `Σ(points × credits) / Σ(credits)` over
countable rows; rows with `su: true` (strictly the boolean `true`) are **excluded first**,
whatever their grade/credits; grades are normalised (trim + uppercase) before lookup;
unusable rows (unknown grade, credits ≤ 0 / non-numeric) are **skipped, never an error**;
no countable rows → no GPA (not 0). **Cumulative GPA** pools the active handbook's rows with every
`otherHandbooks` entry of the **same scheme** (university with university, poly with
poly — never across schemes) and computes ONE weighted average over the pool (not an
average of per-semester GPAs).

## StudySession object

One recorded focus/study session. Stored at a fine granularity (one row per session) so
daily / weekly / cumulative totals can be **summed** later and the per-day average rating
**averaged** — totals are never pre-stored, always derived from these rows.

| Field          | Type                | Required | Description |
|----------------|---------------------|----------|-------------|
| `id`           | string              | yes      | Unique id (`crypto.randomUUID()`). |
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
| `week`        | string | Which weeks this session runs: `"all"` (every week — the default), `"odd"`, or `"even"`. For alternating-week timetables. Treat a missing `week` as `"all"`. |

### Which fields each level populates

| Field         | Primary | Secondary | JC | Poly | Uni |
|---------------|---------|-----------|----|----|-----|
| `code`        | —       | —         | —  | ✓  | ✓   |
| `name`        | ✓       | ✓         | ✓  | ✓  | ✓   |
| `sessionType` | lesson / cca | lesson / cca | lecture / tutorial | lecture / tutorial / lab / practical | lecture / tutorial / lab / recitation / seminar |
| `classNo`     | —       | —         | (if shown) | ✓ | ✓ |
| `location`    | (if shown) | (if shown) | ✓ | ✓ | ✓ |
| `week`        | all/odd/even | all/odd/even | all/odd/even | all/odd/even | all/odd/even |

> `color` for module colour-coding is assigned client-side in the UI.

> `week` defaults to `"all"` and only differs when a timetable has an odd/even split. Sec/JC
> usually publish **two separate** Odd/Even Week images (parse each, slots tagged from the title);
> Uni/Poly use **per-cell labels** in one image (e.g. "Even Weeks", odd week-number lists). Week
> ranges / specific-week lists are collapsed to `all`/`odd`/`even` (finer granularity isn't stored).

---

## Note files (Phase 20 — separate Drive files, NOT inside `modulo-data.json`)

Uploaded study-note files (PDFs/images) live as **individual Drive files next to
`modulo-data.json`** in the same `appDataFolder` — never inside the JSON (they'd
bloat every save and fight the sync/conflict story). They are **web-managed for
now**; Android support is optional/later.

⚠️ **Rule for every reader of the folder:** select `modulo-data.json` **by file
name**. Never assume it is the only file in the `appDataFolder` — once a user
uploads a note, it isn't. Unrecognised files must be left alone.

**Identifying a note file** — Drive `appProperties` (custom key→value metadata,
queryable in `files.list`):

| appProperties key | Value | Meaning |
|-------------------|-------|---------|
| `moduloKind` | `"note"` | Marks the file as a MODULO note. **The** discriminator — list notes with the query `appProperties has { key='moduloKind' and value='note' }`. |
| `module` | e.g. `"CS2030S"` | Module tag — same label strings as Task `module` (code, or name for school levels). `""` = untagged. |
| `handbook` | a `handbookId` UUID | The **active handbook when the note was uploaded**. Notes are stored GLOBALLY (they outlive semesters, like `studySessions`); this tag only drives the web's default "this semester" display filter. A deleted handbook's notes remain valid (shown under "All semesters"). |

**Other properties:** the Drive file `name` = the user's chosen filename (renameable
in the UI; duplicates allowed — Drive identifies by `id`); the Drive `mimeType` = the
uploaded file's type. The web currently allows `application/pdf` and `image/*`, max
**5 MB** per file (Google's recommended ceiling for non-resumable uploads). Files
count against the **user's own Drive quota** (hence delete support).

---

## Time fields, sync, and local save

Two storage locations: Google Drive `appDataFolder` (when linked) and local device storage (local-only mode). The top-level `updatedAt` makes them reconcilable:
- **Last-write-wins**: each save overwrites the whole file; `updatedAt` records when.
- **On open, load fresh** so you're not editing a stale copy.
- **If both a local and a Drive copy exist**, compare top-level `updatedAt`, keep the newer.

---

## Reference implementations

### Web (JavaScript)

```javascript
let appState = {
  schemaVersion: 2,
  handbookId: crypto.randomUUID(), // id of the ACTIVE handbook (= the flat fields) — Phase 13.5
  educationLevel: null,   // "primary" | "secondary" | "jc" | "poly" | "university" — locked per handbook
  academicYear: null,     // level-aware: "YY/YY" (uni/poly) or "YYYY" (school) — Phase 13 handbook
  semester: null,         // 1 | 2 (Phase 13 handbook) — sidebar header
  handbookSetup: false,   // true once onboarding done (Phase 13) — gates first-run modal
  termStart: null,        // "YYYY-MM-DD" Week-1 Monday anchor, or null
  termEnd: null,          // "YYYY-MM-DD" last day of term, or null
  breaks: [],             // ["YYYY-MM-DD", ...] recess/holiday week Mondays
  updatedAt: null,
  tasks: [],
  grades: [],             // [{ id, module, credits, grade }] per-handbook grade rows — Phase 16
  studySessions: [],      // [{ id, start, end, durationMins, rating, createdAt }] — Phase 10
  city: { buildings: [] },// study-city grid [{ x, y, floors }] (GLOBAL, like studySessions) — Phase 14
  hiddenModules: [],      // module labels hidden from the dashboard/sidebar (web-only) — Phase 12
  otherHandbooks: [],     // the INACTIVE handbooks [{ id, ...per-handbook fields }] — Phase 13.5
  timetable: null,        // { educationLevel, modules: [...] }
};
```

### App (Kotlin)

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class ModuloData(
    val schemaVersion: Int = 2,
    val handbookId: String? = null,        // id of the ACTIVE handbook (the flat fields) — Phase 13.5
    val educationLevel: String? = null,
    val academicYear: String? = null,      // level-aware: "YY/YY" (uni/poly) or "YYYY" (school)
    val semester: Int? = null,             // 1 or 2
    val handbookSetup: Boolean = false,    // true once web onboarding done
    val termStart: String? = null,         // "YYYY-MM-DD" Week-1 Monday anchor
    val termEnd: String? = null,           // "YYYY-MM-DD" last day of term
    val breaks: List<Break> = emptyList(), // recess/holiday date ranges
    val updatedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val grades: List<Grade> = emptyList(),  // per-handbook grade rows — Phase 16
    val studySessions: List<StudySession> = emptyList(),
    val city: City = City(),               // study-city grid (GLOBAL) — Phase 14
    val hiddenModules: List<String> = emptyList(),
    val otherHandbooks: List<Handbook> = emptyList(),  // inactive handbooks — Phase 13.5
    val timetable: Timetable? = null
)

@Serializable
data class Handbook(                       // one INACTIVE semester (Phase 13.5)
    val id: String,
    val educationLevel: String? = null,
    val academicYear: String? = null,
    val semester: Int? = null,
    val handbookSetup: Boolean = false,
    val termStart: String? = null,
    val termEnd: String? = null,
    val breaks: List<Break> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val grades: List<Grade> = emptyList(),  // per-handbook grade rows — Phase 16
    val hiddenModules: List<String> = emptyList(),
    val timetable: Timetable? = null
)

@Serializable
data class Grade(                          // one module's semester result (Phase 16)
    val id: String,
    val module: String = "",     // module code, or name for school levels
    val credits: Double = 0.0,   // credit weight (MCs/units); must be > 0 to count
    val grade: String = "",      // uppercase scheme key (e.g. "A-") or excluded ("S", "U", "P")
    val su: Boolean = false      // S/U election (Phase 17): true = keep the letter, exclude from GPA
)

@Serializable
data class City(                           // the study-city grid (Phase 14)
    val buildings: List<CityBuilding> = emptyList()
)

@Serializable
data class CityBuilding(
    val x: Int,          // centre-origin plot offsets (0,0 = middle of the land)
    val y: Int,
    val floors: Int      // >= 1; each upgrade event adds exactly one floor
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
    val week: String = "all"       // all | odd | even
)
```

---

## Change log

| Version | Date       | Change |
|---------|------------|--------|
| 1       | 2026-05-30 | Initial schema: `tasks`, provisional `timetable`, time fields. |
| 2       | 2026-06-03 | Added `educationLevel`. Finalized the level-aware `timetable` structure: `slots` now use `sessionType` + `classNo` + `teacher` (replacing the provisional `type`). |
| 2       | 2026-06-16 | Dropped the unused `teacher` field from `Slot`. `location` is the room/venue only. |
| 2       | 2026-06-18 | Added `week` (`all`/`odd`/`even`, default `all`) to `Slot` for alternating-week timetables. |
| 2       | 2026-06-18 | Added `module` to `Task`, the module code, or name for primary/secondary/jc, that the task belongs to. |
| 2       | 2026-06-23 | Added top-level `termStart` (`YYYY-MM-DD` Week-1 Monday, default `null`) for the dated weekly timetable view + odd/even parity. |
| 2       | 2026-06-23 | Added top-level `termEnd` (`YYYY-MM-DD`, default `null`) and `breaks` (recess/holiday `{ start, end }` date ranges, default `[]`) for term bounds + non-academic weeks. Any week overlapping a break range is skipped in numbering. |
| 2       | 2026-06-24 | Added top-level `studySessions` (array of StudySession `{ id, start, end, durationMins, rating, createdAt }`, default `[]`). Feeds daily/weekly/cumulative study-time totals + the calendar's per-day average rating. |
| 2       | 2026-06-25 | Added top-level `hiddenModules` (array of strings, default `[]`) — module labels the web app hides from the dashboard + sidebar |
| 2       | 2026-06-27 | Added top-level `academicYear` (string `"YY/YY"`, default `null`), `semester` (number `1`/`2`, default `null`), and `handbookSetup` (boolean, default `false`) for the handbook/onboarding. Drive the sidebar header + gate the first-run setup modal. |
| 2       | 2026-07-05 | **Phase 13.5 (multiple handbooks):** added top-level `handbookId` (string, default generated) and `otherHandbooks` (array of Handbook, default `[]`). The flat fields remain **the active handbook**, so existing readers are unaffected — but **kotlinx.serialization must tolerate the new keys** (add the fields per the Kotlin reference, or set `ignoreUnknownKeys = true`), otherwise parsing a web-saved file throws. `studySessions` stay global (never inside a handbook). Education level is now locked **per handbook**. |
| 2       | 2026-07-08 | **Phase 14 (study city):** added top-level `city` (`{ buildings: [{ x, y, floors }] }`, default empty) — the generative city grid. Stored because upgrade placement is random; every count is **derived** from `studySessions` per the shared rules in `study-city-growth.md`. GLOBAL like `studySessions` — never inside a handbook. Same kotlinx note as 13.5: tolerate the new key. *(An interim `cityLevel` integer existed only on the web feature branch and never shipped — readers may ignore that key if ever seen.)* |
| 2       | 2026-07-13 | **Phase 16 (grade calculator):** added `grades` (array of Grade `{ id, module, credits, grade }`, default `[]`) as a **per-handbook** field — top-level (the active handbook) AND inside each `otherHandbooks` entry, carried by handbook switches like `tasks`. GPAs are never stored — both clients derive them per the "Grade object" section's scheme tables + rules (university = 5.0 scale with S/U/CS/CU excluded; poly = 4.0 with P excluded; jc/secondary/primary = no GPA). Same kotlinx note as 13.5: tolerate the new key. |
| 2       | 2026-07-13 | **Phase 17 (S/U election):** added optional `su` (boolean, default `false`/absent) to the Grade object — `true` keeps the student's real letter in `grade` but **excludes** the row from every GPA (checked before anything else; only the literal boolean `true` elects). University scheme only; the literal `"S"`/`"U"` grade values stay valid and excluded. Kotlin: `val su: Boolean = false`. |
| 2       | 2026-07-15 | **Phase 20 (notes):** `modulo-data.json` itself is **unchanged**. NEW: uploaded study-note files now live **beside it** in the `appDataFolder` — one Drive file per note, marked `appProperties.moduloKind = "note"` + `module`/`handbook` tags (see "Note files"). ⚠️ Every reader must select `modulo-data.json` **by name** and ignore unrecognised files — the data file is no longer the only file in the folder. |
