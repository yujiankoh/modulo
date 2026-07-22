// sidebar.js — fills the dynamic parts of the sidebar (Phase 12.4d): the MODULES list
// (distinct modules from the timetable + tasks, each with a colour dot) and the account
// chip (current storage mode). Reads appState; redraws on modulo:datachanged.

import { appState, getStorageMode } from "./data.js";
import { isTokenValid } from "./auth.js"; // Phase 22: chip tells the TRUTH about the session
import { handbookHeaderLabel } from "./handbook.js";
import { openModuleByLabel } from "./dashboard.js"; // open the module detail modal on click

// A fixed palette + a stable assignment: the same module name always maps to the same
// colour (sum of char codes mod palette length), independent of order. Exported so other
// views (e.g. the dashboard) can colour-match modules later.
// Cohesive cool spectrum so modules are distinguishable but none clashes with the blue UI.
// Two palettes, hue-aligned by index, so a module keeps "its" hue across themes: the LIGHT
// set is mid-toned for white cards; the DARK set is brighter so dots/headers pop on navy.
const PALETTE_LIGHT = [
  "#2f6df0", "#1c7ed6", "#1b6ec2", "#3b5bdb", "#4263eb", "#5c7cfa", "#6741d9", "#7048e8",
  "#5f3dc4", "#862e9c", "#1098ad", "#0c8599", "#0e8f9d", "#087f8c", "#0ca678", "#099268",
];
const PALETTE_DARK = [
  "#4dabf7", "#74c0fc", "#5c7cfa", "#748ffc", "#9775fa", "#845ef7", "#be4bdb", "#cc5de8",
  "#22b8cf", "#3bc9db", "#66d9e8", "#20c997", "#12b886", "#38d9a9", "#40c057", "#51cf66",
];

// Deterministic colour for a module name (same name → same index), picked from the palette
// that matches the current theme. Re-evaluated on each render (the theme toggle fires a redraw).
export function moduleColor(name) {
  let sum = 0;
  for (let i = 0; i < name.length; i++) sum += name.charCodeAt(i);
  const palette = document.documentElement.dataset.theme === "dark" ? PALETTE_DARK : PALETTE_LIGHT;
  return palette[sum % palette.length];
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
    row.addEventListener("click", () => openModuleByLabel(m)); // open its detail modal
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
    // "Synced" only when the token is actually valid; once it lapses the chip says
    // "Reconnect" instead of falsely claiming everything's in sync (Phase 22).
    subEl.textContent = isTokenValid() ? "Synced" : "Reconnect";
  } else if (mode === "local") {
    modeEl.textContent = "Local mode";
    subEl.textContent = "This device only";
  } else {
    // Pure indicator (2026-07-15) — no "Tap to connect": the chip isn't clickable;
    // the topbar Connect button is the action.
    modeEl.textContent = "Not connected";
    subEl.textContent = "Not synced";
  }
}

// Render the "HANDBOOK · AY25/26 · S1" header (hidden until the handbook is set up).
function renderHandbookHeader() {
  const el = document.getElementById("handbookHeader");
  const label = handbookHeaderLabel();   // "" until academicYear + semester are set
  if (label) {
    el.textContent = `HANDBOOK · ${label}`;
    el.style.display = "";
  } else {
    el.style.display = "none";
  }
}

function render() {
  renderHandbookHeader();
  renderModules();
  renderAccount();
}

window.addEventListener("modulo:datachanged", render);
window.addEventListener("modulo:authchanged", render); // token granted/refreshed/lapsed
render();
