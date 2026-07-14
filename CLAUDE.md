# CLAUDE.md — MODULO (YJ's side: web + proxy)

Guidance for Claude Code working in this repo. Read this file **and the current-phase
doc** (`docs/handoffs/session-handoff-phase18.md` — Phase 18 next) at the start of each session — they
hold the project's history, the current phase, and the decisions already made. (Local-only
docs are organised: phase roadmaps in `docs/roadmaps/`, session handoffs in `docs/handoffs/`,
YJ's references in `docs/notes/`, Orbital report material in `docs/report/`; older committed
phase docs live in `docs/guides/`.) For full context also see `modulo-handoff.md`.

## What this project is

A companion platform — **Android app + web** — for Singapore students to manage academic
tasks and parse their school/university timetable from a photo. NUS Orbital 2026.

- **YJ (me)** owns the **web app** (`web/`) and the **shared backend proxy** (`server/`).
- **Ling Song** owns the **Android app** (`app/`, Kotlin / Jetpack Compose). Don't edit
  `app/` — coordinate instead.

## Architecture (the load-bearing decisions)

- **Browser-first, no traditional backend for user data.** The user's own **Google Drive**
  is the storage layer, via Drive's hidden `appDataFolder`. No server/database of ours.
- **Cross-device sync** works because the web and Android clients share one Google Cloud
  project (`modulo-0326`), so Google gives them the same `appDataFolder`. The shared
  on-disk contract is a single JSON file, `modulo-data.json` (see `docs/modulo-data-schema.md`).
- **One exception that needs a server: Gemini timetable parsing.** The API key can't live
  in client code, so a small **Node/Express proxy** (`server/`) holds it. Both web and app
  POST a timetable image to that one proxy and get structured JSON back — parsing logic and
  prompt written once, never duplicated in JS and Kotlin.

## Stack

- **Web:** plain HTML/CSS/JS — `web/` (`index.html`, `app.js`, `style.css`). No framework.
- **Auth/storage:** Google Identity Services (token flow) + Google Drive `appDataFolder`.
- **Proxy:** Node + Express + `@google/genai` (Gemini) — `server/`. Deployed on **Render**.
- **Layout:** monorepo — web in `web/`, proxy in `server/`, Android in `app/`.

## ⭐ Most important — I am a student learning while building

This project is for me to **learn**, not just to ship. **Explain everything you do and the
rationale, and go one step at a time.** For every change:

- **Narrate before you act.** Say what you're about to do and *why* before doing it.
- **Go ONE step at a time, and stop.** After each step, wait for me to confirm I understand
  before moving on. Do not batch steps or run ahead.
- **Teach the why and the trade-offs** — why this approach over the alternatives.
- **Define terms of art the first time** (CORS, environment variable, OAuth, base64,
  cold start, quota, SDK, etc.) in plain language.
- **Walk through commands/files line by line** for anything I haven't seen before.
- **Surface decisions instead of silently picking** — lay out options + your recommendation,
  let me decide.
- **I'm on Windows + VS Code + PowerShell** — give PowerShell commands. For browser/dashboard
  steps (Render, Google Cloud), talk me through what to click and why.
- **Flag anything irreversible, and anything involving secrets/keys, before doing it.**

A change I don't understand is not "done". The teaching is part of the task.

## Phase roadmap (YJ's side)

