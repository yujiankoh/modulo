// main.js — the entry point. index.html loads only this file (as a module); it
// pulls in everything else. It owns app boot (token client + restoring the saved
// mode) and the sign-in buttons, which are the one spot that needs both auth and data.

import { initTokenClient, getToken } from "./auth.js";
import { loadInitialData, setStorageMode, getSavedMode, getStorageMode, clearStorageMode, readLocalData, mirrorToLocal } from "./data.js";
import { loadData, saveData } from "./drive.js";
import { migrationPlan, dataSummary, driveIsNewer } from "./logic/migration.js";
import { withOverlay } from "./handbook.js";
import { setStatus } from "./ui.js";
import "./timetable.js"; // side-effect import: runs timetable's event wiring
import "./timetableEditor.js"; // side-effect import: runs the manual editor's wiring
import "./timetableView.js"; // side-effect import: renders the calendar grid
import "./calendarView.js"; // side-effect import: renders the task month-calendar
import "./task.js"; // side-effect import: task list UI + add/toggle/delete + Add button
import "./studyTimer.js"; // side-effect import: study-timer engine + controls (Phase 10)
import "./dashboard.js"; // side-effect import: Dashboard landing view (Phase 12.3)
import "./sidebar.js"; // side-effect import: sidebar modules list + account chip (Phase 12.4d)
import "./handbook.js"; // side-effect import: handbook onboarding modal (Phase 13)
import "./tour.js"; // side-effect import: first-run feature tour (Phase 22)
import "./cityView.js"; // side-effect import: Study City view (Phases 14+15)
import "./gradesView.js"; // side-effect import: Grades view — GPA cards + editor (Phase 17)
import "./notesView.js"; // side-effect import: Notes view — Drive note files (Phase 20)
import "./router.js"; // side-effect import: SPA view-switcher + hash routing (Phase 12)
import "./theme.js"; // side-effect import: light/dark theme toggle (Phase 12 polish)
import "./icons.js"; // side-effect import: renders Lucide icons (data-lucide → <svg>)

// The ONE Drive-connect flow (polish 2026-07-15: was inline on #connectBtn only) —
// opens Google's account chooser + consent popup. Shared by the Settings button,
// the topbar Connect button, and the landing (both variants).
//
// Phase 21: after the token, a device connecting for the FIRST time (or from
// local mode) goes through the migration check — read both sides, act on the
// pure decision matrix (logic/migration.js). Care points, in order:
//  - wasDrive is read BEFORE anything writes the mode (setStorageMode overwrites it).
//  - setStorageMode("drive") runs only AFTER the plan resolves — if the tab
//    closes mid-conflict-dialog, the device stays uncommitted and the next
//    connect re-runs the check.
//  - A failed Drive READ aborts with a message; it is never treated as "Drive
//    is empty" (that guess could overwrite a real file — step 3's hardening
//    makes the failure loud so we can catch it here).
async function connectDrive() {
  const ok = await getToken();
  if (!ok) return;

  if (getSavedMode() === "drive") {
    // Already a drive device (welcome-back / token renewal) — no check needed.
    setStorageMode("drive");
    loadInitialData();
    return;
  }

  let driveData = null;
  try {
    await withOverlay("Checking your Google Drive…", async () => {
      driveData = await loadData(); // null = genuinely no file yet
    });
  } catch (err) {
    setStatus(err.message || "Couldn't reach Google Drive — please try again.");
    return;
  }

  const localData = readLocalData();
  const plan = migrationPlan(localData, driveData);

  if (plan === "ask") {
    openConflictDialog(localData, driveData);
    return; // the dialog's buttons finish the connect
  }
  if (plan === "upload-local") {
    // req 2: this device's data is copied to the (meaningfully) empty Drive.
    // Upload FIRST, claim success after — saveData throws if it didn't happen.
    try {
      await withOverlay("Copying your data to Google Drive…", () => saveData(localData));
    } catch (err) {
      setStatus(err.message || "Couldn't copy your data to Google Drive — please try again.");
      return;
    }
    setStatus("Your data was copied to Google Drive.");
  }
  // "fresh", "use-drive", and a successful upload-local all end the same way:
  setStorageMode("drive");
  loadInitialData();
}

