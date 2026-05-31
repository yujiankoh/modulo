# MODULO — Roadmap & Remaining Work

A checkpoint of where the project stands and what's left, grouped by owner
(YJ = web, LS = Ling Song / Android, Shared) and mapped to the Orbital milestones.

> Note: the "left to do" below is built from the Orbital proposal's milestones plus
> the work done so far. Sanity-check it against what M1 *actually* requires — if you
> spot a mismatch, adjust.

---

## ✅ Done (web backend — the hard part)

- [x] Architecture decision: **browser-first, no server** (Google Drive is the backend).
- [x] Google Cloud setup: project access (shared with LS), Drive API enabled, OAuth web
      client + JavaScript origins, test users.
- [x] Web Google sign-in (Google Identity Services token flow).
- [x] Drive read/write to the shared `appDataFolder` (`findFileId` / `saveData` / `loadData`).
- [x] Task data layer: add / toggle / delete, rendered from one in-memory `appState`.
- [x] Versioned schema: `schemaVersion` + per-task `createdAt`/`updatedAt`.
- [x] Access-token expiry handling (`ensureToken` checks + refreshes before each Drive call).
- [x] Optional **local save** mode (`localStorage`) + remembers the user's mode choice.
- [x] Reference docs: setup notes, data schema spec.
- [x] Git: committed & pushed on the web feature branch.

---

## 🔧 In progress / immediate (YJ)

- [ ] Finish timetable **image capture** (education-level dropdown + image picker + preview +
      base64 prep). Test, then commit.
- [ ] Update the schema doc to include the new `educationLevel` field.

---

## 📋 Left to do — WEB (YJ)

**Timetable parsing (Gemini) — the one part that needs a backend**
- [ ] Decide where the Gemini proxy lives: serverless function (e.g. Google Cloud
      Functions / Vercel) vs small Node server. *Needed because the API key cannot live in
      browser code.*
- [ ] Get a Gemini API key (Google AI Studio, free tier).
- [ ] Build the proxy: receives image + education level → calls Gemini → returns structured
      JSON. (Verify current Gemini model names / structured-output format before coding.)
- [ ] Wire the "Parse timetable" button to the proxy; write the result into `appState.timetable`.
- [ ] **Manual edit / correction screen** after parsing (planned in the proposal) so users
      can fix any mis-parsed rows.

**Real UI (currently just a bare test harness)**
- [ ] Build the designed screens (Dashboard, Calendar, All Tasks, Study Session) from the
      mockups and wire them to the existing data layer. *Data plumbing is done — this is
      "draw `appState` nicely," not new backend work. Likely the largest remaining web effort.*

**Housekeeping**
- [ ] Open the **pull request** to merge the web feature branch into `main` (after LS review).

---

## 📋 Left to do — APP / Android (Ling Song)

- [ ] Implement Drive read/write in Kotlin (the `// TODO: do something with access token`
      in `AuthenticationHelper.kt`), built to the shared schema. **This unblocks the
      cross-device sync demo.**
- [ ] App-side timetable handling (read the parsed timetable from Drive; or do the Gemini
      call on the app — see the open question below).
- [ ] Build out the app UI screens from the mockups.

---

## 📋 Shared / team

- [ ] **Lock the data schema**, especially the timetable structure, once Gemini's actual
      output (per education level) is known. Currently provisional.
- [ ] **Cross-device sync test**: add a task on web → see it in the app, and vice versa.
      *This is the headline Milestone 1 deliverable to demonstrate.*
- [ ] Set-up / onboarding tutorial (proposal lists this under M2).

---

## ⏳ Deferred (intentionally — avoid before deadline)

- [ ] **Local → Drive migration on first connect.** When a local-first user connects Drive,
      upload local data *if Drive is empty*; if both sides have data, prompt instead of
      auto-merging (needs conflict handling, seeded by the `updatedAt` timestamp).
- [ ] Finer-grained sync conflict resolution (last-write-wins is fine for now).

---

## Milestone mapping (from the proposal)

**Milestone 1 (01 Jun) — target**
- Timetable AI reads a timetable → subjects/modules at 90% accuracy → *parsing not built yet
  (next step)*.
- Frontend UI/UX for app + web → *mockups done; real screens not wired yet*.
- Google Drive sync set up (multi-device) → ✅ web side done; ⏳ needs the Android read.
- Local save vs Google login → ✅ done on web.

**Milestone 2 (29 Jun)**
- Full design implementation, set-up tutorial, task add/delete/reminders, productivity/mood
  board, notes upload synced across devices.

**Milestone 3 (27 Jul)**
- Gamified study motivator (city-building), grade calculator.

---

## Honest priority read (deadline is tight)

The web **sync layer is done and demonstrable**. With limited time before M1, the
highest-value remaining items are, in order:

1. **Cross-device sync demo** — nudge LS to finish the Android Drive read so web↔app sync
   can be shown. This is the M1 headline and mostly depends on him, not you.
2. **Finish + commit the timetable image capture** (you're nearly there).
3. **Timetable parsing** — this is the big M1 question mark. It needs the Gemini proxy, so
   it's the most setup-heavy remaining piece. If M1 truly requires a *working* 90%-accuracy
   parse, this is the priority and should start immediately; if the app is doing parsing,
   the web may only need to *display* the result.

A *working, understood* sync demo + image capture beats a half-finished everything. The full
UI build-out and a hardened Gemini proxy realistically extend into M2.

**Open question that changes the plan:** is the Gemini timetable parsing meant to run on the
**web**, on the **app**, or either? That decides whether the web needs the proxy now, or can
just read a parsed timetable the app produced.