| Phase | Status | What |
|---|---|---|
| 0 | ✅ Done | Google sign-in (GIS token flow) + Drive `appDataFolder` sync (`findFileId`/`saveData`/`loadData`/`ensureToken`) + basic task tracking + schema **v2** (MS1) |
| 1 | ✅ Done | Timetable **image capture** — education-level dropdown, image picker, preview, base64 |
| 2 | ✅ Done | Timetable parsing **proxy** — Express + `@google/genai`, `POST /parse-timetable`, level-aware `buildPrompt()` |
| 3 | ✅ Done | **Deploy proxy to Render** + wire web to live proxy + 3-min timeout fix (2026-06-16) |
| 4 | ✅ Done | **Timetable robustness & accuracy** — image downscaling, per-error UX, 4/5 levels tested (uni/JC/poly/secondary), **odd/even week support**. (`primary` sample still untested.) |
| 5 | ✅ Done | **Manual-correction / entry screen** — level-aware editor (module/slot cards + `week` dropdown), validation, parse-failure fallback to manual entry |
| 6 | ✅ Done | **Timetable display UI** — weekly **calendar grid** (`web/timetableView.js`): MON–FRI × hourly, session blocks, **odd/even toggle**, current-day highlight, "Re-upload timetable". Rough/functional (white blocks; colour-coding + polish deferred to Phase 12). *Old read-only list still rendered — remove later.* |
| 7 | ✅ Done | **Calendar — tasks** — month-grid calendar (`web/calendarView.js`): Monday-first grid, tasks as pills on their due-date cells (capped at 3 + "N more"), click a day for a view-only popup, month nav (prev/next arrows + month/year picker popover), today highlight, done-task strikethrough. Subscribes to `modulo:datachanged`. (2026-06-23) |
| 8 | ✅ Done | **Dates in the timetable** — dated weekly grid (`web/timetableView.js`): real Mon–Fri dates + week number from a **term-start anchor**, **term end** + **recess/holiday ranges** (break-aware numbering — breaks skipped so odd/even parity continues), **week navigation** (prev/next bounded by term + "This week"), **parity-driven odd/even slots** (manual toggle is the no-anchor fallback), **date-accurate today highlight**, exact-duration session blocks, hour-line alignment fix. Interim term-setup inputs (`termStart`/`termEnd`/`breaks` on `appState`) move into the handbook (Phase 13). (2026-06-24) |
| 9 | ✅ Done | **Task management — filtering + sorting + simple UI** (`web/task.js`). Add-Task **modal** (`#taskModal`); non-mutating `getVisibleTasks()` = filter (status/type/module) + sort (due/type/newest), filter dropdowns built dynamically from current tasks; **bucketed list** (Overdue / Due-this-week / Later / Completed, break at 7 days) with relative-date pills (`Today`/`Tomorrow`/`Yesterday`/date). Layout upgrade: **checkbox rows** (replaced Done/Undo button) + **right-aligned filter/sort toolbar** (colours/fonts deferred to Phase 12). (2026-06-24) |
| 10 | ✅ Done | **Study-time tracking + simple UI** (`web/studyTimer.js`). Count-up **stopwatch** (Start / Pause / **Stop & Save**, no Reset), **timestamp-based elapsed** (interval only repaints — survives tab-throttling). On Stop & Save a **rating modal** (1–5, or Skip→`null`) records a session `{ id, start, end, durationMins, rating, createdAt }` to `appState.studySessions` (schema additive, no version bump). Derived-live UI: **today / this-week / cumulative** totals + a recent-sessions list (sub-minute sessions show seconds). Calendar shows each day's **rounded average rating** as stars (`web/calendarView.js`). (2026-06-25) |
| 11 | ⏸ Deferred | **Cross-device sync demo (web ↔ app)** — *was* the MS2 headline; **parked** (blocked on Ling Song's Android Drive read/write, `AuthenticationHelper.kt`). Picked up once the Android side can read/write `modulo-data.json`. |
| 12 | ✅ Done | **Core app UI** (the SPA shell + full visual polish). **Shell:** `web/router.js` hash routing (`#dashboard`/`#calendar`/`#tasks`/`#timetable`/`#study`/`#settings`), persistent **left sidebar** (logo · nav · Modules list with colour dots · account chip) + a **top bar** (status link + global **+ Add Task**). **Dashboard** (`web/dashboard.js`): term-week eyebrow, time-of-day greeting, summary, **study-streak + this-week-hours** cards, **today's schedule** + **tasks-due-soon** panels (bucketed: Overdue/Due-this-week), and **Modules** cards → detail modal (notes placeholder + that module's tasks) with a **Manage** popup to hide modules (`appState.hiddenModules`). **Blue design-token theme** + **light/dark mode** (`web/theme.js`, `[data-theme]`, localStorage). **Inter** font, unified card `--shadow`, hue-aware **module colour palette** (`web/sidebar.js` `moduleColor`). **Tasks polished** (`web/task.js`: per-bucket cards, module dots, accent pills, group-by sort). **Timetable restructured (12.5):** grid-first, setup (level + term dates) moved to **Settings**, **Edit** + **Upload** as modals, empty state, redundant read-only list removed. (2026-06-27) |
| 13 | ✅ Done | **Handbook / onboarding** (`web/handbook.js`). First-run **non-dismissable modal** captures **education level** (then **locked** — immutable, shown as read-only text afterward), a **level-aware academic year** (uni/poly `"25/26"` vs school `"2026"`, with a live "Will show as…" preview), **semester**, and **term dates** (start/end + recess). New additive `appState` fields `academicYear`/`semester`/`handbookSetup` (defaulted + migrated: pre-13 files with a level set are treated as set up; no `schemaVersion` bump; documented for Ling Song). Settings "Timetable setup" card → **read-only Handbook summary + Edit** popup (recess editor relocated into the modal, same ids; old `#eduLevel`/`#termStart`/`#termEnd` listeners removed). **Sidebar header** "HANDBOOK · AY…" from `handbookHeaderLabel()` (one shared formatter). **Polish:** **Lucide icons** (CDN + `data-lucide`, `web/icons.js`, theme-aware via `currentColor`, deferred first draw) across nav/buttons/modal-closes/theme-toggle (moon/sun); study **Start/Pause merged into one play/pause toggle**; Add-Task module field is now a **dropdown of parsed modules + "+ Add other…"**. (2026-06-27) |
| 13.5 | ✅ Done | **Multiple handbooks** (`web/logic/handbooks.js` + `handbook.js`). A handbook = one semester's context (`HANDBOOK_FIELDS`: level, AY/sem, term dates + breaks, timetable, tasks, hiddenModules, handbookSetup); the **flat fields stay = the ACTIVE handbook** (Android unchanged) with additive `handbookId` + `otherHandbooks[]` (no version bump; migrated on load). **"Start new semester"** snapshots the current handbook + reuses the first-run modal as onboarding (year pre-filled to the current year); Settings **handbook list** with atomic **Switch** (pure `switchHandbook` → ONE `persist()`, shared `withOverlay` loading screen ≥400ms) + **delete** for stored handbooks (confirm names label + task count); education level now **LOCKED per handbook** (confirm-on-change removed). `studySessions` stay **global** (fuel Phase 14). 8 unit tests (25 total). (2026-07-05) |
| 14 | ✅ Done | **Study city — logic** (`web/logic/growth.js`). **Generative grid city** (redesigned 2026-07-08 MID-phase: the first fixed-12-stage + Upgrade-button + EXP-bar design was fully built through UI step 3, then superseded — see branch git history). Pacing `t = 2x+10` mins (n upgrades = `n²+9n` total; float-safe integer inverse); **centre-weighted single pick** (weight `(R−d+1)³` by Chebyshev ring — CUBED 2026-07-08, was ², for a stronger downtown; the exponent is a contract value; picked plot empty → spawn, occupied → grow a floor; founding building always dead-centre); land tiers 5×5 → 7×7 @20h → 9×9 @100h, floor caps 5/8/12; upgrades **AUTOMATIC** (reconcile-on-redraw — no button, no numbers ever shown). Stored: ONE additive global field `city: {buildings: [{x,y,floors}]}` (random placement isn't re-derivable; every count derived). RNG **injected** → deterministic unit tests incl. a **tier-capacity invariant** that guards future floor-cap tuning (12 tests; 37 total). Rewrote `docs/study-city-growth.md` contract (7 rules + Kotlin sketch + shared test vectors) for Ling Song's Android mirror. (2026-07-08) |
| 15 | ✅ Done | **Study city — UI** (`web/cityView.js` + `web/cityScene.js` + `web/citySchemes.js`). Lives INSIDE the Study Session tab (one tab, decided mid-phase; timer totals moved to a side card). `cityView` = the one impure caller: auto-reconciles pending upgrades (assign-before-await, persist ONCE, pending-0 loop guard) then draws. `cityScene.renderScene(city, tier)` = **programmatic isometric SVG** — 2:1 projection, painter's-order depth sort, git-city stacked towers (one hue token per building via coordinate hash `(x·31+y·17)%5`, faces shaded by translucent black/white overlays, window columns lit at night, 22px floors, antennas on 5+ floors, waves), **auto-zoom camera** (viewBox computed from island + tallest tower). **Pop-in animation** for new construction (`data-plot` diff, staggered scaleY via `transform-box: fill-box`). **6 colour schemes** (Classic / Dreamland / Ocean / Sunset / Ember / Midnight) restyling buildings AND land/sea (+ per-scheme window override — Ember's white-hot); retro HUD stepper "▶ NAME i/6" (Press Start 2P); **per-DEVICE localStorage pref — NOT in the schema/contract** (only the coordinate hash is shared, so colour LAYOUT matches cross-device). Final polish: dark outer edge strokes on towers (silhouette), land outlines (sand + grass), 12-wave varied sea; footprint kept uniform (variety tried + reverted). (2026-07-08) |
| 16 | ✅ Done | **Grade calculator — logic** (`web/logic/gpa.js`, pure). **Schemes as data:** `nus5` (5.0 scale, NUS/NTU-style — **SMU's 4.3 scale NOT supported**, documented) + `poly4` (4.0, SP's core table incl. DIST); `schemeForLevel` maps the stored level values (**`"poly"` not `"polytechnic"`** — caught mid-phase by cross-checking the schema doc after the tests had passed with the same wrong string) and stubs jc/secondary/primary with a reason. `computeGPA(entries, scheme)` = one generic `Σ(pts×credits)/Σ(credits)`: grades normalised (trim+uppercase), **S/U/CS/CU + poly P excluded** from numerator AND denominator (S/U stored as the literal transcript grade — decided over an `su` flag), junk rows **skipped + counted, never thrown** (trust boundary — Android writes the same file), `gpa: null` (not 0) when nothing counts, full precision (UI rounds). `cumulativeGPA(state)` pools active + **same-scheme** `otherHandbooks` grades into ONE weighted average (NOT an average of per-semester GPAs). **Schema (additive):** per-handbook `grades: []` (`{ id, module, credits, grade }`) — in `HANDBOOK_FIELDS` + `blankHandbook` so the 13.5 swap carries it; `data.js` default + shape-checked migration; schema doc "Grade object" section + Kotlin `Grade`. 17 tests (54 total). (2026-07-13) |
| 17 | ✅ Done | **Grade calculator — UI** (`web/gradesView.js`). `#grades` routed view (nav link + `.view` section = the whole route registration). **Two live stat cards** (semester + cumulative GPA — 2-dp at DISPLAY only, `null` → "—", "5.0 scale" pill per the SMU-limitation labelling). **Rows editor with PHANTOM rows:** every timetable module always SHOWN, a row only STORED once it has a grade (clearing to "—" deletes it; storage stays clean); credits pre-fill **4 (uni) / blank (poly)**; **live persist per `change`** (not per keystroke — focus); `<optgroup>`-sectioned grade dropdown; "+ Add module" (session-only `extraModules` Set until graded); **× only on ungraded manual rows** (grade "—" is the delete elsewhere). **S/U ELECTION — REVERSED 16's option A:** additive `su: boolean` on Grade (absent = false; only the literal boolean `true` elects — trust boundary) + `suElection` scheme flag (nus5 only) → checkbox column, letter KEPT + row excluded; S/U left the dropdown (CS/CU stay); literal "S"/"U" grades valid forever; schema doc + Kotlin + Ling Song heads-up amended. **Semester history** (YJ mid-phase request): read-only per-handbook GPA list — own scheme per row, scale noted when mixed, chronological (`parseStartYear`), "current" chip, renders even when the active level is unsupported. Hint line only for graded-but-skipped rows (YJ cut the phantom hint); **dashboard teaser card CUT** (YJ). 57 tests. (2026-07-14) |
| 18 | Planned (MS3) | **Grade calculator — advisor** (logic) — grades needed to reach a target GPA |
| 19 | Planned (MS3) | **Grade calculator advisor — UI integration** — target input + suggestions display |
| 20 | Planned (MS3) | **Notes & file sync via Drive + simple UI** — upload/list/open notes synced through `appDataFolder`. *(Moved from MS2 → MS3 on 2026-06-21.)* |
| 21 | Open | Deferred — local→Drive migration on first connect + conflict resolution (last-write-wins today) |