// ---- Connect-time conflict dialog (Phase 21, plan "ask") ----
// Both sides hold real data; the user picks one. Held module-level between
// opening and the button click that resolves it.
const conflictModal = document.getElementById("conflictModal");
let conflictLocal = null;
let conflictDrive = null;

function fillConflictSide(prefix, summary) {
  document.getElementById(prefix + "Saved").textContent = summary.updatedAt;
  document.getElementById(prefix + "Tasks").textContent = String(summary.taskCount);
  // The handbook line only shows when there's a label to show.
  document.getElementById(prefix + "Hb").textContent = summary.handbookLabel;
  document.getElementById(prefix + "HbLine").style.display = summary.handbookLabel ? "" : "none";
}

function openConflictDialog(localData, driveData) {
  conflictLocal = localData;
  conflictDrive = driveData;
  fillConflictSide("confDrive", dataSummary(driveData));
  fillConflictSide("confLocal", dataSummary(localData));
  conflictModal.style.display = "flex";
}

function closeConflictDialog() {
  conflictModal.style.display = "none";
  conflictLocal = conflictDrive = null;
}

// The ✕ = "cancel connecting, leave me as I was". No mode is set and nothing
// loads, so the device keeps whatever state it had before Connect: still local
// mode (Settings-connect), or back to the landing overlay showing underneath
// (first-visit drive choice). Safe precisely because we commit the mode LAST.
document.getElementById("conflictClose").addEventListener("click", closeConflictDialog);

// Keep Drive — the default/safe side. The localStorage copy is deliberately
// left untouched: a stale safety net, not a bug (roadmap-documented).
document.getElementById("conflictKeepBtn").addEventListener("click", () => {
  closeConflictDialog();
  setStorageMode("drive");
  loadInitialData();
});

// Replace Drive — the destructive side: confirm() spells out the consequence,
// with an EXTRA line when Drive's copy is the more recent one.
document.getElementById("conflictReplaceBtn").addEventListener("click", async () => {
  const extra = driveIsNewer(conflictLocal, conflictDrive)
    ? "\n\n⚠ Your Google Drive copy was saved MORE RECENTLY than this device's data."
    : "";
  const sure = confirm(
    "Replace the data in your Google Drive with this device's data?\n\n" +
    "The Drive copy will be overwritten and can't be recovered." + extra +
    "\n\nReplace anyway?"
  );
  if (!sure) return; // back to the dialog — still undecided
  const localData = conflictLocal;
  closeConflictDialog();
  try {
    await withOverlay("Copying your data to Google Drive…", () => saveData(localData));
  } catch (err) {
    // Upload failed → nothing was committed (no mode set). Reconnecting
    // re-runs the whole check.
    setStatus(err.message || "Couldn't copy your data to Google Drive — please try again.");
    return;
  }
  setStorageMode("drive");
  loadInitialData();
});

// Landing overlay (Phase 21): the full-screen mode chooser. Shown while NO mode
// is active AND none will boot on its own (saved local mode boots straight in).
// Variant: a remembered drive device gets "Welcome back" instead of the choice.
// Re-checked on modulo:datachanged — loadInitialData fires it at the end of
// every successful boot, which is what hides the overlay; if the Google popup
// is closed or blocked, no event fires and the landing simply stays.
const landing = document.getElementById("landing");
function renderLanding() {
  if (getStorageMode() || getSavedMode() === "local") {
    // Leaving the landing (it was actually SHOWING — not just a redundant
    // re-check mid-session): a sign-in/first choice always lands on the
    // Dashboard, instead of whatever tab the URL hash still remembered.
    if (landing.style.display === "flex") location.hash = "#dashboard";
    landing.style.display = "none";
    return;
  }
  const returning = getSavedMode() === "drive";
  document.getElementById("landingChoice").style.display = returning ? "none" : "";
  document.getElementById("landingReturn").style.display = returning ? "" : "none";
  landing.style.display = "flex";
}
window.addEventListener("modulo:datachanged", renderLanding);
renderLanding();

