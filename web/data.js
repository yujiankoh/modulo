// data.js — MODULO's in-memory state + how it's saved/loaded + task logic + render.
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
  tasks: [],
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

// Save the WHOLE current state (stamping the time first).
export async function persist() {
  appState.updatedAt = new Date().toISOString();
  if (storageMode === "drive") {
    if (!(await ensureToken())) { setStatus("Please reconnect Google Drive."); return; }
    await saveData(appState);
  } else if (storageMode === "local") {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(appState));
  }
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
    const eduEl = document.getElementById("eduLevel");
    if (appState.educationLevel) eduEl.value = appState.educationLevel;
    if (!appState.tasks) appState.tasks = [];
  }
  render();
}

// Each action changes memory, saves, then re-draws. Private to this module —
// the buttons that call them are wired below.
async function addTask(title, due, type) {
  const now = new Date().toISOString();
  appState.tasks.push({
    id: Date.now(),
    title,
    due,
    type,
    done: false,
    createdAt: now,
    updatedAt: now,   // updated whenever the task changes
  });
  await persist();
  render();
}

// Toggle a task's completion status.
async function toggleTask(id) {
  const task = appState.tasks.find((t) => t.id === id);
  if (task) {
    task.done = !task.done;
    task.updatedAt = new Date().toISOString();   // stamp the change
  }
  await persist();
  render();
}

async function deleteTask(id) {
  appState.tasks = appState.tasks.filter((t) => t.id !== id); // keep all EXCEPT this id
  await persist();
  render();
}

// Show the current state onto the page.
export function render() {
  const list = document.getElementById("taskList");
  list.innerHTML = ""; // wipe the list, then rebuild it from appState

  if (appState.tasks.length === 0) {
    list.innerHTML = "<li>No tasks yet.</li>";
    return;
  }

  appState.tasks.forEach((task) => {
    const li = document.createElement("li");
    li.textContent = `${task.title} — ${task.type} — due ${task.due || "no date"} `;
    if (task.done) li.style.textDecoration = "line-through";

    const toggleBtn = document.createElement("button");
    toggleBtn.textContent = task.done ? "Undo" : "Done";
    toggleBtn.addEventListener("click", () => toggleTask(task.id));

    const delBtn = document.createElement("button");
    delBtn.textContent = "Delete";
    delBtn.addEventListener("click", () => deleteTask(task.id));

    li.append(" ", toggleBtn, " ", delBtn);
    list.append(li);
  });
}

// Wire the task UI buttons (this module owns them).
document.getElementById("addBtn").addEventListener("click", () => {
  if (!storageMode) { alert("Choose Google Drive or local mode first."); return; }
  const title = document.getElementById("taskTitle").value.trim();
  const due = document.getElementById("taskDue").value;
  const type = document.getElementById("taskType").value;
  if (!title) { alert("Enter a task title."); return; }
  addTask(title, due, type);
  document.getElementById("taskTitle").value = ""; // clear the input
});

document.getElementById("reloadBtn").addEventListener("click", () => {
  if (!storageMode) { alert("Choose Google Drive or local mode first."); return; }
  loadInitialData();
});
