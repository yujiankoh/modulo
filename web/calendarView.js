// calendarView.js — month-grid calendar showing tasks on their due dates (Phase 7).
// Monday-first month grid; tasks as pills (capped, with "N more"); click a day for a
// view-only popup; month nav via prev/next arrows + a month/year picker; today is
// highlighted. Redraws on the modulo:datachanged event. (Phase 8 will add timetable
// sessions onto these same date cells.)
//
// Phase 10: each day cell also shows the rounded AVERAGE rating of that day's study
// sessions (as stars) — read from appState.studySessions, display-only.

import { appState } from "./data.js";

// Monday-first weekday labels + full month names for the title.
const WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];
// Short labels for the month picker grid (Jan..Dec).
const MONTH_ABBR = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

// How many task pills to show in a cell before collapsing the rest into "N more".
const MAX_PILLS = 3;

// Which month the calendar is showing. Starts on today's month; nav changes it (7.4).
const today = new Date();
let viewYear = today.getFullYear();
let viewMonth = today.getMonth(); // 0=Jan ... 11=Dec

const headerEl = document.getElementById("taskCalendarHeader");
const gridEl = document.getElementById("taskCalendarGrid");

// Header pieces built once by buildHeader(); kept so updateTitle/renderPicker
// can change them without rebuilding the whole header on every redraw.
let titleMonthEl, titleYearEl, pickerEl;
let pickerOpen = false;   // is the month/year picker showing?
let pickerYear = viewYear; // which year the picker is currently browsing

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

// Group study sessions into a per-day ROUNDED AVERAGE rating: { "2026-06-08": 4, ... }.
// A session's `start` is an ISO timestamp, so we key on its LOCAL date using the same
// isoDate() the cells use → keys line up (no timezone drift). Sessions with no rating
// (skipped → null) are ignored; a day with only unrated sessions gets no entry.
function avgRatingByDate(sessions) {
  const acc = {}; // date -> { sum, count }
  for (const s of sessions) {
    if (s.rating == null) continue;  // skip unrated (null / undefined)
    const d = new Date(s.start);
    const key = isoDate(d.getFullYear(), d.getMonth(), d.getDate());
    if (!acc[key]) acc[key] = { sum: 0, count: 0 };
    acc[key].sum += s.rating;
    acc[key].count += 1;
  }
  const avg = {};
  for (const key in acc) avg[key] = Math.round(acc[key].sum / acc[key].count);
  return avg;
}

// Build the header ONCE: eyebrow + clickable title (with chevron) + picker popover
// on the left, prev/next month arrows on the right. updateTitle() refreshes the text.
function buildHeader() {
  headerEl.innerHTML = "";

  // --- left: eyebrow, title button (opens picker), and the picker popover ---
  const left = document.createElement("div");
  left.className = "tcal-header-left";

  // title row: month + year are plain text; only the chevron is clickable
  const title = document.createElement("div");
  title.className = "tcal-title tcal-title-row";
  titleMonthEl = document.createElement("em");   // italic month (filled in updateTitle)
  titleYearEl = document.createElement("span");   // year (filled in updateTitle)

  const chevronBtn = document.createElement("button");
  chevronBtn.type = "button";
  chevronBtn.className = "tcal-chevron-btn";
  chevronBtn.setAttribute("aria-label", "Choose month and year");
  const chevron = document.createElement("span");
  chevron.className = "tcal-chevron"; // a CSS triangle, no text
  chevronBtn.append(chevron);
  chevronBtn.addEventListener("click", togglePicker);

  title.append(titleMonthEl, titleYearEl, chevronBtn); // gap handles spacing

  pickerEl = document.createElement("div");
  pickerEl.className = "tcal-picker";
  pickerEl.style.display = "none";
  // Clicks inside the picker must not reach the document "click-away" listener,
  // which would otherwise close the picker when its buttons rebuild themselves.
  pickerEl.addEventListener("click", (e) => e.stopPropagation());

  left.append(title, pickerEl);

  // --- right: previous / next month arrows ---
  const nav = document.createElement("div");
  nav.className = "tcal-nav";
  const prev = document.createElement("button");
  prev.type = "button";
  prev.className = "tcal-arrow";
  prev.textContent = "‹";
  prev.addEventListener("click", () => shiftMonth(-1));
  const next = document.createElement("button");
  next.type = "button";
  next.className = "tcal-arrow";
  next.textContent = "›";
  next.addEventListener("click", () => shiftMonth(1));
  nav.append(prev, next);

  headerEl.append(left, nav);
}

// Refresh just the title text to the currently-shown month/year.
function updateTitle() {
  titleMonthEl.textContent = MONTHS[viewMonth];
  titleYearEl.textContent = viewYear;
}

// Move the shown month by delta (-1 / +1). Date normalises out-of-range months,
// so Dec→Jan and Jan→Dec roll the year over automatically.
function shiftMonth(delta) {
  const d = new Date(viewYear, viewMonth + delta, 1);
  viewYear = d.getFullYear();
  viewMonth = d.getMonth();
  renderCalendar();
}

// --- month/year picker (opened by the title) ---
function togglePicker() {
  pickerOpen = !pickerOpen;
  if (pickerOpen) {
    pickerYear = viewYear; // start browsing on the shown year
    renderPicker();
    pickerEl.style.display = "block";
  } else {
    pickerEl.style.display = "none";
  }
}

function closePicker() {
  pickerOpen = false;
  pickerEl.style.display = "none";
}