**Milestones:** MS1 (1 Jun) done · MS2 eval (29 Jun) done · **MS3 — 27 Jul 2026** (Phases 13.5 + 14–20, in that order).

**Strategy (decided 2026-06-21; updated 2026-06-25):** build feature **logic + rough functional UI**
for everything first (Phases 6–10), then the **polished designed UI + routing** (Phase 12) and the
**handbook** (Phase 13). Polish-last on purpose — de-risk the logic, avoid throwaway UI. Keep each
feature usable/demoable as we go. **The cross-device sync demo (Phase 11) is deferred** (2026-06-25) —
it's blocked on Ling Song's Android Drive read/write, so it no longer gates the MS2 UI work; pick it
up when the Android side is ready. *(Notes & file sync moved to MS3, Phase 20, on 2026-06-21.)*

**MS3 strategy (decided 2026-07-05):** **multiple handbooks (13.5) ship first**, then 14–20 in
order. Scope decisions: per-handbook data = education level, AY/semester, term dates + breaks,
timetable, tasks, hidden modules; **`studySessions` stay GLOBAL** (streaks + cumulative study time
carry across semesters — they fuel the Phase 14 motivator). Handbooks are **switchable and fully
editable** — the feature is a per-semester context switcher (decided 2026-07-05; supersedes the
earlier read-only idea). Schema change is **additive** — the existing flat fields keep meaning
"the active handbook", so the Android app keeps working unchanged.

