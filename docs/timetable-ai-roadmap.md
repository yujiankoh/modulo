# MODULO — Timetable AI: Next-Steps Roadmap

> Living doc created **2026-06-16** after deploying the parsing proxy.
> Sections marked **❓ NEEDS YOUR INPUT** are decisions for YJ to fill in.
> Target: **MS2 eval — 29 Jun 2026** (~13 days out).

---

## ✅ Done today (2026-06-16)

- Proxy deployed to **Render** (free tier, Singapore): `https://modulo-proxy.onrender.com`.
  - Auto-redeploys on every push to `feature/timetable-ai-web`.
  - `GEMINI_API_KEY` stored as a Render env var (never in Git).
- Web app wired to the live proxy (`PROXY_URL` in `web/app.js`).
- **Timeout fix:** raised the `@google/genai` client timeout from its 1-min default to **3 min** (`httpOptions: { timeout: 180000 }`) — slow parses no longer get cut off.
- **Accuracy vs speed decision:** tried `thinkingBudget` to speed up parsing; it cut accuracy too much. **Decision: keep NO thinking budget** (dynamic/default) for best accuracy. Speed is "good enough," and the planned manual-correction screen covers the rest.

---

## 🚩 Blockers / urgent (do before the demo)

### 1. Gemini free-tier quota — **20 parses/day** (DECISION: staying on free tier)
- Free tier = **20 `gemini-3.5-flash` requests/day per project**, shared across web + app. Exceeding → `429 RESOURCE_EXHAUSTED`. Resets at midnight US Pacific (~mid-afternoon SGT).
- **Decision (2026-06-16): keep the free 20/day — no billing for now.**
- Mitigation (since the cap stays):
  - [ ] Be sparing with test parses; spread level-testing across multiple days.
  - [ ] **Warm + sanity-check right before the 29 Jun demo** so a `429` doesn't hit live.
  - [ ] Coordinate testing windows with Ling Song (shared quota).
  - Fallback if it gets painful: enabling billing on `modulo-0326` lifts the cap (revisit only if needed).

### 2. Tiny code cleanup
- [ ] Remove the now-stale comment on `server/index.js` line ~95 (still mentions "thinking budget" though it's gone). Cosmetic only.

---

## 🛠️ Timetable AI feature — remaining work

### A. Robustness
- [ ] **Image downscaling before upload** (`web/app.js`): cap longest side **~1600–2000px** before
      base64 (not lower — dense secondary grids have tiny text). → lighter/faster parses, fewer
      timeouts. After picking the cap, **verify accuracy on the dense MGS grid**. The right speed
      lever (vs thinking budget).
- [ ] **Error UX — a clear message for EACH failure** (map from status/error in `web/app.js`'s parse handler):

      | Error | What happened | Message to show |
      |-------|---------------|-----------------|
      | `Failed to fetch` | Can't reach proxy (down / offline / CORS) | "Can't reach the server — check your connection and try again." |
      | `429` | Daily quota used up (20/day) | "Parsing limit reached for today — please try again later." |
      | `503` | Model overloaded ("high demand") | "The parser is busy right now — try again in a moment." |
      | `504` / timeout / `AbortError` | Gemini too slow / our 3-min timeout fired | "That took too long — try again (a smaller/clearer photo helps)." |
      | `400` | Missing / invalid image | "Couldn't read that image — pick a clearer timetable photo." |
      | other 5xx | Server/parse error | "Something went wrong reading the timetable — try again or another photo." |

- [ ] **(Optional) Auto-retry with backoff** in the proxy on transient `503`/`504` so blips don't surface.
- [ ] Wake-on-demand before a demo (free Render sleeps after ~15 min idle → 30–50s cold start).

### B. Accuracy & schema
- [x] **`teacher` removed (2026-06-16).** Decided NOT to capture teacher. Done: dropped from the
      proxy prompts (`location` = room only now) + schema v2 + Kotlin `Slot`. Ling Song informed.
      ⚠️ The proxy prompt change needs **commit + push** to take effect on Render.
- [x] **Schema field names aligned.** Proxy + web + Kotlin all use `sessionType` + `classNo`
      (the old `type` mismatch was already resolved in schema v2). Nothing to rename.
- [ ] **Test all 5 education levels.** Only *university* + *secondary* tried. Get real
      *primary / jc / poly* samples and tune per-level prompts. (Quota-heavy — see §1; spread across days.)

### C. UX / features
- [ ] **Manual-correction screen** — top feature gap; lets users fix the ~10% the parser misses. Turns 90% → 100% correct.
- [ ] **Display the timetable in real UI** (calendar/dashboard) with module colour-coding (currently raw JSON dump).

### D. Hand-off to Ling Song (app side)
- [ ] Give Ling Song the live URL + fill `<the-render-url>` placeholders in `docs/timetable-parsing-app-guide.md`.
- [ ] App-side parsing: pick image → base64 → POST to proxy → decode into `Timetable`/`Module`/`Slot` → store + sync.

---

## 📋 Broader MS2 work (from main roadmap)

- [ ] Wire designed screens (Dashboard, Calendar, All Tasks, Study Session) to the working data layer — largest remaining web effort.
- [ ] **Ling Song:** Android Drive read/write (`// TODO` in `AuthenticationHelper.kt`).
- [ ] **Cross-device sync demo (web ↔ app)** — headline deliverable.
- [ ] Task management: filtering + sorting (delete already done on web).
- [ ] Productivity / mood board on the calendar.
- [ ] Notes & file sync via Drive.
- [ ] Onboarding / setup tutorial.

---

## Decisions made (2026-06-16)

1. ✅ **Billing:** staying on the **free 20/day** — no billing for now.
2. ✅ **`teacher`:** **removed** from the schema (not captured).
3. ✅ **Web app architecture:** vanilla-JS **SPA** (one shell + JS view-switching + hash routing).
4. ✅ **Accuracy over speed:** no thinking budget; image downscaling is the speed lever instead.

### Still open
- **Priority order** for after Phase 4 — manual-correction screen vs real UI display first? _(your call)_

---

## Notes / scratch (YJ to fill in)

-
