// timetableView.js — read-only weekly calendar grid for the timetable (Phase 6).
// Scaffold + session blocks + odd/even toggle + "Re-upload" + current-day highlight.
// Real dates come in a later phase (Step 3 of the plan).

import { appState } from "./data.js";
import { startReupload } from "./timetable.js";

const DAYS = ["MON", "TUE", "WED", "THU", "FRI"];
const DEFAULT_START = 8;
const DEFAULT_END = 19;
const HOUR_PX = 64;
const PX_PER_MIN = HOUR_PX / 60;

const TYPE_ABBREV = {
  lecture: "Lec", tutorial: "Tut", lab: "Lab", recitation: "Rec",
  seminar: "Sem", sectional: "Sec", practical: "Prac", lesson: "Lesson", cca: "CCA",
};
function abbrevType(t) {
  return TYPE_ABBREV[t] || t || "";
}

function toMinutes(t) {
  if (!t || !t.includes(":")) return null;
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
}

function gridRange(modules) {
  let startH = DEFAULT_START, endH = DEFAULT_END;
  for (const m of modules) {
    for (const s of m.slots || []) {
      const a = toMinutes(s.start), b = toMinutes(s.end);
      if (a != null) startH = Math.min(startH, Math.floor(a / 60));
      if (b != null) endH = Math.max(endH, Math.ceil(b / 60));
    }
  }
  return { startH, endH };
}

// Does any slot run on only odd/even weeks? Then we need the toggle.
function hasAlternatingWeeks(modules) {
  return modules.some((m) =>
    (m.slots || []).some((s) => s.week === "odd" || s.week === "even"));
}

// Today's weekday as a MON..SUN code (for the current-day column highlight).
function todayCode() {
  return ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"][new Date().getDay()];
}

// --- date helpers for the dated weekly view (Phase 8) ---

// Parse a "YYYY-MM-DD" string into a local Date at midnight (no timezone surprises).
function parseISODate(s) {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d); // month is 0-indexed
}

// A date n days after the given one (handles month/year rollover via setDate).
function addDays(date, n) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  d.setDate(d.getDate() + n);
  return d;
}

// The Monday on or before a given date (start of that date's week, Monday-first).
function mondayOf(date) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const dow = (d.getDay() + 6) % 7; // 0=Mon..6=Sun
  return addDays(d, -dow);
}

// Whole days a − b. Both are local-midnight dates and SG has no daylight saving,
// so a plain subtraction is exact; Math.round just guards against float dust.
function daysBetween(a, b) {
  return Math.round((a - b) / 86400000); // ms per day
}

// The Week-1 Monday from appState.termStart (snapped to its Monday), or null if unset.
function termMonday() {
  return appState.termStart ? mondayOf(parseISODate(appState.termStart)) : null;
}

// For a viewed week (given its Monday): the Mon–Fri dates, and — if a term anchor is
// set — the academic week number + odd/even parity. number/parity are null without it.
function weekInfo(monday) {
  const dates = [0, 1, 2, 3, 4].map((i) => addDays(monday, i)); // Mon..Fri
  const term = termMonday();
  let number = null, parity = null;
  if (term) {
    number = Math.floor(daysBetween(monday, term) / 7) + 1; // week 1 = the term Monday
    parity = number % 2 === 1 ? "odd" : "even";
  }
  return { dates, number, parity };
}

let viewedMonday = mondayOf(new Date()); // which week the grid shows; defaults to this week
let currentWeek = "odd"; // which week the toggle is showing (odd timetables only)

const headerEl = document.getElementById("timetableViewHeader");
const calEl = document.getElementById("timetableCalendar");