## MS3 phase plans (proposed 2026-07-05 — for YJ's review)

> Draft plans for every not-yet-done phase. Read top-to-bottom, comment on anything —
> each plan lists its open questions. Nothing here is built yet.

### Phase 13.5 — Multiple handbooks (first)

- **Goal:** handbooks are per-semester **contexts you can switch between**. "Start new semester"
  stores the current handbook and onboards a fresh one; a switcher in Settings makes any previous
  academic year/semester active again — **fully editable** (timetable, tasks, term dates), exactly
  like today's single handbook. *(Updated 2026-07-05: supersedes the earlier read-only-archive idea;
  no viewer needed — to look at an old semester you just switch to it.)*
- **Schema (additive, no version bump):** each handbook = `{ id, educationLevel, academicYear,
  semester, termStart, termEnd, breaks, timetable, tasks, hiddenModules }`. The existing **flat
  fields stay = the active handbook** (Android unchanged); new `appState.otherHandbooks: []` holds
  the inactive ones, and a flat `handbookId` identifies the active one. **Switching** = push the
  flat fields into `otherHandbooks`, pull the chosen entry out into the flat fields, ONE
  `persist()`. Document in `docs/modulo-data-schema.md` + heads-up to Ling Song.
- **UI:** Settings → Handbook card becomes a **handbook list** — one row per handbook (label from
  `formatHeaderLabel`, active one marked) with a **Switch** action, plus **"Start new semester"**
  (opens the handbook modal fresh, first-run style).
- **Level locking lands here** (the documented plan): the education level is chosen when a handbook
  is created and then **locked for that handbook** — the Edit modal shows the existing hidden
  locked-text field (`#hbLevelLockedField`) again instead of the dropdown. Different handbooks can
  have different levels (e.g. a JC handbook, then a university one).
- **Steps:** (1) pure snapshot/swap helpers in `web/logic/handbooks.js` + unit tests →
  (2) schema defaults/migration (existing single-handbook files get an `id` + empty
  `otherHandbooks`) + schema doc → (3) Start-new-semester flow → (4) handbook list + Switch →
  (5) lock the level per handbook → (6) Ling Song heads-up.
- **Care points:** the swap must replace ALL the flat fields in **one** `persist()` — atomic, so
  no view ever sees half of one semester and half of another; **`studySessions` are global and
  must NOT be swapped**; the sidebar "HANDBOOK · AY…" header and dashboard week math follow the
  flat fields automatically (free once the swap is atomic).

