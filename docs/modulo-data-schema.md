# MODULO - Data Schema (`modulo-data.json`)

This schema defines the shape of the data that both the web and app will use to sync via Google Drive

- **File name:** `modulo-data.json`
- **Location:** the Google Drive `appDataFolder` (and for local-only mode, the device's local storage)
- **Format:** a single JSON object holding the entire app state
- **Current schema version:** `1`

## Example
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

## Top-level fields

| Field           | Type            | Required | Description |
|-----------------|-----------------|----------|-------------|
| `schemaVersion` | number          | yes      | The structure version. Currently `1`. |
| `updatedAt`     | string (ISO 8601) | yes    | When the whole state was last saved.  |
| `tasks`         | array of Task   | yes      | All of the user's tasks. May be empty (`[]`). |
| `timetable`     | Timetable       | yes      | The parsed timetable, or `null` if none yet. |

## Task object

| Field       | Type              | Required | Description |
|-------------|-------------------|----------|-------------|
| `id`        | number            | yes      | Unique id for the task. Generated with `Date.now()` (ms since 1970). |
| `title`     | string            | yes      | Task name, e.g. "CS1101S tutorial". |
| `due`       | string (`YYYY-MM-DD`) | yes  | Due date. Empty string if no date set. |
| `type`      | string (enum)     | yes      | One of: `assignment`, `tutorial`, `quiz`, `exam`. |
| `done`      | boolean           | yes      | Whether the task is completed. |
| `createdAt` | string (ISO 8601) | yes      | When the task was first created. |
| `updatedAt` | string (ISO 8601) | yes      | When the task was last changed. |

## Timetable object
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