// The strip above the grid: title (+ odd/even toggle) on the left, re-upload right.
function renderHeader(showToggle) {
  headerEl.innerHTML = "";

  const left = document.createElement("div");
  left.className = "cal-header-left";
  const title = document.createElement("span");
  title.className = "cal-title";
  title.textContent = "Timetable";
  left.append(title);

  if (showToggle) {
    for (const wk of ["odd", "even"]) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "cal-week-btn" + (wk === currentWeek ? " is-active" : "");
      btn.textContent = wk === "odd" ? "Odd week" : "Even week";
      btn.addEventListener("click", () => { currentWeek = wk; renderCalendar(); });
      left.append(btn);
    }
  }

  const reupload = document.createElement("button");
  reupload.type = "button";
  reupload.className = "cal-reupload";
  reupload.textContent = "Re-upload timetable";
  reupload.addEventListener("click", startReupload);

  headerEl.append(left, reupload);
}

// Place one session block in its day column at the right time + height.
function addBlock(col, module, slot, startH) {
  const startMin = toMinutes(slot.start);
  const endMin = toMinutes(slot.end);
  if (startMin == null || endMin == null) return;

  const block = document.createElement("div");
  block.className = "cal-block";
  block.style.top = (startMin - startH * 60) * PX_PER_MIN + "px";
  block.style.height = Math.max((endMin - startMin) * PX_PER_MIN - 3, 34) + "px";

  const title = document.createElement("div");
  title.className = "cal-block-title";
  title.textContent = module.code || module.name || "(unnamed)";

  const sub = document.createElement("div");
  sub.className = "cal-block-sub";
  sub.textContent = `${abbrevType(slot.sessionType)} · ${slot.start}`;

  block.append(title, sub);
  col.append(block);
}

function renderCalendar() {
  // TEMP (8.1 verify): log the viewed week's computed info; removed in 8.2.
  const _wk = weekInfo(viewedMonday);
  console.log("[timetable week]", {
    number: _wk.number, parity: _wk.parity,
    dates: _wk.dates.map((d) => d.toDateString()),
  });

  const modules = appState.timetable?.modules || [];
  const alternating = hasAlternatingWeeks(modules);
  renderHeader(alternating);

  calEl.innerHTML = "";
  const { startH, endH } = gridRange(modules);
  // +16 tail so the bottom hour label isn't clipped by the container's overflow:hidden
  const bodyHeight = (endH - startH) * HOUR_PX + 16;
  const today = todayCode();

  // --- header row: empty corner + one cell per day ---
  const corner = document.createElement("div");
  corner.className = "cal-corner";
  calEl.append(corner);
  for (const day of DAYS) {
    const head = document.createElement("div");
    head.className = "cal-dayhead" + (day === today ? " cal-dayhead--today" : "");
    head.textContent = day;
    calEl.append(head);
  }

  // --- body row: time gutter (hour labels) ---
  const gutter = document.createElement("div");
  gutter.className = "cal-gutter";
  gutter.style.height = bodyHeight + "px";
  for (let h = startH; h <= endH; h++) {
    const label = document.createElement("div");
    label.className = "cal-hour";
    label.style.top = (h - startH) * HOUR_PX + "px";
    label.textContent = String(h).padStart(2, "0") + ":00";
    gutter.append(label);
  }
  calEl.append(gutter);

  // --- body row: one column per day, kept in `cols` so blocks find theirs ---
  const cols = {};
  for (const day of DAYS) {
    const col = document.createElement("div");
    col.className = "cal-col" + (day === today ? " cal-col--today" : "");
    col.style.height = bodyHeight + "px";
    col.dataset.day = day;
    calEl.append(col);
    cols[day] = col;
  }

  // --- place the session blocks (filtered to the shown week) ---
  for (const m of modules) {
    for (const slot of m.slots || []) {
      // when alternating, hide the OTHER week's slots; "all" slots always show
      if (alternating && slot.week && slot.week !== "all" && slot.week !== currentWeek) continue;
      const col = cols[slot.day];        // undefined for SAT/SUN — not shown yet
      if (col) addBlock(col, m, slot, startH);
    }
  }
}

window.addEventListener("modulo:datachanged", renderCalendar);
renderCalendar();
