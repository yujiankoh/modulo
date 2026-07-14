# MODULO — Software Testing (web + proxy)

How we test MODULO's web app (`web/`) and parsing proxy (`server/`), across the three
levels expected for Orbital: **unit**, **integration**, and **system** testing. For
Milestone 2 the goal is *documented evidence of a testing approach*, not exhaustive
coverage (that is Milestone 3); this file records what we test, how, and why.

## Approach at a glance

| Level | What it checks | How we do it | Where |
|-------|----------------|--------------|-------|
| **Unit** | Individual pure functions (one input → one expected output) | Automated, **Node's built-in test runner** (`node --test`), no framework/installs | `tests/*.test.js` |
| **Integration** | Several units working together across a boundary | Automated: the proxy's HTTP route end-to-end (Express + handler + response). Manual: parse → merge → grid in the running app | `tests/proxy.test.js` + manual |
| **System** | The whole app, end-to-end, as a user | Manual scripted scenarios through the real UI, with screenshots | table below |

**Why this split:** the proxy is plain Node, so it's fully automatable. The web app's
modules touch the browser at load (`document`, `localStorage`, Google Identity Services),
so running them in Node would crash — full browser automation needs heavier tooling
(jsdom / Playwright), which is planned for **Milestone 3**. For MS2 we automate the
**pure logic** (which we deliberately extracted into `web/logic/` so it's importable and
testable in isolation) and cover the browser-dependent UI with documented **manual system
tests**.

## Running the automated tests

From the repo root:

```bash
npm test          # runs every tests/*.test.js with Node's built-in runner
```

Requires Node 18+ (developed on Node 24). No dependencies to install — `node --test`,
`node:assert`, and `fetch` are all built in. The proxy route test starts the Express app
on an ephemeral port and makes a real HTTP request; it asserts the **input-validation**
path, which returns `400` *before* any Gemini call — so the suite **spends no Gemini
quota** and needs no API key.

Current result: **57 tests, 57 passing.**

## Unit tests

Pure, dependency-free functions were extracted into `web/logic/` and `server/index.js` so
they can be tested in isolation.

| Function | File | Test file | What's asserted |
|----------|------|-----------|-----------------|
| `formatAcademicYear`, `parseStartYear`, `isTertiary`, `formatHeaderLabel` | `web/logic/academicYear.js` | `tests/academicYear.test.js` | Tertiary AY spans two years (`2025`→`"25/26"`), school is single (`"2026"`); inverse round-trips; header label formats per level and is empty until complete |
| `layoutColumns` | `web/logic/timetableLayout.js` | `tests/timetableLayout.test.js` | Non-overlapping classes get 1 lane; two overlapping split into 2 lanes; touching (end == start) is not an overlap; three mutually overlapping use 3 lanes |
| `mergeModules`, `sameSlot` | `web/logic/mergeModules.js` | `tests/mergeModules.test.js` | Same module's slots combine; exact-duplicate slots skipped; differing code/name add a new module; inputs are not mutated |
| `buildPrompt`, `extractJson` | `server/index.js` | `tests/proxy.test.js` | Prompt includes the level-specific section + falls back to secondary for unknown levels; JSON is extracted from surrounding noise and braces inside strings are ignored |
| `snapshotHandbook`, `blankHandbook`, `switchHandbook` | `web/logic/handbooks.js` | `tests/handbooks.test.js` | Snapshot captures every `HANDBOOK_FIELDS` entry, deep-copies (no shared references), excludes globals (`studySessions`); switch swaps active↔stored, round-trips losslessly, never mutates its input, no-ops on unknown/active ids (Phase 13.5) |
| `totalStudyMins`, `earnedUpgrades`, `gridTier`, `plotWeight`, `applyUpgrades`, `appliedUpgrades`, `cityState` | `web/logic/growth.js` | `tests/growth.test.js` | Study-city rules (Phase 14): pacing inverse exact at every `n²+9n` boundary (10/22/36/…/1200→30); tier switches exactly at 20 h/100 h; centre-weighted pick (9/4/1 rings) obeys an **injected scripted RNG** (deterministic tests of random logic); founding building always dead-centre; spawn-vs-grow by occupancy; capped plots excluded; maxed grid banks; inputs never mutated; **progression invariant** — every tier expands long before its land can fill (guards future floor-cap tuning) |
| `SCHEMES`, `schemeForLevel`, `computeGPA`, `cumulativeGPA` | `web/logic/gpa.js` | `tests/gpa.test.js` | Grade calculator (Phase 16): level→scheme mapping (`university`→5.0, `poly`→4.0, jc/secondary/primary stubbed with a reason); hand-computed weighted averages for both schemes; credit weighting (bigger modules pull harder); S/U/CS/CU and poly `P` excluded from numerator AND denominator; `gpa: null` (not 0) when nothing counts; junk rows (unknown grade, ≤0/string/NaN credits, null row) skipped + counted, never thrown; grade normalisation (`"a+"` counts); cumulative GPA pools only same-scheme handbooks (JC contributes nothing to uni; poly never mixes with uni), tolerates pre-16 handbooks without `grades`; **S/U election** (Phase 17): `su: true` keeps the letter but excludes the row (excluded, not skipped), junk-proof both ways (only the literal boolean `true` elects; an elected junk row is still excluded), `suElection` scheme flags (nus5 yes, poly4 no); inputs never mutated |

