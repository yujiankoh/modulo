// handbook.js — Phase 13 onboarding / handbook. Captures the per-semester setup:
// education level (editable for now — changing it with a saved timetable asks to
// confirm; per-handbook locking returns later), a LEVEL-AWARE academic year, the
// semester, and the term dates. Auto-opens on first run (while appState.handbookSetup
// is false); closing it then snoozes it until reload. Reuses the .tcal-popup recipe.

import { appState, persist } from "./data.js";
import { isTertiary, formatAcademicYear, parseStartYear, formatHeaderLabel } from "./logic/academicYear.js";
import { snapshotHandbook, blankHandbook, switchHandbook } from "./logic/handbooks.js"; // 13.5: pure swap helpers
import { drawIcons } from "./icons.js"; // redraw the trash icons the handbook list injects

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
  // Education level is locked PER HANDBOOK (13.5): chosen while the handbook is being
  // created (first run / Start new semester — handbookSetup still false), then shown as
  // read-only text. It drives the timetable parser + editor rules, so changing it
  // mid-handbook could orphan the timetable; a different level = start a new semester.
  const locked = appState.handbookSetup;
  document.getElementById("hbLevelField").style.display = locked ? "none" : "";
  document.getElementById("hbLevelLockedField").style.display = locked ? "" : "none";
  if (locked) {
    document.getElementById("hbLevelLocked").textContent =
      LEVEL_NAMES[appState.educationLevel] || appState.educationLevel || "—";
  }

  // Keep the (possibly hidden) dropdown in sync — the Save handler and the year
  // preview both read levelEl.value, locked or not.
  levelEl.value = appState.educationLevel || "";
  semesterEl.value = String(appState.semester || 1);
  const startYear = parseStartYear(appState.academicYear, appState.educationLevel);
  yearEl.value = startYear || new Date().getFullYear();   // default a fresh handbook to this year
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

  // (13.5) No level-change warning needed anymore: once a handbook is set up its level
  // dropdown is hidden, so `level` here can only differ from the stored one during
  // handbook CREATION — when there's no timetable to mismatch yet.

  appState.educationLevel = level;
  appState.academicYear = formatAcademicYear(startYear, level);
  appState.semester = parseInt(semesterEl.value, 10);
  appState.termStart = ts || null;
  appState.termEnd = te || null;
  appState.handbookSetup = true;

  firstRun = false;
  await withOverlay("Saving handbook…", persist); // fires modulo:datachanged → views redraw
  modal.style.display = "none";
});

// "Edit" button in Settings → open the handbook modal.
document.getElementById("editHandbookBtn").addEventListener("click", openHandbook);

// --- Start new semester (Phase 13.5) ---------------------------------------
// Store the current handbook in otherHandbooks and reset the flat fields to a fresh,
// not-set-up handbook — in ONE persist(). We never open the modal here: the fresh
// handbook has handbookSetup:false, so the first-run listener below sees the
// modulo:datachanged from persist() and opens onboarding exactly like a first run.
document.getElementById("newSemesterBtn").addEventListener("click", async () => {
  if (!appState.handbookSetup) {
    alert("Finish setting up your current handbook first.");
    return;
  }
  const label = handbookHeaderLabel() || "your current handbook";
  const ok = confirm(
    `Start a new semester? ${label} will be saved — you can switch back to it anytime.`
  );
  if (!ok) return;

  // 1) Current handbook → otherHandbooks (deep-copied snapshot, id included).
  appState.otherHandbooks = [...(appState.otherHandbooks || []), snapshotHandbook(appState)];

  // 2) Flat fields → a blank handbook with a fresh identity.
  const { id, ...fields } = blankHandbook(crypto.randomUUID());
  appState.handbookId = id;
  Object.assign(appState, fields);   // resets every per-handbook field, incl. handbookSetup:false

  dismissed = false;                 // a snoozed modal must still prompt for the NEW semester
  await persist();                   // one atomic save → datachanged → first-run modal opens
});

// --- Handbook list + Switch (Phase 13.5) ------------------------------------
// Settings shows one row per handbook: the active one badged, the others with a
// Switch button. (No modal can be open when Switch is clicked — any open modal's
// backdrop covers the whole page — so there's no stale-modal state to defend against.)