### Phase 14 — Gamified study motivator (logic)

- **Goal:** map **global, all-time** study minutes (`studySessions`, across semesters) to a
  growth stage for the virtual city/garden.
- **Design:** pure `web/logic/growth.js` — a thresholds table (e.g. stage 0 at 0h → stage N at
  100h+) and `growthState(sessions)` returning `{ stage, stageName, totalMins, nextThresholdMins,
  progressPct }`. Fully **derived** from `studySessions` — nothing new stored (same
  derive-don't-store rule as the study totals), so it can't drift and needs no schema change.
- **Steps:** agree the growth curve → implement + unit tests → expose to the Phase 15 UI.
- **Decided (2026-07-05): it's a CITY.** Stage count, art style, and whether streaks give a
  bonus are deliberately left open — design the growth curve together when this phase starts.

### Phase 15 — Gamified study motivator (UI)

- **Goal:** the visual screen for the motivator.
- **Design:** a new routed view (`#garden` or `#city`, new sidebar nav link + Lucide icon) —
  stage artwork as **inline SVG per stage** (token/theme-aware via `currentColor`/CSS vars, no
  image assets), a progress bar to the next stage, and "Xh until your next upgrade". Redraws on
  `modulo:datachanged`. Optional: a small teaser card on the Dashboard.
- **Steps:** view shell + route → stage art → wire `growthState` → (optional) dashboard card.

### Phase 16 — Grade calculator core (logic)

- **Goal:** per-school GPA computation.
- **Design:** pure `web/logic/gpa.js` — per-level grading **schemes**: university = NUS 5.0 GPA
  (letter → points, **S/U excluded** from the average), poly = 4.0 GPA. Each scheme = a
  grade→points map + credit weighting; `computeGPA(entries, scheme)` over
  `[{ credits, grade }]`. Unit tests with known worked examples.
- **Schema (additive):** `appState.grades: []` as part of the **active handbook's flat fields**
  (so each handbook keeps its own grades — the 13.5 swap carries them) — `{ id, module, credits,
  grade }`. Document + tell Ling Song.
- **Decided (2026-07-05):** university + poly first; JC (rank points) and secondary (L1R5)
  stubbed with a clear "not yet supported" message, added later if time allows.
- **Cumulative GPA across semesters (Ling Song, 2026-07-07):** the overall GPA must span the
  **whole duration of study**, not just the active semester — computed live over the active
  handbook's grades **plus** every entry in `otherHandbooks` (grades ride their handbook, so
  13.5 already preserves the history). Rule: only **same-scheme** handbooks combine (levels are
  locked per handbook, so each semester's scheme is unambiguous; a JC handbook contributes
  nothing to a university GPA).

### Phase 17 — Grade calculator (UI)