Each test feeds known inputs and asserts the exact output with `node:assert/strict` — e.g.
`assert.equal(formatAcademicYear(2025, "university"), "25/26")`.

## Integration test

`tests/proxy.test.js` starts the real Express app (`app.listen(0)`) and sends an actual
`POST /parse-timetable` request over HTTP. It verifies the routing + middleware + handler
work **together**: a request missing `image`/`mimeType` is rejected with HTTP `400` and a
`"Missing image..."` body. This exercises the same path a browser hits, minus the external
Gemini call (which the handler only reaches *after* validation passes).

Web-side integration (parse result → `mergeModules` → `layoutColumns` → rendered grid) is
covered by the manual system tests below, since it spans the DOM.

## System tests (manual, end-to-end)

Run in the browser against the deployed/Live-Server app, signed in (local or Drive mode).
Record the result and attach a screenshot for each. Mark **Pass / Fail**; on Fail, note
the actual behaviour.

| # | Scenario | Steps | Expected result | Result |
|---|----------|-------|-----------------|--------|
| S1 | First-run onboarding | Fresh state → choose a storage mode | Handbook setup modal auto-opens; can be filled or dismissed; doesn't re-nag after dismiss | Pass (observed) |
| S2 | Level-aware academic year | In handbook, switch level uni↔secondary, type a year | "Will show as…" preview updates: `AY25/26 · S1` vs `2026 · Sem 1` | Pass (observed) |
| S3 | Connect storage | Settings → "Use this device only" / "Connect Google Drive" | Mode set; account chip + status reflect it; data loads | To verify |
| S4 | Upload + parse timetable | Upload modal → pick image → Parse | Modules parsed and saved; grid populates (uses 1 Gemini quota) | To verify |
| S5 | Timetable grid display | Open Timetable | Blocks coloured by module (matching sidebar dots), show type · time · location | Pass (observed) |
| S6 | Overlapping classes | View a week with two classes at the same time | They render side by side (lanes), both readable — not stacked | Pass (observed) |
| S7 | Odd/even weeks | Navigate weeks with an odd/even timetable | Only the matching-parity slots show; week number + parity correct | To verify |
| S8 | Manual timetable edit | Timetable → Edit → change/add a slot → Save | Grid reflects the edit | To verify |
| S9 | Add task (module picker) | + Add Task → pick a parsed module / "+ Add other…" | Task saves with chosen module; "other" reveals a text field | Pass (observed) |
| S10 | Task list filter/sort/complete | All Tasks → change filters/sort, tick a task | List re-buckets; completed task moves to Completed | To verify |
| S11 | Calendar | Calendar view | Tasks appear as pills on due dates; day popup lists them; study ratings as stars | To verify |
| S12 | Study timer | Study → play/pause toggle → Stop & Save → rate | One play/pause button toggles; session saved; today/week/total update | To verify |
| S13 | Dashboard aggregation | Dashboard | Eyebrow week, greeting, today's schedule, tasks-due-soon, module cards all correct | To verify |
| S14 | Module detail modal | Click a module in the sidebar / a dashboard card | Modal opens with that module's notes placeholder + tasks; school subjects show once (no "1BY2 · 1BY2") | Pass (observed) |
| S15 | Edit handbook / change level | Settings → Edit → change education level (with a timetable saved) | A confirm warns the timetable may not match; cancel keeps everything; sidebar header updates on save | Pass (observed) |
| S16 | Light/dark theme | Toggle theme | All views + icons + module colours re-theme; choice persists across reload | Pass (observed) |
| S17 | Proxy error handling | (If a parse fails) read the message | Friendly message shown; real cause visible in Render logs (429/503/504 mapping) | To verify |
| S18 | Start new semester (13.5) | Settings → Start new semester → confirm | Confirm names the current handbook; onboarding modal opens fresh (year pre-filled to current); old handbook stored (appears in the handbook list) | To verify |
| S19 | Switch handbook (13.5) | Settings → handbook list → Switch | "Switching handbook…" overlay (≥400ms); timetable, tasks, sidebar header + modules, dashboard all swap; reload keeps the switched-to handbook active | To verify |
| S20 | Study time is global (13.5) | Note study totals/streak → switch handbook | Today/week/total, streak, and calendar stars are IDENTICAL before and after any switch | To verify |
| S21 | Delete handbook (13.5) | Handbook list → trash on a stored row | Active row has no trash; confirm names the handbook + its task count; Cancel = no change; OK removes it permanently (survives reload) | To verify |
| S22 | Level locked per handbook (13.5) | Settings → Edit (set-up handbook) vs Start new semester | Edit shows the level as read-only text + note; a NEW handbook's modal shows the full dropdown | To verify |
| S23 | Pre-13.5 file migration | Load data saved before 13.5 | Loads cleanly; gains `handbookId` + empty `otherHandbooks` on save; app behaves as a single-handbook state | To verify |
| S24 | Study city — first building (14/15) | Fresh data → study ≥10 min total → Stop & Save | Empty island + "island is waiting" blurb before; after saving, the founding building pops in at the exact centre; blurb switches to the growth wording | To verify |
| S25 | Study city — auto growth + persistence | With hours banked, load the app; then reload | Pending upgrades apply automatically (staggered pop-in); the SAME layout persists across reloads (grid is stored, not re-rolled) | To verify |
| S26 | Study city — land expansion | Cross 20 h total study (or fake mins locally) | Island expands 5×5 → 7×7 (camera zooms out); existing buildings keep their plots; 100 h → 9×9 | To verify |
| S27 | Study city — global across handbooks (like S20) | Note the city → switch handbook | The city is IDENTICAL before/after any handbook switch (city + studySessions are global) | To verify |
| S28 | Study city — colour schemes | Click the "▶ SCHEME i/6" HUD pill; toggle light/dark; reload | Cycles all 6 schemes; land + sea + buildings + windows restyle instantly; each scheme has day/night variants; choice persists per device | To verify |
| S29 | Study city — cross-device render (with Ling Song, when app-side lands) | Same Drive data on web + Android | Both devices show the SAME city layout and building colours (stored grid + shared coordinate hash); visual palettes may differ per device by design | Blocked on app |
| S30 | Grades — enter grades, live GPA (17) | #grades → set credits + grade on timetable rows | Every timetable module pre-listed (phantom rows, credits pre-filled 4 for uni / blank for poly); picking a grade stores the row + both GPA cards update instantly; reload keeps grades; setting a grade back to "—" deletes the row (phantom returns) | To verify |
| S31 | Grades — S/U election (17, uni only) | Tick S/U on a graded module | Letter grade STAYS visible; semester + cumulative GPA recompute without it; untick → counts again; reload keeps the tick; poly handbooks show NO S/U column | To verify |
| S32 | Grades — per-handbook (17) | Note both GPAs → switch handbook → switch back | Rows + semester GPA swap with the handbook; cumulative GPA identical before/after (same-scheme pooling); JC handbook shows the "not supported" panel instead | To verify |
| S33 | Grades — unsupported level (17) | Switch to a jc/secondary handbook → #grades | Cards + editor replaced by the "no grade calculator for this level yet" panel with the reason text; semester history still renders below | To verify |
| S34 | Grades — semester history (17) | ≥2 handbooks, some with grades | One row per handbook, chronological, "current" chip on the active one; each GPA in ITS OWN scheme (scale shown when mixed, e.g. "3.80 (4.0)"); JC rows say "no GPA"; single-handbook states show no history card | To verify |

> Replace "To verify" with **Pass/Fail** during a manual pass and drop screenshots into a
> `docs/test-evidence/` folder (or the report), referenced by scenario number.

## Known limitations / Milestone 3 plans

- **No automated browser/DOM tests yet.** UI-coupled modules (`router`, `dashboard`,
  `task`, `calendarView`, `studyTimer`, `timetableView` rendering) are covered manually.
  MS3: add **jsdom** for DOM-level unit tests and/or **Playwright** for automated
  end-to-end system tests.
- **Gemini parsing is not asserted on real images.** We test the proxy's validation +
  prompt construction + JSON extraction, but not Gemini's output (non-deterministic +
  quota-limited). MS3: a small set of fixture images with tolerance-based checks.
- **Drive sync** (Phase 11) is deferred, so cross-device sync isn't yet in scope.
