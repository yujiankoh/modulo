// timetableView.js — weekly calendar grid for the timetable.
// Dated week view (Phase 8): real Mon–Fri dates + week number from a term anchor,
// week navigation, term bounds + recess/holiday weeks, parity-driven odd/even slots,
// and a date-accurate today highlight. Falls back to a plain undated grid (with the
// manual odd/even toggle) when no term start is set.

import { appState } from "./data.js";
import { startReupload } from "./timetable.js";
import { openEditor } from "./timetableEditor.js";

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

// Is the given date today? Used for the current-day column highlight — date-accurate,
// so only the real "today" column lights up (and only when that week is on screen).
function isToday(date) {
  const now = new Date();
  return date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate();
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

// The Week-1 Monday from appState.termStart (snapped to its Monday), or null if unset.
function termMonday() {
  return appState.termStart ? mondayOf(parseISODate(appState.termStart)) : null;
}

// The Monday of the term-end week (so a week is "in term" up to and including it), or null.
function termEndMonday() {
  return appState.termEnd ? mondayOf(parseISODate(appState.termEnd)) : null;
}

// A week (given its Monday) is a break week if Mon–Sun overlaps any recess/holiday range.
function isBreakWeek(monday) {
  const weekEnd = addDays(monday, 6); // that week's Sunday
  return (appState.breaks || []).some((b) => {
    const start = parseISODate(b.start), end = parseISODate(b.end);
    return start <= weekEnd && end >= monday; // two date ranges overlap
  });
}

// For a viewed week (given its Monday), returns { dates, number, parity, status }:
//   status "none"    — no term anchor set (fall back to the plain undated view)
//   status "outside" — before term start or after term end (dates shown, no classes)
//   status "break"   — a recess/holiday week (dates shown, no classes, no number)
//   status "normal"  — a teaching week: number (break weeks skipped) + odd/even parity
function weekInfo(monday) {
  const dates = [0, 1, 2, 3, 4].map((i) => addDays(monday, i)); // Mon..Fri
  const term = termMonday();
  if (!term) return { dates, number: null, parity: null, status: "none" };

  const end = termEndMonday();
  if (monday < term || (end && monday > end)) {
    return { dates, number: null, parity: null, status: "outside" };
  }
  if (isBreakWeek(monday)) {
    return { dates, number: null, parity: null, status: "break" };
  }

  // teaching week: count non-break weeks from term start up to (and incl.) this week
  let number = 0;
  for (let w = term; w <= monday; w = addDays(w, 7)) {
    if (!isBreakWeek(w)) number++;
  }
  const parity = number % 2 === 1 ? "odd" : "even";
  return { dates, number, parity, status: "normal" };
}

// --- Phase 12: read-only helpers the Dashboard reuses, so its "Week N" matches this
// view exactly (one source of truth for term-week math). Both just call weekInfo, which
// already handles term bounds + break-skipping. ---

// This week's info (number / parity / status) for today's week.
export function currentWeekInfo() {
  return weekInfo(mondayOf(new Date()));
}

// Total teaching weeks in the term = the number of the term-end week (breaks excluded),
// or null if no term end is set. (weekInfo on the last week returns the running count.)
export function totalTeachingWeeks() {
  const end = termEndMonday();
  return end ? weekInfo(end).number : null;
}

// Month abbreviations for the week-range title.
const MONTHS_SHORT = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// "26 — 30 May" (same month) or "29 May — 2 Jun" (week spanning two months).
function formatWeekRange(start, end) {
  const sd = start.getDate(), ed = end.getDate();
  const sm = MONTHS_SHORT[start.getMonth()], em = MONTHS_SHORT[end.getMonth()];
  return start.getMonth() === end.getMonth()
    ? `${sd} — ${ed} ${em}`
    : `${sd} ${sm} — ${ed} ${em}`;
}

let viewedMonday = mondayOf(new Date()); // which week the grid shows; defaults to this week
let currentWeek = "odd"; // manual toggle's week (only used when no term anchor)

// Move the shown week by delta (-1 prev, +1 next), clamped within the term when set.
function shiftWeek(delta) {
  const candidate = addDays(viewedMonday, delta * 7);
  const start = termMonday(), end = termEndMonday();
  if (start && candidate < start) return; // don't step before Week 1
  if (end && candidate > end) return;     // don't step past term end
  viewedMonday = candidate;
  renderCalendar();
}

// Jump back to the week containing today.
function goThisWeek() {
  viewedMonday = mondayOf(new Date());
  renderCalendar();
}

const headerEl = document.getElementById("timetableViewHeader");
const calEl = document.getElementById("timetableCalendar");

// The strip above the grid: week title (+ odd/even toggle) on the left, re-upload right.
// `wk` is the weekInfo for the shown week; when it has a number we show real dates.
function renderHeader(showToggle, wk) {
  headerEl.innerHTML = "";

  const left = document.createElement("div");
  left.className = "cal-header-left";

  // title block: with a term anchor → eyebrow ("TIMETABLE · WEEK n") + date range;
  // without one → plain "Timetable".
  const titleBlock = document.createElement("div");
  if (wk.status === "none") {
    const title = document.createElement("span");
    title.className = "cal-title";
    title.textContent = "Timetable";
    titleBlock.append(title);
  } else {
    const eyebrow = document.createElement("div");
    eyebrow.className = "tt-eyebrow";
    eyebrow.textContent =
      wk.status === "normal" ? `TIMETABLE · WEEK ${wk.number}` :
      wk.status === "break"  ? "TIMETABLE · RECESS / BREAK" :
                               "TIMETABLE · OUTSIDE TERM";
    const title = document.createElement("div");
    title.className = "cal-title";
    title.textContent = formatWeekRange(wk.dates[0], wk.dates[4]);
    titleBlock.append(eyebrow, title);
  }
  left.append(titleBlock);

  if (wk.status === "none") {
    // no term anchor → keep the manual odd/even toggle (no dates to derive parity from)
    if (showToggle) {
      for (const w of ["odd", "even"]) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "cal-week-btn" + (w === currentWeek ? " is-active" : "");
        btn.textContent = w === "odd" ? "Odd week" : "Even week";
        btn.addEventListener("click", () => { currentWeek = w; renderCalendar(); });
        left.append(btn);
      }
    }
  } else {
    // dated view → week navigation (the week itself determines odd/even parity)
    const nav = document.createElement("div");
    nav.className = "tt-nav";
    const start = termMonday(), end = termEndMonday();

    const prev = document.createElement("button");
    prev.type = "button";
    prev.className = "tt-arrow";
    prev.textContent = "‹";
    prev.disabled = !!start && addDays(viewedMonday, -7) < start; // can't go before Week 1
    prev.addEventListener("click", () => shiftWeek(-1));

    const thisWk = document.createElement("button");
    thisWk.type = "button";
    thisWk.className = "tt-thisweek";
    thisWk.textContent = "This week";
    thisWk.addEventListener("click", goThisWeek);

    const next = document.createElement("button");
    next.type = "button";
    next.className = "tt-arrow";
    next.textContent = "›";
    next.disabled = !!end && addDays(viewedMonday, 7) > end; // can't go past term end
    next.addEventListener("click", () => shiftWeek(1));

    nav.append(prev, thisWk, next);
    left.append(nav);
  }

  const edit = document.createElement("button");
  edit.type = "button";
  edit.className = "cal-reupload";
  edit.textContent = "Edit";
  edit.addEventListener("click", openEditor);

  const reupload = document.createElement("button");
  reupload.type = "button";
  reupload.className = "cal-reupload";
  reupload.textContent = "Re-upload timetable";
  reupload.addEventListener("click", startReupload);

  // Group the actions together on the right.
  const actions = document.createElement("div");
  actions.className = "cal-header-actions";
  actions.append(edit, reupload);
  headerEl.append(left, actions);
}

