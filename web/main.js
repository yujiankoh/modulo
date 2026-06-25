// main.js — the entry point. index.html loads only this file (as a module); it
// pulls in everything else. It owns app boot (token client + restoring the saved
// mode) and the sign-in buttons, which are the one spot that needs both auth and data.

import { initTokenClient, getToken } from "./auth.js";
import { loadInitialData, setStorageMode, getSavedMode } from "./data.js";
import { setStatus } from "./ui.js";
import "./timetable.js"; // side-effect import: runs timetable's event wiring
import "./timetableEditor.js"; // side-effect import: runs the manual editor's wiring
import "./timetableView.js"; // side-effect import: renders the calendar grid
import "./calendarView.js"; // side-effect import: renders the task month-calendar
import "./task.js"; // side-effect import: task list UI + add/toggle/delete + Add button
import "./studyTimer.js"; // side-effect import: study-timer engine + controls (Phase 10)
import "./router.js"; // side-effect import: SPA view-switcher + hash routing (Phase 12)

// Runs after the page + Google's library have finished loading.
window.onload = () => {
  initTokenClient();

  // Restore the user's saved mode on reload.
  const savedMode = getSavedMode();
  if (savedMode === "local") {
    setStorageMode("local");
    setStatus("Local mode (this device only).");
    loadInitialData();
  } else if (savedMode === "drive") {
    setStatus("Click Connect Google Drive to resume sync.");
    // Need to connect first and get a token.
  }
};

// Clicking Connect opens Google's account chooser + consent popup.
document.getElementById("connectBtn").addEventListener("click", async () => {
  const ok = await getToken();
  if (ok) {
    setStorageMode("drive");
    loadInitialData();
  }
});

document.getElementById("localBtn").addEventListener("click", () => {
  setStorageMode("local");
  setStatus("Local mode (this device only).");
  loadInitialData();
});
