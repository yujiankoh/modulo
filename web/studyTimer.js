// studyTimer.js — a count-up stopwatch (Phase 10). Owns the timer engine + the big
// display, wires Start / Pause / Reset, and (step 3) records a session on Stop & Save.
//
// Accuracy idea: we compute elapsed time from wall-clock TIMESTAMPS, not by counting
// ticks. A backgrounded tab throttles setInterval, so a "+1 each tick" counter would
// drift/lose time. Here the interval ONLY triggers a repaint; the time itself is always
// recomputed from Date.now(), so it's correct the instant we read it.

import { appState, persist, getStorageMode } from "./data.js";

// --- Engine state (private to this module) -----------------------------------
let running = false;        // is the clock currently counting?
let startedAt = null;       // Date.now() ms when the CURRENT run segment began (null if paused)
let accumulatedMs = 0;      // time banked from PREVIOUS run segments (before the last pause)
let tickHandle = null;      // the setInterval id, so we can stop repainting

// Total elapsed = banked time + (time since the current segment started, if running).
function elapsedMs() {
  return accumulatedMs + (running ? Date.now() - startedAt : 0);
}

// Format milliseconds as HH:MM:SS. padStart(2,"0") zero-pads ("9" -> "09").
function formatElapsed(ms) {
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}

// --- Display ------------------------------------------------------------------
const displayEl = document.getElementById("timerDisplay");

// Repaint the big clock from the current elapsed time. Called by the tick + on each action.
function render() {
  displayEl.textContent = formatElapsed(elapsedMs());
}

// --- Controls -----------------------------------------------------------------
// Start: begin (or resume) counting. Ignore if already running (no double-start).
function start() {
  if (running) return;
  running = true;
  startedAt = Date.now();
  // Repaint 4x/second so the seconds tick over smoothly. The interval only repaints;
  // it never adds to the count, so throttling/drift can't corrupt the elapsed time.
  tickHandle = setInterval(render, 250);
  render();
}

// Pause: stop counting, banking the current segment into accumulatedMs so it isn't lost.
function pause() {
  if (!running) return;
  accumulatedMs += Date.now() - startedAt;
  running = false;
  startedAt = null;
  clearInterval(tickHandle);   // stop repainting — nothing is changing while paused
  tickHandle = null;
  render();
}

// Reset: stop and zero everything back to 00:00:00.
function reset() {
  clearInterval(tickHandle);
  tickHandle = null;
  running = false;
  startedAt = null;
  accumulatedMs = 0;
  render();
}

// --- Stop & Save: record a study session --------------------------------------
// When the user stops, we freeze the session details here while the rating modal is
// open. Picking a rating (or Skip) finalizes the save; cancelling discards this.
let pendingSession = null;

const ratingModal = document.getElementById("ratingModal");

// Stop & Save: pause the clock, capture the session, and ask for a rating.
function stopAndSave() {
  if (!getStorageMode()) { alert("Choose Google Drive or local mode first."); return; }
  if (elapsedMs() < 1000) { alert("Start the timer first."); return; } // nothing to save
  if (running) pause();            // bank the time so elapsedMs is stable while we save

  const ms = elapsedMs();
  const end = new Date();
  pendingSession = {
    start: new Date(end.getTime() - ms).toISOString(), // end minus elapsed = when it began
    end: end.toISOString(),
    durationMins: Math.round(ms / 60000),              // whole minutes (60,000 ms per min)
  };

  document.getElementById("ratingSummary").textContent =
    `You studied for ${pendingSession.durationMins} min.`;
  ratingModal.style.display = "flex";  // .tcal-popup CSS centres the card
}

// Finalize: build the full record (with rating or null), save it, then reset the clock.
async function finalizeSave(rating) {
  if (!pendingSession) return;
  if (!appState.studySessions) appState.studySessions = []; // defensive
  appState.studySessions.push({
    id: crypto.randomUUID(),
    start: pendingSession.start,
    end: pendingSession.end,
    durationMins: pendingSession.durationMins,
    rating,                              // 1–5, or null if skipped
    createdAt: new Date().toISOString(),
  });
  pendingSession = null;
  ratingModal.style.display = "none";
  reset();                               // zero the clock for the next session
  await persist();                       // save + fire modulo:datachanged
}

