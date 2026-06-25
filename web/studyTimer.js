// studyTimer.js — a count-up stopwatch (Phase 10). Owns the timer engine + the big
// display, and wires the Start / Pause / Reset buttons. Step 2 = engine only: it runs
// and shows elapsed time but does NOT save anything yet (recording a session = step 3).
//
// Accuracy idea: we compute elapsed time from wall-clock TIMESTAMPS, not by counting
// ticks. A backgrounded tab throttles setInterval, so a "+1 each tick" counter would
// drift/lose time. Here the interval ONLY triggers a repaint; the time itself is always
// recomputed from Date.now(), so it's correct the instant we read it.

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

// Wire the buttons to the engine. Pass the function itself (no "()"), so it's CALLED on
// click rather than immediately.
document.getElementById("timerStart").addEventListener("click", start);
document.getElementById("timerPause").addEventListener("click", pause);
document.getElementById("timerReset").addEventListener("click", reset);

// Draw 00:00:00 once on load.
render();
