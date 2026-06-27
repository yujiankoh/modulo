// handbook.js — Phase 13 onboarding / handbook. Captures the per-semester setup once:
// education level (locked after first run — Step 3), a LEVEL-AWARE academic year, the
// semester, and the term dates. Auto-opens as a non-dismissable modal on first run
// (when appState.handbookSetup is still false). Reuses the .tcal-popup modal recipe.

import { appState, persist } from "./data.js";

// --- DOM handles (all inside #handbookModal) ---
const modal = document.getElementById("handbookModal");
const closeX = document.getElementById("hbCloseX");
const levelEl = document.getElementById("hbLevel");
const yearEl = document.getElementById("hbYear");
const yearLabelEl = document.getElementById("hbYearLabel");
const yearPreviewEl = document.getElementById("hbYearPreview");
const semesterEl = document.getElementById("hbSemester");
const termStartEl = document.getElementById("hbTermStart");
const termEndEl = document.getElementById("hbTermEnd");
const saveBtn = document.getElementById("hbSaveBtn");
const errorEl = document.getElementById("hbError");

// True while the FIRST-RUN modal is showing — it can't be dismissed until saved.
let firstRun = false;

// Tertiary levels (uni/poly) span TWO calendar years -> "25/26". School levels
// (primary/secondary/jc) are a SINGLE calendar year -> "2026".
const TERTIARY = ["university", "poly"];
function isTertiary(level) { return TERTIARY.includes(level); }

// Build the STORED academicYear string from a start year + level.
//   tertiary 2025 -> "25/26"      school 2026 -> "2026"
function formatAcademicYear(startYear, level) {
  if (!startYear) return null;
  if (isTertiary(level)) {
    const a = String(startYear).slice(-2);        // 2025 -> "25"
    const b = String(startYear + 1).slice(-2);    // 2026 -> "26"
    return `${a}/${b}`;
  }
  return String(startYear);                        // "2026"
}

// The reverse: from a stored academicYear back to the start-year NUMBER for the input.
//   "25/26" -> 2025      "2026" -> 2026
function parseStartYear(academicYear, level) {
  if (!academicYear) return null;
  if (isTertiary(level)) return 2000 + parseInt(academicYear.split("/")[0], 10); // "25" -> 2025
  return parseInt(academicYear, 10);
}

// The label shown in the sidebar header (Step 4 reuses this — kept here so the format
// lives in ONE place). "" when not enough is set yet.
export function handbookHeaderLabel() {
  const { educationLevel, academicYear, semester } = appState;
  if (!academicYear || !semester) return "";
  return isTertiary(educationLevel)
    ? `AY${academicYear} · S${semester}`
    : `${academicYear} · Sem ${semester}`;
}

// Relabel the year field + refresh the live "Will show as: …" preview whenever the
// level / year / semester changes. Teaches the user exactly what gets stored.
function refreshYearUI() {
  const level = levelEl.value;
  const tertiary = isTertiary(level);
  yearLabelEl.textContent = tertiary ? "Academic year (starting year)" : "Year";
  yearEl.placeholder = tertiary ? "e.g. 2025" : "e.g. 2026";

  if (!level) { yearPreviewEl.textContent = "Pick your education level first."; return; }
  const startYear = parseInt(yearEl.value, 10);
  if (!startYear) { yearPreviewEl.textContent = ""; return; }

  const ay = formatAcademicYear(startYear, level);
  const sem = semesterEl.value;
  yearPreviewEl.textContent = tertiary
    ? `Will show as: AY${ay} · S${sem}`
    : `Will show as: ${ay} · Sem ${sem}`;
}

// Fill the form from the current state, then show the modal. On first run the close
// ✕ is hidden (the modal is non-dismissable until the user saves).
export function openHandbook() {
  levelEl.value = appState.educationLevel || "";
  semesterEl.value = String(appState.semester || 1);
  const startYear = parseStartYear(appState.academicYear, appState.educationLevel);
  yearEl.value = startYear || "";
  termStartEl.value = appState.termStart || "";
  termEndEl.value = appState.termEnd || "";
  errorEl.textContent = "";
  closeX.style.display = firstRun ? "none" : "";
  refreshYearUI();
  modal.style.display = "flex";   // .tcal-popup centres the card
}

function closeHandbook() {
  if (firstRun) return;           // can't dismiss the first-run setup until it's saved
  modal.style.display = "none";
}

// Close affordances (skipped during first run by closeHandbook's guard).
closeX.addEventListener("click", closeHandbook);
modal.addEventListener("click", (e) => { if (e.target === modal) closeHandbook(); });
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && modal.style.display !== "none") closeHandbook();
});

// Live preview wiring.
levelEl.addEventListener("change", refreshYearUI);
yearEl.addEventListener("input", refreshYearUI);
semesterEl.addEventListener("change", refreshYearUI);

// Save: validate, write everything to appState, mark the handbook done, persist, close.
saveBtn.addEventListener("click", async () => {
  const level = levelEl.value;
  const startYear = parseInt(yearEl.value, 10);
  const ts = termStartEl.value;
  const te = termEndEl.value;

  if (!level) { errorEl.textContent = "Please choose your education level."; return; }
  if (!startYear) { errorEl.textContent = "Please enter your academic year."; return; }
  if (ts && te && te < ts) {           // ISO date strings compare chronologically
    errorEl.textContent = "Term end can't be before term start."; return;
  }

  appState.educationLevel = level;
  appState.academicYear = formatAcademicYear(startYear, level);
  appState.semester = parseInt(semesterEl.value, 10);
  appState.termStart = ts || null;
  appState.termEnd = te || null;
  appState.handbookSetup = true;

  firstRun = false;
  await persist();                     // fires modulo:datachanged → views (+ sidebar header) redraw
  modal.style.display = "none";
});

// First-run trigger: after data loads (modulo:datachanged), if the handbook was never
// completed AND the modal isn't already open, auto-open it as the non-dismissable setup.
// The "already open" guard stops a stray datachanged from wiping what the user is typing.
window.addEventListener("modulo:datachanged", () => {
  if (!appState.handbookSetup && modal.style.display === "none") {
    firstRun = true;
    openHandbook();
  }
});
