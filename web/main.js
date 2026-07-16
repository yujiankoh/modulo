// main.js — the entry point. index.html loads only this file (as a module); it
// pulls in everything else. It owns app boot (token client + restoring the saved
// mode) and the sign-in buttons, which are the one spot that needs both auth and data.

import { initTokenClient, getToken } from "./auth.js";
import { loadInitialData, setStorageMode, getSavedMode, getStorageMode, clearStorageMode } from "./data.js";
import "./timetable.js"; // side-effect import: runs timetable's event wiring
import "./timetableEditor.js"; // side-effect import: runs the manual editor's wiring
import "./timetableView.js"; // side-effect import: renders the calendar grid
import "./calendarView.js"; // side-effect import: renders the task month-calendar
import "./task.js"; // side-effect import: task list UI + add/toggle/delete + Add button
import "./studyTimer.js"; // side-effect import: study-timer engine + controls (Phase 10)
import "./dashboard.js"; // side-effect import: Dashboard landing view (Phase 12.3)
import "./sidebar.js"; // side-effect import: sidebar modules list + account chip (Phase 12.4d)
import "./handbook.js"; // side-effect import: handbook onboarding modal (Phase 13)
import "./cityView.js"; // side-effect import: Study City view (Phases 14+15)
import "./gradesView.js"; // side-effect import: Grades view — GPA cards + editor (Phase 17)
import "./notesView.js"; // side-effect import: Notes view — Drive note files (Phase 20)
import "./router.js"; // side-effect import: SPA view-switcher + hash routing (Phase 12)
import "./theme.js"; // side-effect import: light/dark theme toggle (Phase 12 polish)
import "./icons.js"; // side-effect import: renders Lucide icons (data-lucide → <svg>)

// The ONE Drive-connect flow (polish 2026-07-15: was inline on #connectBtn only) —
// opens Google's account chooser + consent popup, then loads data on success.
// Shared by the Settings button, the topbar Connect button, and the account chip.
async function connectDrive() {
  const ok = await getToken();
  if (ok) {
    setStorageMode("drive");
    loadInitialData();
  }
}

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

document.getElementById("localBtn").addEventListener("click", () => {
  setStorageMode("local");
  loadInitialData();
});
