// handbook.js — Phase 13 onboarding / handbook. Captures the per-semester setup:
// education level (editable for now — changing it with a saved timetable asks to
// confirm; per-handbook locking returns later), a LEVEL-AWARE academic year, the
// semester, and the term dates. Auto-opens on first run (while appState.handbookSetup
// is false); closing it then snoozes it until reload. Reuses the .tcal-popup recipe.

import { appState, persist } from "./data.js";
import { isTertiary, formatAcademicYear, parseStartYear, formatHeaderLabel } from "./logic/academicYear.js";

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

// firstRun = the auto-opened setup (vs the "Edit handbook" reopen) — controls the title.
let firstRun = false;
// Set when the user closes first-run setup WITHOUT saving, so it doesn't auto-reopen on
// the next datachanged. Resets on page reload (module re-evaluates).
let dismissed = false;

// Pretty names for the locked-level text + the Settings summary.
const LEVEL_NAMES = {
  primary: "Primary school", secondary: "Secondary school", jc: "Junior College",
  poly: "Polytechnic", university: "University",
};

// The label shown in the sidebar header (sidebar.js reuses this). Delegates the per-level
// format to the pure formatHeaderLabel (logic/academicYear.js) — one source of truth.
export function handbookHeaderLabel() {
  return formatHeaderLabel(appState.educationLevel, appState.academicYear, appState.semester);
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

// Fill the form from the current state, then show the modal.
export function openHandbook() {
  // Education level is EDITABLE for now (showcase): always show the dropdown. Changing it
  // with an existing timetable triggers a confirm in the Save handler. (The locked-text
  // field stays hidden in the DOM — it returns when per-handbook locking lands.)
  document.getElementById("hbLevelField").style.display = "";
  document.getElementById("hbLevelLockedField").style.display = "none";

  levelEl.value = appState.educationLevel || "";
  semesterEl.value = String(appState.semester || 1);
  const startYear = parseStartYear(appState.academicYear, appState.educationLevel);
  yearEl.value = startYear || "";
  termStartEl.value = appState.termStart || "";
  termEndEl.value = appState.termEnd || "";
  errorEl.textContent = "";
  closeX.style.display = "";   // the setup is now dismissable (showcase)
  document.getElementById("hbTitle").textContent =
    firstRun ? "Set up your handbook" : "Edit handbook";
  refreshYearUI();
  modal.style.display = "flex";   // .tcal-popup centres the card
}

function closeHandbook() {
  // If closing before setup is done, don't auto-reopen again this session (until reload).
  if (!appState.handbookSetup) dismissed = true;
  modal.style.display = "none";
}

// Close affordances: ✕, backdrop click, Esc.
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

  // Changing the level with a timetable already saved: warn (it was parsed under the old
  // level's rules). Non-destructive — we keep the timetable; the user can re-upload/edit.
  const prevLevel = appState.educationLevel;
  const hasTimetable = (appState.timetable?.modules || []).length > 0;
  if (prevLevel && level !== prevLevel && hasTimetable) {
    const ok = confirm(
      `Your saved timetable was set up for "${LEVEL_NAMES[prevLevel] || prevLevel}". ` +
      `Changing to "${LEVEL_NAMES[level] || level}" may not match it — you can re-upload ` +
      `or edit it afterward. Continue?`
    );
    if (!ok) return;   // keep the modal open, change nothing
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

// "Edit" button in Settings → open the handbook modal.
document.getElementById("editHandbookBtn").addEventListener("click", openHandbook);

// Render the read-only Settings summary from appState. Runs on every modulo:datachanged.
function renderSummary() {
  const box = document.getElementById("handbookSummary");
  if (!box) return;
  if (!appState.handbookSetup) {
    box.innerHTML = `<p class="hb-locked-note">Not set up yet — click Edit to get started.</p>`;
    return;
  }
  const breaks = appState.breaks || [];
  const rows = [
    ["Education level", LEVEL_NAMES[appState.educationLevel] || "—"],
    ["Academic year", handbookHeaderLabel() || "—"],
    ["Term start", appState.termStart || "—"],
    ["Term end", appState.termEnd || "—"],
    ["Recess / holiday", breaks.length ? breaks.map((b) => `${b.start} → ${b.end}`).join(", ") : "None"],
  ];
  box.innerHTML = rows
    .map(([k, v]) => `<div class="hb-row"><span class="hb-row-k">${k}</span><span class="hb-row-v">${v}</span></div>`)
    .join("");
}
window.addEventListener("modulo:datachanged", renderSummary);

// First-run trigger: after data loads (modulo:datachanged), if the handbook was never
// completed AND the modal isn't already open, auto-open it as the non-dismissable setup.
// The "already open" guard stops a stray datachanged from wiping what the user is typing.
window.addEventListener("modulo:datachanged", () => {
  if (!appState.handbookSetup && !dismissed && modal.style.display === "none") {
    firstRun = true;
    openHandbook();
  }
});
