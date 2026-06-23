// calendarView.js — month-grid calendar showing tasks on their due dates (Phase 7).
// Step 7.1: static current-month scaffold (Monday-first). Tasks + month nav come next.

import { appState } from "./data.js";

// Monday-first weekday labels + full month names for the title.
const WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

// Which month the calendar is showing. Starts on today's month; nav changes it (7.3).
const today = new Date();
let viewYear = today.getFullYear();
let viewMonth = today.getMonth(); // 0=Jan ... 11=Dec

const headerEl = document.getElementById("taskCalendarHeader");
const gridEl = document.getElementById("taskCalendarGrid");

// How many blank cells go before day 1 = the Monday-first index of the 1st.
// getDay() is 0=Sun..6=Sat; (n+6)%7 rotates it to 0=Mon..6=Sun.
function leadingBlanks(year, month) {
  const firstDow = new Date(year, month, 1).getDay();
  return (firstDow + 6) % 7;
}

// Days in the month: "day 0 of next month" is the last day of this one.
function daysInMonth(year, month) {
  return new Date(year, month + 1, 0).getDate();
}

// Build a "YYYY-MM-DD" string for a cell's date, to match against task.due
// (which the <input type="date"> already stores in exactly this format).
// padStart(2, "0") turns 6 into "06" so the format is always two digits.
function isoDate(year, month, day) {
  const mm = String(month + 1).padStart(2, "0"); // month is 0-indexed, so +1
  const dd = String(day).padStart(2, "0");
  return `${year}-${mm}-${dd}`;
}

// Group tasks into a lookup: { "2026-06-08": [task, task], ... }. Built once per
// render so each cell can grab its day's tasks without re-scanning the whole list.
function tasksByDate(tasks) {
  const map = {};
  for (const t of tasks) {
    if (!t.due) continue;            // skip tasks with no due date
    if (!map[t.due]) map[t.due] = []; // first task for this day → start a list
    map[t.due].push(t);
  }
  return map;
}

// The strip above the grid: eyebrow + "Month Year" title.
function renderHeader() {
  headerEl.innerHTML = "";

  const eyebrow = document.createElement("div");
  eyebrow.className = "tcal-eyebrow";
  eyebrow.textContent = "CALENDAR";

  const title = document.createElement("div");
  title.className = "tcal-title";
  const month = document.createElement("em"); // italic month, like the mockup
  month.textContent = MONTHS[viewMonth];
  title.append(month, " " + viewYear);

  headerEl.append(eyebrow, title);
}

function renderGrid() {
  gridEl.innerHTML = "";

  // --- weekday header row (MON..SUN) ---
  for (const wd of WEEKDAYS) {
    const head = document.createElement("div");
    head.className = "tcal-weekday";
    head.textContent = wd;
    gridEl.append(head);
  }

  // --- blank cells before day 1, so the 1st lands under the right weekday ---
  const blanks = leadingBlanks(viewYear, viewMonth);
  for (let i = 0; i < blanks; i++) {
    const blank = document.createElement("div");
    blank.className = "tcal-cell tcal-cell--blank";
    gridEl.append(blank);
  }

  // --- one cell per day of the month ---
  const grouped = tasksByDate(appState.tasks || []);
  const total = daysInMonth(viewYear, viewMonth);
  for (let d = 1; d <= total; d++) {
    const cell = document.createElement("div");
    cell.className = "tcal-cell";

    const num = document.createElement("div");
    num.className = "tcal-date";
    num.textContent = d;
    cell.append(num);

    // tasks due on this day → one pill each (neutral colour for now)
    const dayTasks = grouped[isoDate(viewYear, viewMonth, d)] || [];
    for (const t of dayTasks) {
      const pill = document.createElement("div");
      pill.className = "tcal-pill";
      pill.textContent = t.title;
      pill.title = t.title; // hover tooltip, in case the title is truncated
      cell.append(pill);
    }

    gridEl.append(cell);
  }
}

function renderCalendar() {
  renderHeader();
  renderGrid();
}

// Redraw whenever tasks are saved/loaded (data.js fires this on persist/load).
window.addEventListener("modulo:datachanged", renderCalendar);
renderCalendar();
