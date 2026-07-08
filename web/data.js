// data.js — MODULO's in-memory state + how it's saved/loaded (the storage layer).
// Task logic + the task-list UI live in task.js; views redraw via modulo:datachanged.
// Depends on auth (token), drive (Drive read/write), ui (status line).

import { ensureToken } from "./auth.js";
import { saveData, loadData } from "./drive.js";
import { setStatus } from "./ui.js";

const LOCAL_KEY = "modulo-data";   // localStorage key for the data itself
const MODE_KEY = "modulo-mode";    // localStorage key for the chosen storage mode

let storageMode = null;            // "drive" | "local" | null — private to this module

// MODULO app state: what is held in memory. `export let` so other modules can read
// it; only this module reassigns it (loadInitialData does, and importers see the
// new value because module imports are live).
export let appState = {
  schemaVersion: 2,        // was 1 — we finalized the timetable structure + added educationLevel
  educationLevel: null,
  academicYear: null,      // Phase 13: e.g. "25/26" — handbook; drives the sidebar header
  semester: null,          // Phase 13: 1 or 2 — handbook; drives the sidebar header
  handbookSetup: false,    // Phase 13: true once onboarding is done (gates the first-run modal)
  handbookId: crypto.randomUUID(), // Phase 13.5: id of the ACTIVE handbook (= these flat fields)
  termStart: null,         // "YYYY-MM-DD" Week-1 Monday anchor (Phase 8); null until set
  termEnd: null,           // "YYYY-MM-DD" last day of term; weeks past it are "outside term"
  breaks: [],              // [{ start, end }] date ranges of recess/holiday (non-academic) weeks
  tasks: [],
  studySessions: [],       // Phase 10: recorded focus/study sessions (see modulo-data-schema.md)
  cityLevel: 0,            // Phase 14: study-city level BUILT so far (GLOBAL, like studySessions)
  hiddenModules: [],       // Phase 12: module labels hidden from the dashboard + sidebar
  otherHandbooks: [],      // Phase 13.5: the INACTIVE handbooks (see logic/handbooks.js)
  timetable: null,
  updatedAt: null,
};

// Set the storage backend and remember the choice across reloads.
export function setStorageMode(mode) {
  storageMode = mode;
  localStorage.setItem(MODE_KEY, mode);
}

// What mode (if any) the user picked last time. main.js uses this on boot.
export function getSavedMode() {
  return localStorage.getItem(MODE_KEY);
}

// The active in-memory mode ("drive" | "local" | null). task.js reads it to guard actions.
export function getStorageMode() {
  return storageMode;
}

// Save the WHOLE current state (stamping the time first).
export async function persist() {
  appState.updatedAt = new Date().toISOString();
  if (storageMode === "drive") {
    if (!(await ensureToken())) { setStatus("Please reconnect Google Drive."); return; }
    await saveData(appState);
  } else if (storageMode === "local") {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(appState));
  }
  // Tell any interested view (e.g. the timetable display) that state changed.
  window.dispatchEvent(new Event("modulo:datachanged"));
}

// Load the saved state into memory, then draw it.
export async function loadInitialData() {
  let saved = null;
  if (storageMode === "drive") {
    if (!(await ensureToken())) { setStatus("Please reconnect Google Drive."); return; }
    saved = await loadData();
  } else if (storageMode === "local") {
    const raw = localStorage.getItem(LOCAL_KEY);
    saved = raw ? JSON.parse(raw) : null;
  }
  if (saved) {
    appState = saved;
    // (Phase 13) The education-level + term-date inputs moved into the handbook modal,
    // which reads appState directly when opened — no DOM restore needed here.
    if (!appState.tasks) appState.tasks = [];
    if (!appState.breaks) appState.breaks = []; // default for files saved before Phase 8
    if (!appState.studySessions) appState.studySessions = []; // default for files saved before Phase 10
    if (!appState.hiddenModules) appState.hiddenModules = []; // default for files saved before Phase 12
    if (appState.academicYear === undefined) appState.academicYear = null; // Phase 13
    if (appState.semester === undefined) appState.semester = null;         // Phase 13
    // Migrate files saved before Phase 13: no handbookSetup flag. If a level was already
    // chosen, treat the handbook as done (don't re-nag); otherwise it's a fresh first run.
    if (appState.handbookSetup === undefined) appState.handbookSetup = !!appState.educationLevel;
    // Migrate files saved before Phase 13.5: the single existing handbook gets an id,
    // and there are no other handbooks yet.
    if (!appState.handbookId) appState.handbookId = crypto.randomUUID();
    if (!appState.otherHandbooks) appState.otherHandbooks = [];
    // Default for files saved before Phase 14. Integer check (not just "missing") so a
    // corrupt value also resets — growth.js clamps at read-time anyway, belt and braces.
    if (!Number.isInteger(appState.cityLevel)) appState.cityLevel = 0;
  }
  // Announce the load; every view (task list, calendar, timetable) redraws from this.
  window.dispatchEvent(new Event("modulo:datachanged"));
}

// "Reload from Drive" — a storage action, so it stays here (task UI lives in task.js).
document.getElementById("reloadBtn").addEventListener("click", () => {
  if (!storageMode) { alert("Choose Google Drive or local mode first."); return; }
  loadInitialData();
});