- **Goal:** enter modules/grades, see the GPA live.
- **Design:** a new `#grades` routed view — one row per module (pre-seeded from the parsed
  timetable's modules), credits input + grade dropdown (options from the active scheme),
  add/remove rows, and a live GPA stat card. Same editor pattern as the timetable editor
  (build from state → edit → harvest → persist).
- **Steps:** view + route → row editor bound to `appState.grades` → live GPA cards
  (**this semester** + **cumulative across all same-scheme handbooks**) →
  (13.5 tie-in) each handbook carries its own grades, so switching semesters switches the
  semester GPA while the cumulative one stays put.

### Phase 18 — Grade advisor (logic)

- **Goal:** "what do I need to reach a target GPA?"
- **Design:** pure `web/logic/advisor.js` — inputs: current grade entries, target GPA, planned
  remaining credits; output: required **average grade points** over the remaining credits +
  a feasibility flag (required average > max grade = not achievable) + the nearest achievable
  target. Straight weighted-average algebra; unit tests incl. edge cases (no remaining credits,
  already above target).
- **Schema:** none — advisor results are computed on the fly, never stored.

### Phase 19 — Grade advisor (UI)

- **Goal:** target input + suggestion display.
- **Design:** an "Advisor" card inside the `#grades` view (not a separate route): target-GPA
  input + remaining-credits input → result panel ("average **A-** over your remaining 20 MCs",
  or "not achievable — closest is 4.72"). Reuses the validation-message pattern from the
  handbook modal.

### Phase 20 — Notes & file sync via Drive (+ simple UI)

- **Goal:** upload/list/open notes, synced through the same `appDataFolder`.
- **Design:** notes live as **separate Drive files** next to `modulo-data.json` (so they don't
  bloat the JSON or fight Phase 21's conflict story), tagged with Drive `appProperties`
  (e.g. `module: "CS2030S"`). New helpers in `drive.js`: multipart **upload** (metadata + bytes),
  **list** filtered by appProperties, **download/open** as a blob URL. UI: the module detail
  modal's "My notes" placeholder becomes real (upload + list + open), **plus a general
  "All notes" routed view** (`#notes`, own sidebar link) listing every note with a module filter
  *(decided 2026-07-05)*.
- **Care points:** `appDataFolder` files count against the **user's own Drive quota** — keep an
  upload size cap (~5 MB?) and clear errors; opening needs a blob URL + `URL.revokeObjectURL`.
- **Still open:** which file types first (suggest PDFs + images).

### Phase 11 (still deferred) — cross-device sync demo

Unchanged: blocked on Ling Song's Android Drive read/write (`AuthenticationHelper.kt`). When his
side can read/write `modulo-data.json`, run the demo script: edit on web → reload on Android →
edit on Android → Reload-from-Drive on web. No web work expected beyond bug fixes it shakes out.

### Phase 21 (open) — migration + conflict resolution

Post-MS3 unless sync testing forces it earlier: local→Drive **migration** on first connect
(today local data is simply ignored once Drive mode is chosen) and a better **conflict story**
than last-write-wins (at minimum: compare `updatedAt` and warn before overwriting newer data).

## Conventions and guardrails

- **Keep the app working and commit at the end of every step.** Standing discipline.
- **Never commit secrets.** `server/.env` is gitignored and holds `GEMINI_API_KEY`. The key
  lives in exactly two places: `server/.env` (local) and the Render env var. Never in code,
  a commit, or chat. Don't print the real key back to me.
- **Don't break the `modulo-data.json` schema contract** without coordinating with Ling Song
  — the web writes what the app reads and vice versa. Field-name drift = silent data loss.
- **Respect ownership:** work in `web/` and `server/`. Don't edit `app/` (Ling Song's).
- **Ask before anything destructive/irreversible** — force-push, deleting files, rewriting
  git history, rotating keys. Explain the consequence and wait.
- **Current working branch:** `feature/GPA-calculator-web` (Phases 16 + 17, ✅ both built —
  cut from `feature/study-city-web`, whose 14+15 PR was **not yet merged** at branch time,
  so this branch's PR shows the study-city commits until that PR merges). Main branch:
  `main` (web deployed to GitHub Pages — https://yujiankoh.github.io/modulo/). Next up is
  **Phase 18 (grade advisor — logic)**, new branch per phase. ⚠️ Render still auto-deploys the **proxy** from `feature/timetable-ai-web`
  (now behind `main`), so a `server/` change must be pushed there (or repoint Render's deploy
  branch to `main`). 14+15 were web-only — no proxy work.

## Critical: Gemini free-tier quota is only 20 parses/day

Free tier = **20 `gemini-3.5-flash` `generateContent` requests per day, per project**
(`GenerateRequestsPerDayPerProjectPerModel-FreeTier`). Exceeding it returns **`429
RESOURCE_EXHAUSTED`**. Resets at midnight US Pacific (~mid-afternoon SGT). **Test calls and
diagnostic scripts spend this quota too — be sparing.** **Decision (2026-06-16): staying on the free 20/day — no billing for now.** Mitigation:
spread testing across days, coordinate the shared quota with Ling Song, and **warm +
sanity-check right before the 29 Jun demo** so a `429` doesn't hit live. (If it gets painful,
enabling billing on `modulo-0326` lifts the cap — Gemini Flash is cheap, ~a cent or less per parse.)

## Critical: telling proxy failures apart (read the Render logs)

The browser only shows a generic message; the real error is in the Render **Logs** tab. Map:

- **`Failed to fetch`** → browser couldn't reach the server (server down / wrong URL / CORS).
- **`429 RESOURCE_EXHAUSTED`** → daily quota hit (above). Wait for reset or enable billing.
- **`504 DEADLINE_EXCEEDED`** → Gemini's backend was slow; usually transient. Retry; lighten
  the request (smaller image).
- **`AbortError: This operation was aborted`** → *our* timeout fired. The `@google/genai`
  client defaults to a **1-minute** timeout; we raised it to **3 min** via
  `httpOptions: { timeout: 180000 }` at the `new GoogleGenAI(...)` call. Don't remove that.

## Critical: Render deployment

- **Root Directory = `server`** (monorepo — without it Render installs at repo root and fails).
- Build `npm install` · Start `npm start` · Instance **Free** · Region **Singapore**.
- `GEMINI_API_KEY` is set as a **Render env var** (cloud equivalent of `.env`).
- **Auto-redeploys on every push to `feature/timetable-ai-web`.** So shipping a proxy change
  = edit → commit → push → wait for "Live".
- **Cold start:** free tier sleeps after ~15 min idle; first request then takes ~30–50s. Wake
  it before a demo.
- Live endpoint: `https://modulo-proxy.onrender.com/parse-timetable` (POST-only; a bare GET
  shows "Cannot GET /", which is correct).

## Critical: the WEB front-end is NOT deployed yet (do before MS2)

Only the **proxy** is hosted (Render). The **web app (`web/`) has never been deployed** — it's
only run via VS Code Live Server. A tester **cannot** just open `index.html` from disk:
**(1)** ES modules (`<script type="module">` + `import`) are blocked over `file://` (CORS) → blank/broken page;
**(2)** Google Sign-In only works from **authorised JavaScript origins** on the OAuth client
(currently ~`localhost:5500`) — `file://` isn't one, so Drive auth fails.
**To-do before 29 Jun:** deploy `web/` to a static host (GitHub Pages recommended — repo already
on GitHub; or Netlify/Vercel/Render Static Site), hand the tester the **URL**, and **add that URL
to the OAuth client's Authorised JavaScript origins** in Google Cloud (`modulo-0326`). Mind the
GitHub Pages base-path quirk (project sites serve under `/<repo>/`).

## Critical: the model name is the line most likely to rot

`MODEL = "gemini-3.5-flash"` (SDK `@google/genai` v2.7.0, supports `thinking`). If a parse
500s with a model error, check this first.

## Design decisions captured

- **Web is a vanilla-JS Single-Page App (SPA) — decided 2026-06-16.** One `index.html`
  shell; JavaScript swaps views (each "page" a hidden `<section>`/template via a
  `showPage()`-style switcher) with **hash-based routing** (`#tasks`, `#calendar`, …) so
  refresh/back/forward work. **Why SPA:** the Google access token (`ensureToken`) and the
  in-memory `appState` would be wiped by a full MPA page reload, forcing re-login +
  re-download of `modulo-data.json` on every navigation. **No framework** (React/Vue) for
  now — stay vanilla through MS2; a framework is only a deliberate later phase, if ever.
  **Routing/view-switching is built in Phase 12** (core app UI), per the 2026-06-21 strategy.
  Until then, rough feature UIs (Phases 6–11) are added to the current page; build a minimal
  `showPage()`-style view-switcher when the page gets crowded, and the polished routed shell
  in Phase 13. **Add features as views, NOT new `.html` files.** YJ is new to web dev — walk
  through view-switching/routing step by step.
- **Dashboard + left-nav is the Phase 12 design target (decided 2026-06-25).** There's an
  agreed mockup: a persistent **left navigation bar** (Dashboard · Calendar · All Tasks ·
  Timetable · Study Session · Settings, with a Modules list and a "Handbook · AY/sem" header)
  and a **Dashboard** landing page — greeting, **study-streak** + **this-week hours** cards
  (both come from Phase 10's `studySessions`), **today's schedule** (from the timetable), and
  **tasks-due-soon** (from `tasks`). The nav links become the SPA routes (`#dashboard`,
  `#calendar`, `#tasks`, `#timetable`, `#study`). The existing feature modules
  (`task.js`/`calendarView.js`/`timetableView.js`/`studyTimer.js`) get **moved into these
  routed views** — same logic, real layout. This is the polish pass the rough UIs were
  deferring to.
- **Blue design-token theme + light/dark mode (decided 2026-06-26/27).** All colour lives in
  **CSS custom properties** on `:root` (`--bg`/`--surface`/`--primary`/`--text`/`--border`/
  `--pill-bg`/`--hover`/`--today`/`--shadow`); components only ever use `var(--…)`. **Dark mode**
  is the *same tokens* re-valued under `[data-theme="dark"]`, toggled by `web/theme.js` (sets
  `data-theme` on `<html>`, persists to `localStorage` **per device**, applied before paint by an
  inline `<head>` script to avoid a flash). **Rule:** never hardcode a colour — add/extend a token.
  **Module colours** are JS-driven (inline), so they have a light + a brighter dark palette in
  `sidebar.js` (`moduleColor`), and the theme toggle fires `modulo:datachanged` to re-apply them.
- **Settings is the interim home for timetable setup (decided 2026-06-27).** Education level +
  term start/end + recess inputs live in a Settings "Timetable setup" card until the **handbook
  (Phase 13)** owns them. Education level there is still a free dropdown; the handbook makes it
  **set-once / locked** (see the handbook decision above).
- **Handbook & education level — LOCKED PER-HANDBOOK (updated 2026-07-05 in Phase 13.5;
  was temporarily editable for the MS2 showcase).** The education level is chosen while a
  handbook is being **created** (first run / "Start new semester" — `handbookSetup` still
  false) and then **locked for that handbook**: the Edit modal shows the read-only
  `#hbLevelLockedField` instead of the dropdown. Why: the level drives the timetable
  schema/parsing rules *and* the level-aware editor (`web/timetableEditor.js`), so changing
  it mid-handbook could leave a timetable that doesn't match. Wanting a different level =
  start a new semester (different handbooks can have different levels — e.g. JC then
  university). The MS2-era `confirm()`-on-change path was removed with the dropdown.
- **Accuracy over speed (decided 2026-06-16).** Tried `thinkingConfig.thinkingBudget` to
  speed up parsing; it cut accuracy too much. **Keep NO thinking budget** (dynamic default =
  most accurate). The planned manual-correction screen (Phase 5) covers the remaining misses.
- **Image downscaling is the right speed lever** (Phase 4), not thinking budget — cap the
  image's longest side (~1500px) in the browser before base64 so parses are lighter/faster.
- **`teacher` dropped from the schema — decided 2026-06-16.** No `teacher` field anywhere
  (web JSON, schema v2, Kotlin `Slot`). The per-level prompts no longer ask for it, and
  `location` captures the room/venue only. Ling Song informed.
- **Schema v2 field names are aligned** — proxy, web, and Kotlin `Slot` all use
  `sessionType` + `classNo`. (The earlier hand-off claim that Kotlin used `type` / lacked
  `classNo` was already resolved in schema v2.)
- **Conflict resolution is last-write-wins** for now (Phase 21 revisits).

## Useful commands

Web (repo root) — served by VS Code **Live Server**:

```
# Right-click web/index.html → "Open with Live Server"  (http://localhost:5500 / 127.0.0.1:5500)
```

Proxy (`server/`):

```powershell
cd server
npm start              # run the proxy locally (http://localhost:3000)
```

Deploy the proxy:

```powershell
git add server/index.js
git commit -m "..."
git push               # → Render auto-redeploys feature/timetable-ai-web
```

## Where things are

- `web/app.js` — auth + Drive sync (`findFileId`/`saveData`/`loadData`/`ensureToken`),
  in-memory `appState` task logic, `PROXY_URL` + the Parse-button handler.
- `web/index.html`, `web/style.css` — web UI. **Phase 12:** `index.html` is the SPA shell
  (`#app` grid → `#sidebar` + `#main` with `.view` sections + global modal overlays);
  `style.css` is **design-token driven** (`:root` light + `[data-theme="dark"]` dark; one
  `--shadow`; an inline `<head>` script applies the saved theme before paint).
- `web/router.js` — Phase 12 **SPA router**: hash → which `.view` shows; toggles `is-active`
  on the view + nav link; default `#dashboard`; reacts to `hashchange`.
- `web/dashboard.js` — Phase 12 **Dashboard**: eyebrow/greeting/summary, study-streak +
  week-hours cards, today's-schedule + tasks-due-soon panels, Modules cards + detail modal +
  the **Manage** (hide modules) popup. Reuses `currentWeekInfo`/`moduleColor`/`toggleTask`.
- `web/sidebar.js` — Phase 12 sidebar **Modules list** (colour dots) + **account chip**, and
  the shared **`moduleColor(name)`** (stable hash → light/dark palette).
- `web/theme.js` — Phase 12 **light/dark toggle**: sets `data-theme` on `<html>`, persists to
  `localStorage`, fires a redraw so inline module colours re-apply.
- `web/timetableEditor.js` — manual timetable editor; **Phase 12.5c** it's a **modal**
  (`openEditor` exported; opened by the grid header's **Edit** button).
- `web/calendarView.js` — Phase 7 month-grid **task calendar** (date cells, task pills,
  day popup, month nav + month/year picker, today highlight). **Phase 10:** also shows each
  day's **rounded average study rating** as stars (`avgRatingByDate`). Reads `appState`,
  redraws on `modulo:datachanged`.
- `web/studyTimer.js` — Phase 10 **study timer**: count-up stopwatch engine (timestamp-based
  elapsed), Start/Pause/**Stop & Save**, the 1–5 **rating modal**, records to
  `appState.studySessions`, and the derived **today/week/cumulative totals** + recent-sessions
  list. Side-effect-imported in `main.js`; redraws the list/totals on `modulo:datachanged`.
- `web/timetableView.js` — weekly **timetable grid**. Phase 8 made it a **dated** view:
  term-start/end + recess `breaks` drive the week number, navigation, odd/even parity, and a
  date-accurate today highlight (`weekInfo()` is the date brain; `viewedMonday` is the shown
  week). Falls back to an undated grid + manual odd/even toggle when no term start is set.
- `web/timetable.js` — timetable image pick/downscale/parse. **Phase 12.5d** the upload UI is a
  **modal** (`startReupload` opens it; the empty-state "Upload" + grid "Re-upload" trigger it).
  The **interim setup** inputs (`educationLevel`, `termStart`, `termEnd`, `breaks`) now live in a
  **Settings → "Timetable setup"** card (still `appState`/`persist`, same ids) — they move to the
  handbook (Phase 13).
- `server/index.js` — Express proxy: `buildPrompt(level)`, `POST /parse-timetable`, port
  binding (`process.env.PORT || 3000`), 3-min timeout.
- `server/.env` — `GEMINI_API_KEY` (**gitignored**).
- `app/` — Ling Song's Android app. **Don't edit; coordinate.**
- `shared/` — placeholder for cross-platform definitions.
- `docs/` — **committed (Ling Song-facing):** `modulo-data-schema.md` (v2 contract) +
  `testing.md` at top level; `guides/` holds `study-city-growth.md` (city contract)
  + `timetable-parsing-app-guide.md`. **Local-only (never `git add`):** `roadmaps/`
  (phase roadmaps + `study-city-math.md`; also the formerly-tracked
  `multi-handbook-roadmap.md` + `timetable-ai-roadmap.md`, untracked 2026-07-13),
  `handoffs/` (session handoffs), `notes/` (`learning-notes.md`, `web-code-guide.md`),
  `report/` (challenges, dependencies, poster, video script). `modulo-handoff.md`
  (repo root) — full context.

## Key config reference

- Google Cloud project: `modulo-0326`
- OAuth web client ID (public, committed): `332114614658-87cqh1e2u8luh9b5q15sf22sb30i3nda.apps.googleusercontent.com`
- Drive scope: `https://www.googleapis.com/auth/drive.appdata`
- Gemini: SDK `@google/genai` v2.7.0, model `gemini-3.5-flash`, key `GEMINI_API_KEY`
- Hosting: Render (free tier, Singapore), service `modulo-proxy`