// Place one session block in its day column at the right time + height.
function addBlock(col, module, slot, startH) {
  const startMin = toMinutes(slot.start);
  const endMin = toMinutes(slot.end);
  if (startMin == null || endMin == null) return;

  const block = document.createElement("div");
  block.className = "cal-block";
  block.style.top = (startMin - startH * 60) * PX_PER_MIN + "px";
  // exact-duration height: a 1-hour class fills its hour and touches the next line,
  // and shorter classes end exactly where the next begins (no overlap). border-box
  // keeps the 1px border inside the height; overflow:hidden clips text in short cards.
  block.style.height = (endMin - startMin) * PX_PER_MIN + "px";

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
  const modules = appState.timetable?.modules || [];
  const alternating = hasAlternatingWeeks(modules);
  const wk = weekInfo(viewedMonday);
  renderHeader(alternating, wk);

  calEl.innerHTML = "";
  const { startH, endH } = gridRange(modules);
  // +16 tail so the bottom hour label isn't clipped by the container's overflow:hidden
  const bodyHeight = (endH - startH) * HOUR_PX + 16;
  // which column (0–4) is today, or -1 if today isn't in the shown week
  const todayIdx = wk.dates.findIndex(isToday);

  // --- header row: empty corner + one cell per day ---
  const corner = document.createElement("div");
  corner.className = "cal-corner";
  calEl.append(corner);
  for (let i = 0; i < DAYS.length; i++) {
    const day = DAYS[i];
    const head = document.createElement("div");
    head.className = "cal-dayhead" + (i === todayIdx ? " cal-dayhead--today" : "");
    if (wk.status !== "none") {
      // dated view: weekday code on top, the real date number below
      const code = document.createElement("div");
      code.className = "tt-dayhead-code";
      code.textContent = day;
      const dnum = document.createElement("div");
      dnum.className = "tt-dayhead-date";
      dnum.textContent = wk.dates[i].getDate();
      head.append(code, dnum);
    } else {
      head.textContent = day;
    }
    calEl.append(head);
  }

  // --- body row: time gutter (hour labels) ---
  const gutter = document.createElement("div");
  gutter.className = "cal-gutter";
  gutter.style.height = bodyHeight + "px";
  for (let h = startH; h <= endH; h++) {
    const label = document.createElement("div");
    // the first label can't centre on its line (that line is the header border),
    // so render it top-aligned instead — see .cal-hour--first
    label.className = "cal-hour" + (h === startH ? " cal-hour--first" : "");
    label.style.top = (h - startH) * HOUR_PX + "px";
    label.textContent = String(h).padStart(2, "0") + ":00";
    gutter.append(label);
  }
  calEl.append(gutter);

  // --- body row: one column per day, kept in `cols` so blocks find theirs ---
  const cols = {};
  for (let i = 0; i < DAYS.length; i++) {
    const day = DAYS[i];
    const col = document.createElement("div");
    col.className = "cal-col" + (i === todayIdx ? " cal-col--today" : "");
    col.style.height = bodyHeight + "px";
    col.dataset.day = day;
    calEl.append(col);
    cols[day] = col;
  }

  // --- place the session blocks (none on recess/break or outside-term weeks) ---
  if (wk.status !== "break" && wk.status !== "outside") {
    // dated view: the week's own parity filters odd/even slots; undated: the manual toggle
    const weekParity = wk.parity || currentWeek;
    for (const m of modules) {
      for (const slot of m.slots || []) {
        // hide the OTHER week's slots; "all" slots always show
        if (alternating && slot.week && slot.week !== "all" && slot.week !== weekParity) continue;
        const col = cols[slot.day];        // undefined for SAT/SUN — not shown yet
        if (col) addBlock(col, m, slot, startH);
      }
    }
  }
}

window.addEventListener("modulo:datachanged", renderCalendar);
renderCalendar();