// A Promise that resolves after ms — lets us `await` a pause.
function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Keep the loading overlay up at least this long: a local save is instant, and a
// few-ms flash of overlay reads as a glitch; ~400ms reads as a deliberate transition.
const MIN_OVERLAY_MS = 400;

// Run an async action behind the loading overlay, showing `message` beside the
// spinner. Waits for BOTH the action and the minimum display time (whichever is
// longer); the finally guarantees the overlay never gets stuck up, even on an error.
async function withOverlay(message, action) {
  const overlay = document.getElementById("switchOverlay");
  document.getElementById("switchOverlayText").textContent = message;
  overlay.style.display = "flex";
  try {
    await Promise.all([action(), delay(MIN_OVERLAY_MS)]);
  } finally {
    overlay.style.display = "none";
  }
}

// Make the handbook with `targetId` active. All the thinking is in the pure
// switchHandbook (logic/handbooks.js); this applies its result and saves ONCE,
// behind the loading overlay.
async function switchToHandbook(targetId) {
  const result = switchHandbook(appState, targetId);
  if (!result) return;                             // unknown id / already active → no-op
  await withOverlay("Switching handbook…", async () => {
    Object.assign(appState, result.flat);            // target's fields (incl. handbookId) in
    appState.otherHandbooks = result.otherHandbooks; // current handbook stored in its place
    await persist();                                 // ONE save → every view redraws swapped
  });
}

// Delete a STORED handbook (the active one has no delete button — switch away first).
// Destructive + unrecoverable, so the confirm() names exactly what's being deleted.
async function deleteHandbook(targetId, label) {
  const target = (appState.otherHandbooks || []).find((h) => h.id === targetId);
  if (!target) return;
  const taskCount = (target.tasks || []).length;
  const ok = confirm(
    `Delete ${label || "this handbook"}? Its timetable and ${taskCount} task(s) ` +
    `will be permanently deleted. This cannot be undone.`
  );
  if (!ok) return;
  appState.otherHandbooks = appState.otherHandbooks.filter((h) => h.id !== targetId);
  await persist();   // the datachanged redraw removes the row
}

// Rebuild the list from state. Hidden entirely while there's only one handbook
// (no point listing a single row with no action).
function renderHandbookList() {
  const box = document.getElementById("handbookList");
  const others = appState.otherHandbooks || [];
  box.innerHTML = "";
  if (others.length === 0) { box.style.display = "none"; return; }
  box.style.display = "";

  // Active handbook first, then the stored ones in stored order.
  const entries = [
    { id: appState.handbookId, label: handbookHeaderLabel(),
      level: appState.educationLevel, active: true },
    ...others.map((h) => ({
      id: h.id,
      label: formatHeaderLabel(h.educationLevel, h.academicYear, h.semester),
      level: h.educationLevel,
      active: false,
    })),
  ];

  for (const entry of entries) {
    const row = document.createElement("div");
    row.className = "hb-list-row";

    const label = document.createElement("span");
    label.className = "hb-list-label";
    label.textContent = entry.label || "Not set up yet";

    const level = document.createElement("span");
    level.className = "hb-list-level";
    level.textContent = LEVEL_NAMES[entry.level] || "";

    row.append(label, level);

    if (entry.active) {
      const badge = document.createElement("span");
      badge.className = "hb-active-badge";
      badge.textContent = "Active";
      row.append(badge);
    } else {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "hb-switch-btn";
      btn.textContent = "Switch";
      btn.addEventListener("click", () => switchToHandbook(entry.id)); // closes over ITS id

      const del = document.createElement("button");
      del.type = "button";
      del.className = "hb-del-btn";
      del.innerHTML = `<i data-lucide="trash-2"></i>`;
      del.title = "Delete handbook";
      del.setAttribute("aria-label", `Delete ${entry.label || "handbook"}`);
      del.addEventListener("click", () => deleteHandbook(entry.id, entry.label));

      row.append(btn, del);
    }
    box.append(row);
  }
  drawIcons();   // render the freshly-injected <i data-lucide> placeholders
}
window.addEventListener("modulo:datachanged", renderHandbookList);
renderHandbookList();

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
