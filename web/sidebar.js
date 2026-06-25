// sidebar.js — fills the dynamic parts of the sidebar (Phase 12.4d): the MODULES list
// (distinct modules from the timetable + tasks, each with a colour dot) and the account
// chip (current storage mode). Reads appState; redraws on modulo:datachanged.

import { appState, getStorageMode } from "./data.js";

// A fixed palette + a stable assignment: the same module name always maps to the same
// colour (sum of char codes mod palette length), independent of order. Exported so other
// views (e.g. the dashboard) can colour-match modules later.
// Cohesive cool spectrum (blues → indigos → violets → teals/cyans) so modules are
// distinguishable but none clashes with the blue dashboard. More entries = fewer repeats.
const PALETTE = [
  "#2f6df0", // royal blue (the dashboard accent)
  "#1c7ed6", // sky blue
  "#1b6ec2", // steel blue
  "#3b5bdb", // blue
  "#4263eb", // indigo
  "#5c7cfa", // periwinkle
  "#6741d9", // blue-violet
  "#7048e8", // violet
  "#5f3dc4", // deep violet
  "#862e9c", // purple
  "#1098ad", // cyan
  "#0c8599", // aqua
  "#0e8f9d", // teal
  "#087f8c", // deep teal
  "#0ca678", // turquoise
  "#099268", // emerald
];
export function moduleColor(name) {
  let sum = 0;
  for (let i = 0; i < name.length; i++) sum += name.charCodeAt(i);
  return PALETTE[sum % PALETTE.length];
}

// Distinct module labels from the timetable (code or name) + tasks, sorted, with any
// hidden modules (appState.hiddenModules, managed from the dashboard) filtered out.
function distinctModules() {
  const hidden = new Set(appState.hiddenModules || []);
  const set = new Set();
  for (const m of appState.timetable?.modules || []) {
    const label = m.code || m.name;
    if (label && !hidden.has(label)) set.add(label);
  }
  for (const t of appState.tasks || []) {
    if (t.module && !hidden.has(t.module)) set.add(t.module);
  }
  return [...set].sort();
}

// Render the MODULES list (or nothing if there are no modules yet).
function renderModules() {
  const el = document.getElementById("moduleNav");
  el.innerHTML = "";
  const mods = distinctModules();
  if (mods.length === 0) return;

  const label = document.createElement("div");
  label.className = "module-nav-label";
  label.textContent = "MODULES";
  el.append(label);

  for (const m of mods) {
    const row = document.createElement("div");
    row.className = "module-row";
    const dot = document.createElement("span");
    dot.className = "module-dot";
    dot.style.background = moduleColor(m); // inline colour from the stable palette
    const name = document.createElement("span");
    name.textContent = m;
    row.append(dot, name);
    el.append(row);
  }
}

// Render the account chip from the current storage mode.
function renderAccount() {
  const mode = getStorageMode(); // "drive" | "local" | null
  const modeEl = document.getElementById("accountMode");
  const subEl = document.getElementById("accountSub");
  if (mode === "drive") {
    modeEl.textContent = "Google Drive";
    subEl.textContent = "Synced";
  } else if (mode === "local") {
    modeEl.textContent = "Local mode";
    subEl.textContent = "This device only";
  } else {
    modeEl.textContent = "Not connected";
    subEl.textContent = "Tap to connect";
  }
}

function render() {
  renderModules();
  renderAccount();
}

window.addEventListener("modulo:datachanged", render);
render();