// Cancel: discard the pending save but KEEP the timer paused (no data lost — resume or
// Stop & Save again).
function cancelRating() {
  pendingSession = null;
  ratingModal.style.display = "none";
}

// Wire the buttons to the engine. Pass the function itself (no "()"), so it's CALLED on
// click rather than immediately.
document.getElementById("timerStart").addEventListener("click", start);
document.getElementById("timerPause").addEventListener("click", pause);
document.getElementById("timerStop").addEventListener("click", stopAndSave);

// Rating modal: a click on any number button reads its data-rating and saves with it.
// (Event delegation: one listener on the container; closest() finds the clicked button.)
document.getElementById("ratingButtons").addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-rating]");
  if (!btn) return;
  finalizeSave(Number(btn.dataset.rating));
});
document.getElementById("ratingSkip").addEventListener("click", () => finalizeSave(null));

// Close the rating modal = cancel: via ✕, clicking the dim backdrop, or pressing Esc.
document.getElementById("ratingClose").addEventListener("click", cancelRating);
ratingModal.addEventListener("click", (e) => {
  if (e.target === ratingModal) cancelRating();   // backdrop only, not the card
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && ratingModal.style.display !== "none") cancelRating();
});

// --- Sessions list + study-time totals ----------------------------------------
// All derived LIVE from appState.studySessions on each redraw — nothing is pre-stored,
// so totals can never drift out of sync. (If the array ever got huge, this is where a
// cached cumulative total / daily aggregation would slot in — not needed at this scale.)

// Local-midnight reference points for "today" and "this week" (Monday-first, matching
// the rest of the app's calendars).
function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}
function startOfWeek() {
  const d = startOfToday();
  const mondayOffset = (d.getDay() + 6) % 7; // getDay() is Sun-first; rotate to Mon-first
  d.setDate(d.getDate() - mondayOffset);
  return d;
}

// Sum durationMins of sessions whose start is on/after a cutoff (null = all sessions).
function sumMinsSince(sessions, cutoff) {
  return sessions
    .filter((s) => !cutoff || new Date(s.start) >= cutoff)
    .reduce((total, s) => total + (s.durationMins || 0), 0);
}

// Minutes -> friendly "2h 15m" / "45m" / "0m".
function formatMins(mins) {
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (h && m) return `${h}h ${m}m`;
  if (h) return `${h}h`;
  return `${m}m`;
}

// One session's <li>: "Wed 25 Jun, 14:00 · 50m · ★4" (or "no rating").
function renderSessionRow(s) {
  const li = document.createElement("li");
  const when = new Date(s.start);
  const dateStr = when.toLocaleDateString("en-GB", {
    weekday: "short", day: "2-digit", month: "short", // "Wed 25 Jun"
  });
  const timeStr = when.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  const rating = s.rating ? `★${s.rating}` : "no rating";
  li.textContent = `${dateStr}, ${timeStr} · ${formatMins(s.durationMins || 0)} · ${rating}`;
  return li;
}

const RECENT_LIMIT = 10; // how many sessions to show in the list (keeps the DOM light)

// Redraw the totals + the recent-sessions list from current state.
function renderSessions() {
  const sessions = appState.studySessions || [];

  // Totals.
  document.getElementById("totalToday").textContent = formatMins(sumMinsSince(sessions, startOfToday()));
  document.getElementById("totalWeek").textContent = formatMins(sumMinsSince(sessions, startOfWeek()));
  document.getElementById("totalAll").textContent = formatMins(sumMinsSince(sessions, null));

  // List: newest first (ISO start strings sort chronologically), capped at RECENT_LIMIT.
  const list = document.getElementById("sessionList");
  list.innerHTML = "";
  const note = document.getElementById("sessionNote");

  if (sessions.length === 0) {
    note.textContent = "";
    list.innerHTML = "<li>No sessions yet — start the timer above.</li>";
    return;
  }

  const sorted = [...sessions].sort((a, b) => b.start.localeCompare(a.start));
  note.textContent = sessions.length > RECENT_LIMIT
    ? `Showing latest ${RECENT_LIMIT} of ${sessions.length}.`
    : "";
  for (const s of sorted.slice(0, RECENT_LIMIT)) list.append(renderSessionRow(s));
}

// Redraw the sessions UI whenever state is saved/loaded, then draw once now.
window.addEventListener("modulo:datachanged", renderSessions);
renderSessions();

// Draw 00:00:00 once on load.
render();
