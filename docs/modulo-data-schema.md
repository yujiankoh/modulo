# MODULO — Data Schema (`modulo-data.json`)

The agreed shape of MODULO's data. **Both the web (JavaScript) and the app (Kotlin)
read and write this same file**, so they must follow this contract exactly — same
field names, same types. If the two sides disagree on a field name, sync silently
breaks even though the file transfers fine.

- **File name:** `modulo-data.json`
- **Location:** the Google Drive `appDataFolder` (and, for local-only mode, the device's
  local storage)
- **Format:** a single JSON object holding the entire app state
- **Current schema version:** `1`

> Rule of thumb when changing this: **adding** a new optional field is safe (old files
> still load). **Renaming or removing** a field requires updating web *and* app together.
> Always read defensively — default missing fields rather than assuming they exist.

---

## Full example

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-05-30T09:35:06.606Z",
  "tasks": [
    {
      "id": 1717059306606,
      "title": "CS1101S tutorial",
      "due": "2026-06-05",
      "type": "tutorial",
      "done": false,
      "createdAt": "2026-05-30T09:30:00.000Z",
      "updatedAt": "2026-05-30T09:35:06.606Z"
    }
  ],
  "timetable": null
}
```

---

## Top-level fields

| Field           | Type            | Required | Description |
|-----------------|-----------------|----------|-------------|
| `schemaVersion` | number          | yes      | The structure version. Currently `1`. Bump when the structure changes. |
| `updatedAt`     | string (ISO 8601) | yes    | When the whole state was last saved. **This is the key field for sync and local save** (see below). |
| `tasks`         | array of Task   | yes      | All of the user's tasks. May be empty (`[]`). |
| `timetable`     | Timetable \| null | yes    | The parsed timetable, or `null` if none yet. Structure is provisional (see below). |

## Task object

| Field       | Type              | Required | Description |
|-------------|-------------------|----------|-------------|
| `id`        | number            | yes      | Unique id for the task. Web generates it with `Date.now()` (ms since 1970). |
| `title`     | string            | yes      | Task name, e.g. "CS1101S tutorial". |
| `due`       | string (`YYYY-MM-DD`) \| `""` | yes | Due date. Empty string if no date set. |
| `type`      | string (enum)     | yes      | One of: `assignment`, `tutorial`, `quiz`, `exam`. |
| `done`      | boolean           | yes      | Whether the task is completed. |
| `createdAt` | string (ISO 8601) | yes      | When the task was first created. |
| `updatedAt` | string (ISO 8601) | yes      | When the task was last changed. |

### `type` allowed values
Keep this list identical on both sides. To add a category later (e.g. `lab`,
`project`), add it here first, then to both the web `<select>` and the app's options.

`assignment` · `tutorial` · `quiz` · `exam`

---

## Timetable object (PROVISIONAL)

The timetable shape isn't finalised yet — it depends on the output of the Gemini
timetable-parsing work. The structure below is a **proposal** to align on; treat it as
a draft until the parser's real output is known.

```json
"timetable": {
  "modules": [
    {
      "code": "CS1101S",
      "name": "Programming Methodology",
      "color": "#4F8DFD",
      "slots": [
        {
          "day": "MON",
          "start": "10:00",
          "end": "12:00",
          "location": "COM1-0210",
          "type": "lecture"
        }
      ]
    }
  ]
}
```

| Field (module) | Type   | Description |
|----------------|--------|-------------|
| `code`         | string | Module code, e.g. "CS1101S". |
| `name`         | string | Full module name. |
| `color`        | string | Hex colour for the module's colour-coding in the UI. |
| `slots`        | array  | The recurring class sessions for this module. |

| Field (slot) | Type   | Description |
|--------------|--------|-------------|
| `day`        | string | `MON`–`SUN`. |
| `start`      | string | Start time, `HH:MM` (24h). |
| `end`        | string | End time, `HH:MM` (24h). |
| `location`   | string | Room/venue. |
| `type`       | string | e.g. `lecture`, `tutorial`, `lab`. |

---

## Time fields, sync, and local save

There are two storage locations a device might use:

- **Google Drive** (`appDataFolder`) — when the user links their Google account.
- **Local device storage** — when the user chooses "local save only" (no Google login).

`updatedAt` (the top-level one) is what makes these reconcilable:

- **Last-write-wins:** each save overwrites the whole file. The `updatedAt` stamp records
  *when* that version was written.
- **On open, load fresh** so you're not editing a stale copy.
- **When both a local copy and a Drive copy exist** (e.g. user used local mode, then later
  links Drive), compare their top-level `updatedAt` and keep the **newer** one. That's the
  whole reconciliation rule for now — no complex merging.

The per-task `createdAt`/`updatedAt` aren't needed for basic sync today; they're included
now (cheap to add, painful to retrofit) so that *later*, if finer-grained merging is ever
wanted, the timestamps already exist.

> **Do not build conflict resolution / merging yet.** For one student using their own
> phone + laptop, last-write-wins with "load fresh on open" is sufficient and standard.

---

## Reference implementations

### Web (JavaScript) — the in-memory state object

```javascript
let appState = {
  schemaVersion: 1,
  updatedAt: null,
  tasks: [],        // array of task objects (see Task fields above)
  timetable: null,
};
```

### App (Kotlin) — matching `@Serializable` data classes

Field names **must match the JSON keys exactly** (kotlinx.serialization maps by name).
Defaults make reading old/partial files safe.

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class ModuloData(
    val schemaVersion: Int = 1,
    val updatedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val timetable: Timetable? = null
)

@Serializable
data class Task(
    val id: Long,
    val title: String,
    val due: String = "",
    val type: String,            // assignment | tutorial | quiz | exam
    val done: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class Timetable(
    val modules: List<Module> = emptyList()
)

@Serializable
data class Module(
    val code: String,
    val name: String,
    val color: String,
    val slots: List<Slot> = emptyList()
)

@Serializable
data class Slot(
    val day: String,
    val start: String,
    val end: String,
    val location: String = "",
    val type: String = ""
)
```

> Tip for the Kotlin side: configure the JSON parser with `ignoreUnknownKeys = true`
> (`Json { ignoreUnknownKeys = true }`). Then if the web adds a field the app doesn't know
> about yet, the app ignores it instead of crashing — a key part of safe schema evolution.

---

## Change log

| Version | Date       | Change |
|---------|------------|--------|
| 1       | 2026-05-30 | Initial schema: `tasks`, `timetable` (provisional), time fields. |