// Fill the picker: a "‹ year ›" stepper + a 3×4 grid of months.
function renderPicker() {
  pickerEl.innerHTML = "";

  // year stepper row
  const yearRow = document.createElement("div");
  yearRow.className = "tcal-picker-year";
  const yPrev = document.createElement("button");
  yPrev.type = "button";
  yPrev.textContent = "‹";
  yPrev.addEventListener("click", () => { pickerYear--; renderPicker(); });
  const yLabel = document.createElement("span");
  yLabel.textContent = pickerYear;
  const yNext = document.createElement("button");
  yNext.type = "button";
  yNext.textContent = "›";
  yNext.addEventListener("click", () => { pickerYear++; renderPicker(); });
  yearRow.append(yPrev, yLabel, yNext);

  // month grid
  const grid = document.createElement("div");
  grid.className = "tcal-picker-months";
  for (let i = 0; i < 12; i++) {
    const mBtn = document.createElement("button");
    mBtn.type = "button";
    // highlight the month currently shown on the calendar
    const isActive = i === viewMonth && pickerYear === viewYear;
    mBtn.className = "tcal-picker-month" + (isActive ? " is-active" : "");
    mBtn.textContent = MONTH_ABBR[i];
    mBtn.addEventListener("click", () => {
      viewMonth = i;
      viewYear = pickerYear;
      closePicker();
      renderCalendar();
    });
    grid.append(mBtn);
  }

  pickerEl.append(yearRow, grid);
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
  const ratings = avgRatingByDate(appState.studySessions || []);
  const total = daysInMonth(viewYear, viewMonth);
  // today's parts, so we only highlight "today" when its real month is on screen
  const isCurrentMonth =
    viewYear === today.getFullYear() && viewMonth === today.getMonth();
  for (let d = 1; d <= total; d++) {
    const cell = document.createElement("div");
    cell.className = "tcal-cell";
    if (isCurrentMonth && d === today.getDate()) cell.classList.add("tcal-cell--today");

    const num = document.createElement("div");
    num.className = "tcal-date";
    num.textContent = d;
    cell.append(num);

    const dayKey = isoDate(viewYear, viewMonth, d);

    // tasks due on this day → up to MAX_PILLS pills, then an "N more" row
    const dayTasks = grouped[dayKey] || [];
    for (const t of dayTasks.slice(0, MAX_PILLS)) {
      const pill = document.createElement("div");
      pill.className = "tcal-pill" + (t.done ? " tcal-pill--done" : "");
      pill.textContent = t.title;
      pill.title = t.title; // hover tooltip, in case the title is truncated
      cell.append(pill);
    }
    if (dayTasks.length > MAX_PILLS) {
      const more = document.createElement("div");
      more.className = "tcal-more";
      more.textContent = `${dayTasks.length - MAX_PILLS} more`;
      cell.append(more);
    }

    // a day with tasks is clickable → opens the popup listing all of them
    if (dayTasks.length > 0) {
      const day = d; // capture this cell's day for the click handler
      cell.classList.add("tcal-cell--has-tasks");
      cell.addEventListener("click", () => openDayPopup(viewYear, viewMonth, day, dayTasks));
    }

    // study-session rating for this day (rounded average) → stars, e.g. ★★★★ for 4
    const avgRating = ratings[dayKey];
    if (avgRating) {
      const rating = document.createElement("div");
      rating.className = "tcal-rating";
      rating.textContent = "★".repeat(avgRating);
      rating.title = `Avg study rating: ${avgRating}/5`;
      cell.append(rating);
    }

    gridEl.append(cell);
  }

  // --- trailing blanks to fill out the final row, so its grid lines are drawn (otherwise
  // the row stops after the last day and the cells past it have no borders). ---
  const placed = blanks + total;
  const trailing = (7 - (placed % 7)) % 7;
  for (let i = 0; i < trailing; i++) {
    const blank = document.createElement("div");
    blank.className = "tcal-cell tcal-cell--blank";
    gridEl.append(blank);
  }
}

function renderCalendar() {
  updateTitle();
  renderGrid();
}

// --- day popup: lists all tasks for one clicked day (view-only) ---
const popupEl = document.getElementById("dayPopup");
const popupTitleEl = document.getElementById("dayPopupTitle");
const popupBodyEl = document.getElementById("dayPopupBody");

function openDayPopup(year, month, day, tasks) {
  // Friendly heading like "Monday, 8 June 2026".
  popupTitleEl.textContent = new Date(year, month, day).toLocaleDateString("en-GB", {
    weekday: "long", day: "numeric", month: "long", year: "numeric",
  });

  popupBodyEl.innerHTML = "";
  for (const t of tasks) {
    const row = document.createElement("div");
    row.className = "tcal-popup-task";

    const title = document.createElement("div");
    title.className = "tcal-popup-task-title";
    title.textContent = t.title;
    if (t.done) title.style.textDecoration = "line-through";

    const meta = document.createElement("div");
    meta.className = "tcal-popup-task-meta";
    meta.textContent = t.type + (t.done ? " · done" : "");

    row.append(title, meta);
    popupBodyEl.append(row);
  }

  popupEl.style.display = "flex"; // CSS centres the card when shown
}

function closeDayPopup() {
  popupEl.style.display = "none";
}

// Close via the ✕ button, clicking the dim backdrop, or pressing Esc.
document.getElementById("dayPopupClose").addEventListener("click", closeDayPopup);
popupEl.addEventListener("click", (e) => {
  if (e.target === popupEl) closeDayPopup(); // only the backdrop, not the card
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") { closeDayPopup(); closePicker(); }
});

// Click anywhere outside the open picker (and not on the title that toggles it)
// closes it — the usual "click away to dismiss" behaviour.
document.addEventListener("click", (e) => {
  if (!pickerOpen) return;
  if (!pickerEl.contains(e.target) && !e.target.closest(".tcal-chevron-btn")) {
    closePicker();
  }
});

// Build the header once, then draw. Redraw whenever tasks change.
buildHeader();
window.addEventListener("modulo:datachanged", renderCalendar);
renderCalendar();