// Local mode start — shared by the landing (both variants' local actions) and
// the Settings sync card, so the behaviour can't drift between them.
// Phase 21: switching FROM an active Drive session mirrors the current data into
// the local store first, so local mode continues from what's on screen (not a
// stale copy). Guarded on "drive": from the landing the mode is null and nothing
// is loaded, so mirroring there would clobber real local data with defaults.
function startLocalMode() {
  if (getStorageMode() === "drive") mirrorToLocal();
  setStorageMode("local");
  loadInitialData();
}
document.getElementById("landingDriveBtn").addEventListener("click", connectDrive);
document.getElementById("landingReturnBtn").addEventListener("click", connectDrive);
document.getElementById("landingLocalBtn").addEventListener("click", startLocalMode);
document.getElementById("landingLocalLink").addEventListener("click", startLocalMode);

// The topbar Connect button shows ONLY while no storage mode is active (fresh visit,
// or drive mode saved but the token not yet renewed). Re-checked on every
// modulo:datachanged — connecting or picking local mode hides it.
const topbarConnect = document.getElementById("topbarConnect");
function updateTopbarConnect() {
  topbarConnect.style.display = getStorageMode() ? "none" : "";
}
window.addEventListener("modulo:datachanged", updateTopbarConnect);
updateTopbarConnect();

// Settings sync card (2026-07-16): show only the controls that apply to the
// current mode, with a one-line status — the card used to offer every button
// in every state ("Reload from Drive" in local mode just errored).
function renderSyncCard() {
  const mode = getStorageMode();
  document.getElementById("syncStatus").textContent =
    mode === "drive" ? "Connected to Google Drive — your data syncs across devices."
    : mode === "local" ? "Local mode — your data stays on this device only."
    : "Not connected — choose where MODULO keeps your data.";
  document.getElementById("connectBtn").style.display = mode === "drive" ? "none" : "";
  document.getElementById("localBtn").style.display = mode === "local" ? "none" : "";
  document.getElementById("reloadBtn").style.display = mode === "drive" ? "" : "none";
  document.getElementById("disconnectBtn").style.display = mode === "drive" ? "" : "none";
}
window.addEventListener("modulo:datachanged", renderSyncCard);
renderSyncCard();

// Disconnect = forget the mode on THIS device, then a clean reboot. Data is NOT
// deleted — the file stays in the user's Drive (and the Google authorisation
// stays until revoked in their Google settings). Reloading matters: without a
// mode, persist() saves nowhere, so the app must not keep running on memory.
document.getElementById("disconnectBtn").addEventListener("click", () => {
  const ok = confirm(
    "Disconnect Google Drive on this device?\n\n" +
    "This device stops syncing. Your data stays safe in your Google Drive — " +
    "connect again anytime to pick up where you left off."
  );
  if (!ok) return;
  clearStorageMode();
  location.reload();
});

// Runs after the page + Google's library have finished loading.
window.onload = () => {
  initTokenClient();

  // Restore the user's saved mode on reload. No status messages here any more —
  // the account chip shows the mode, and the Connect button is the call to action.
  const savedMode = getSavedMode();
  if (savedMode === "local") {
    setStorageMode("local");
    loadInitialData();
  }
  // savedMode "drive": wait for the user to click Connect (tokens don't survive reloads).
};

document.getElementById("connectBtn").addEventListener("click", connectDrive);
topbarConnect.addEventListener("click", connectDrive);
// (The account chip is a pure indicator since 2026-07-15 — no click wiring.)

document.getElementById("localBtn").addEventListener("click", startLocalMode);
