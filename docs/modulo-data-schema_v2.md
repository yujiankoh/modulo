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
  "updatedAt": "2026-06-03T09:35:06.606Z",
  "tasks": [
    {
      "id": 1717059306606,
      "module": "CS1101S",
      "title": "Tutorial 2",
      "due": "2026-06-10",
      "type": "tutorial",
      "done": false,
      "createdAt": "2026-06-03T09:30:00.000Z",
      "updatedAt": "2026-06-03T09:35:06.606Z"
    }
  ],
  "timetable": {
    "educationLevel": "university",
    "modules": [
      {
        "code": "CS2030S",
        "name": "",
        "slots": [
          { "day": "MON", "start": "12:00", "end": "14:00", "location": "E-Learning", "sessionType": "lecture", "classNo": "1" }
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
| `educationLevel` | string \| null    | yes      | The user's level: `primary`, `secondary`, `jc`, `poly`, `university`, or `null` if unset. Drives how the timetable parser reads the image. |
| `updatedAt`      | string (ISO 8601) | yes      | When the whole state was last saved. Key field for sync + local-save reconciliation. |
| `tasks`          | array of Task     | yes      | All of the user's tasks. May be empty (`[]`). |
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
          "classNo": "1"
        }
      ]
    }
  ]
}
```

| Field (module) | Type   | Description |
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

### Which fields each level populates

| Field         | Primary | Secondary | JC | Poly | Uni |
|---------------|---------|-----------|----|----|-----|
| `code`        | —       | —         | —  | ✓  | ✓   |
| `name`        | ✓       | ✓         | ✓  | ✓  | ✓   |
| `sessionType` | lesson / cca | lesson / cca | lecture / tutorial | lecture / tutorial / lab / practical | lecture / tutorial / lab / recitation / seminar |
| `classNo`     | —       | —         | (if shown) | ✓ | ✓ |
| `location`    | (if shown) | (if shown) | ✓ | ✓ | ✓ |

> `color` for module colour-coding is assigned client-side in the UI, not by the parser,
> so it is not part of this contract.

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
  educationLevel: null,   // "primary" | "secondary" | "jc" | "poly" | "university"
  updatedAt: null,
  tasks: [],
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
    val updatedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val timetable: Timetable? = null
)

@Serializable
data class Task(
    val id: Long,
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
    val classNo: String = ""
)
```

---

## Change log

| Version | Date       | Change |
|---------|------------|--------|
| 1       | 2026-05-30 | Initial schema: `tasks`, provisional `timetable`, time fields. |
| 2       | 2026-06-03 | Added `educationLevel`. Finalized the level-aware `timetable` structure: `slots` now use `sessionType` + `classNo` + `teacher` (replacing the provisional `type`). |
| 2       | 2026-06-16 | Dropped the unused `teacher` field from `Slot` — the proxy never emitted it and we decided not to capture teacher info; `location` is the room/venue only. `schemaVersion` stays |
| 2       | 2026-06-18 | Added `module` to Task |
