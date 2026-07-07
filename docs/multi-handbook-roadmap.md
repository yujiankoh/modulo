# Phase 13.5 — Multiple handbooks: roadmap

> The current-phase doc. Decisions were made 2026-07-05 (see CLAUDE.md → "MS3 phase plans");
> this file turns them into build steps. **One step at a time, app working + commit after
> each step.** Branch: `feature/multi-handbook-web`.

## What we're building (recap of the decisions)

A **handbook = one semester's context**: education level, academic year, semester, term
dates + breaks, timetable, tasks, hidden modules (and later, Phase 16's grades).
**"Start new semester"** stores the current handbook and onboards a fresh one.
A **switcher in Settings** makes any handbook active again — fully **editable**, exactly
like today's single handbook. Education level is chosen at handbook creation and **locked
per-handbook**. **`studySessions` are GLOBAL** — never swapped — so streaks and cumulative
study time span semesters (they fuel the Phase 14 city).

## Data model (additive — Android unchanged, no schemaVersion bump)

The **flat fields stay = the active handbook** (that's what Ling Song's app reads today).
Two new fields:

```json
{
  "handbookId": "…uuid…",        // NEW: identifies which handbook the flat fields are
  "otherHandbooks": [            // NEW: the INACTIVE handbooks, same shape as the flat fields
    { "id": "…uuid…", "educationLevel": "jc", "academicYear": "2025", "semester": 2,
      "termStart": "…", "termEnd": "…", "breaks": [], "timetable": {…},
      "tasks": [], "hiddenModules": [] }
  ]
}
```

**The per-handbook field list** (the exact flat fields a switch swaps):
`educationLevel, academicYear, semester, termStart, termEnd, breaks, timetable, tasks,
hiddenModules` (+ `handbookSetup` is per-handbook too — a freshly created handbook is
"not set up" until its modal is saved). Everything else — `schemaVersion`, `studySessions`,
`updatedAt`, storage mode — is global and untouched by a switch.

**Switching** = push the flat fields (as a snapshot with their `handbookId`) into
`otherHandbooks`, pull the chosen entry out into the flat fields, **one `persist()`**.
Atomic: no view ever sees half of one semester and half of another.

**Migration** (files saved before 13.5): on load, if `handbookId` is missing generate one;
if `otherHandbooks` is missing default to `[]`. Old single-handbook files then just *are*
a one-handbook state. (Same `=== undefined` discipline as the Phase 13 migration.)

## Steps

### Step 0 — branch + commit the plan
`git checkout -b feature/multi-handbook-web`, commit CLAUDE.md + this roadmap.
*(No code. App unchanged.)*

### Step 1 — pure logic: `web/logic/handbooks.js` + unit tests
The swap brain, with **no DOM and no appState import** (testable in Node, like the other
`web/logic/` files):
- `HANDBOOK_FIELDS` — the canonical list of per-handbook field names (one place, so the
  snapshot and the apply can never disagree).
- `snapshotHandbook(state)` → a **deep-copied** handbook object `{ id: state.handbookId,
  …HANDBOOK_FIELDS… }` (deep copy so later edits to the live state can't mutate the stored
  snapshot — same never-share-references rule as `mergeModules`).
- `blankHandbook(id)` → a fresh handbook (level null, empty timetable/tasks/breaks,
  `handbookSetup: false`).
- `switchHandbook(state, targetId)` → returns `{ flat, otherHandbooks }`: current flat
  snapshot pushed in, target pulled out. Pure — takes a state-shaped object, returns new
  objects, mutates nothing.
- **Tests** (`tests/handbooks.test.js`): snapshot copies every field + is reference-independent;
  blank handbook shape; switch round-trips (switch away and back = original data);
  `studySessions` never appears in a snapshot; unknown target id → no-op/error.

**Verify:** `npm test` (new tests + existing 17 green). **Commit.**

### Step 2 — schema defaults + migration + docs
- `data.js`: add `handbookId: null` + `otherHandbooks: []` to the `appState` initializer;
  in `loadInitialData`, generate a `handbookId` if missing (`crypto.randomUUID()`) and
  default `otherHandbooks` to `[]`.
- Update `docs/modulo-data-schema.md`: the two new fields, the per-handbook field list,
  "flat = active", and that `studySessions` are global.
- Draft the **Ling Song heads-up** (his app can ignore both fields; nothing he reads changed).

**Verify:** app loads, old saved data still loads (check with your real Drive data), tests
green. **Commit.**

### Step 3 — "Start new semester" flow
- Settings → Handbook card: add a **"Start new semester"** button.
- Handler: guard (current handbook must be set up — `handbookSetup` true); snapshot current
  → push to `otherHandbooks`; reset the flat fields to `blankHandbook(newId)`; **one
  `persist()`**. The existing first-run listener sees `handbookSetup: false` and auto-opens
  the handbook modal — the new-semester onboarding is the first-run flow, reused for free.
- Edge: the modal's "snooze on close" (`dismissed`) flag should reset here so the new
  semester's setup does prompt.

**Verify:** start a new semester → old timetable/tasks vanish (stored, not lost), modal
opens fresh, saving it populates the new handbook; sidebar header updates. Study totals
unchanged throughout. **Commit.**

### Step 4 — handbook list + Switch (Settings)
- The Handbook card becomes a **list**: one row per handbook — label via the existing
  `formatHeaderLabel` logic (+ level), the active one marked, a **Switch** button on the
  others.
- Switch handler: `switchHandbook` → write `flat` + `otherHandbooks` back onto `appState` →
  **one `persist()`**. All views redraw into the other semester automatically
  (`modulo:datachanged`).
- Close any open modals before switching (a stale editor over swapped state would confuse).

**Verify:** switch back and forth — timetable, tasks, sidebar header, dashboard week all
follow; study totals/streak/stars do NOT change; reload mid-way and everything persists.
**Commit.**

### Step 5 — lock the education level per handbook
- `openHandbook()`: if the active handbook is already set up → show the **locked text**
  field (`#hbLevelLocked(Field)` — kept hidden in the DOM since Phase 13) and hide the
  dropdown; a brand-new handbook (not set up) → dropdown as today.
- Remove the now-dead level-change `confirm()` path in the Save handler.
- Update the CLAUDE.md "Handbook & education level" decision entry (TEMPORARILY EDITABLE →
  locked per-handbook, as originally planned) + the auto-memory note.

**Verify:** editing an existing handbook shows the level as fixed text; creating a new
semester offers the full dropdown. **Commit.**

### Step 6 — docs, tests, wrap-up
- `docs/testing.md`: add system-test scenarios (start new semester; switch; global study
  totals across handbooks; old-file migration).
- CLAUDE.md: mark 13.5 ✅ in the roadmap table with a summary line.
- Merge `feature/multi-handbook-web` → `main` via PR; send Ling Song the heads-up.

## Care points (carried from the plan)
- The swap must replace ALL per-handbook flat fields in **one** `persist()` — atomic.
- **Never swap `studySessions`** (the Phase 14 city's fuel is global by design).
- Deep-copy on snapshot — a stored handbook must not share object references with the live
  flat fields.
- Views need zero changes: they already read the flat fields and redraw on
  `modulo:datachanged`. If a view seems to "remember" the old semester, that's a bug in the
  swap's atomicity, not in the view.

## Added during the phase (2026-07-05, after step 5)
- **Switch loading overlay** — a dim overlay + spinner during `switchToHandbook`, held for
  a ≥400ms minimum (`Promise.all([persist(), delay(400)])`) so instant local saves still
  read as a deliberate transition. `#switchOverlay`, z-index above the modals.
- **Delete handbook** — trash button on **stored** rows only (the active handbook can't be
  deleted; switch away first), guarded by a `confirm()` that names the handbook and its
  task count. Permanent — there's no undo.

## Out of scope for 13.5
Per-semester study stats (derivable from session timestamps whenever wanted) · grades
(`Phase 16` adds them as a per-handbook field and the swap picks them up via
`HANDBOOK_FIELDS`).
