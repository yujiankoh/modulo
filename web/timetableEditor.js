// timetableEditor.js — manual timetable editor (Phase 5).
// Opens to CORRECT a parsed timetable, or to ENTER one from scratch when parsing
// fails / there's no photo. Step 2: render editable module cards + slot rows,
// level-aware (fields/options depend on the chosen education level).
// Saving (harvesting these inputs back into appState) comes in Step 3.

import { appState, persist } from "./data.js";

const DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const WEEKS = ["all", "odd", "even"];   // alternating-week support
// Full set — used as a fallback when no level is chosen yet.
const SESSION_TYPES = [
  "lecture", "tutorial", "lab", "recitation",
  "seminar", "practical", "lesson", "cca",
];

// Per-level UI rules, straight from the schema: which fields/options apply.
// showCode: only tertiary (poly/uni) carry a module code; school levels don't.
function levelConfig(level) {
  switch (level) {
    case "primary":
    case "secondary":
      return { showCode: false, sessionTypes: ["lesson", "cca"] };
    case "jc":
      return { showCode: false, sessionTypes: ["lecture", "tutorial", "lesson"] };
    case "poly":
      return { showCode: true, sessionTypes: ["lecture", "tutorial", "lab", "practical"] };
    case "university":
      return { showCode: true, sessionTypes: ["lecture", "tutorial", "lab", "recitation", "seminar"] };
    default:
      return { showCode: true, sessionTypes: SESSION_TYPES }; // unknown level: show everything
  }
}

const editorEl = document.getElementById("timetableEditor");
const moduleListEl = document.getElementById("moduleList");
const editorStatusEl = document.getElementById("editorStatus");

// --- small DOM helpers (build one form control) ---

// A text/time <input>. Time inputs step in 5-minute increments (300 seconds).
function makeInput(type, className, value = "", placeholder = "") {
  const input = document.createElement("input");
  input.type = type;
  input.className = className;
  input.value = value;
  if (placeholder) input.placeholder = placeholder;
  if (type === "time") input.step = "300";
  return input;
}

// A <select> with one <option> per allowed value; pre-selects `selected` if given.
function makeSelect(className, options, selected = "") {
  const sel = document.createElement("select");
  sel.className = className;
  for (const opt of options) {
    const o = document.createElement("option");
    o.value = opt;
    o.textContent = opt;
    if (opt === selected) o.selected = true;
    sel.append(o);
  }
  return sel;
}

// --- build one editable slot row, appended into a module's slot list ---
function addSlot(slotListEl, slot = {}) {
  const { sessionTypes } = levelConfig(appState.educationLevel);

  const row = document.createElement("div");
  row.className = "slot-row";

  const day = makeSelect("slot-day", DAYS, slot.day || "MON");
  const start = makeInput("time", "slot-start", slot.start || "");
  const end = makeInput("time", "slot-end", slot.end || "");
  const location = makeInput("text", "slot-location", slot.location || "", "location");
  const sessionType = makeSelect("slot-sessionType", sessionTypes, slot.sessionType || sessionTypes[0]);
  const classNo = makeInput("text", "slot-classNo", slot.classNo || "", "class no.");
  const week = makeSelect("slot-week", WEEKS, slot.week || "all");

  const delBtn = document.createElement("button");
  delBtn.type = "button";                       // not a form submit button
  delBtn.textContent = "Delete slot";
  delBtn.addEventListener("click", () => row.remove());

  row.append(day, start, end, location, sessionType, classNo, week, delBtn);
  slotListEl.append(row);
}

// --- build one editable module card, appended into #moduleList ---
function addModule(module = {}) {
  const { showCode } = levelConfig(appState.educationLevel);

  const card = document.createElement("div");
  card.className = "module-card";

  const name = makeInput("text", "mod-name", module.name || "", "subject / module name");

  const delModBtn = document.createElement("button");
  delModBtn.type = "button";
  delModBtn.textContent = "Delete module";
  delModBtn.addEventListener("click", () => card.remove());

  const slotList = document.createElement("div");
  slotList.className = "slot-list";

  const addSlotBtn = document.createElement("button");
  addSlotBtn.type = "button";
  addSlotBtn.textContent = "+ Add slot";
  addSlotBtn.addEventListener("click", () => addSlot(slotList));

  // Only tertiary levels (poly/uni) carry a module code; school levels don't.
  if (showCode) {
    const code = makeInput("text", "mod-code", module.code || "", "code (e.g. CS2030S)");
    card.append(code);
  }
  card.append(name, delModBtn, slotList, addSlotBtn);
  moduleListEl.append(card);

  // Pre-fill existing slots (correction flow), else start with one empty slot.
  if (module.slots && module.slots.length) {
    module.slots.forEach((s) => addSlot(slotList, s));
  } else {
    addSlot(slotList);
  }
}

// Read the current cards/rows out of the DOM into a schema-v2 timetable object.
function harvestTimetable() {
  const modules = [];
  for (const card of moduleListEl.querySelectorAll(".module-card")) {
    const codeEl = card.querySelector(".mod-code");          // absent for school levels
    const code = codeEl ? codeEl.value.trim() : "";
    const name = card.querySelector(".mod-name").value.trim();

    const slots = [];
    for (const row of card.querySelectorAll(".slot-row")) {
      slots.push({
        day: row.querySelector(".slot-day").value,
        start: row.querySelector(".slot-start").value,
        end: row.querySelector(".slot-end").value,
        location: row.querySelector(".slot-location").value.trim(),
        sessionType: row.querySelector(".slot-sessionType").value,
        classNo: row.querySelector(".slot-classNo").value.trim(),
        week: row.querySelector(".slot-week").value,
      });
    }
    modules.push({ code, name, slots });
  }
  // mirror the chosen level into the timetable, per the schema contract.
  return { educationLevel: appState.educationLevel || "", modules };
}

// Return the first problem with the timetable as a friendly string, or null if OK.
// Times are "HH:MM" (zero-padded 24h), so plain string comparison orders them correctly.
function firstValidationError(tt) {
  if (tt.modules.length === 0) return "Add at least one module before saving.";
  for (let i = 0; i < tt.modules.length; i++) {
    const m = tt.modules[i];
    const label = `Module ${i + 1}`;
    if (!m.code && !m.name) return `${label}: enter a code or name.`;
    if (m.slots.length === 0) return `${label}: add at least one slot.`;
    for (let j = 0; j < m.slots.length; j++) {
      const s = m.slots[j];
      const where = `${label}, slot ${j + 1}`;
      if (!s.start || !s.end) return `${where}: set a start and end time.`;
      if (s.start >= s.end) return `${where}: end time must be after the start time.`;
    }
  }
  return null;
}

// Validate -> appState.timetable -> persist (Drive or local), with a confirmation.
async function saveTimetable() {
  const timetable = harvestTimetable();
  const error = firstValidationError(timetable);
  if (error) {
    editorStatusEl.textContent = error;
    return;
  }
  appState.timetable = timetable;
  await persist();
  editorStatusEl.textContent = `Saved ✓ (${timetable.modules.length} module(s))`;
}

// (Re)build the editor's contents from the saved timetable. Pre-fills for the
// correction flow; starts with one blank module when there's nothing yet.
function renderEditorFromState() {
  moduleListEl.innerHTML = "";          // clear any previous contents
  editorStatusEl.textContent = "";
  const modules = appState.timetable?.modules || [];
  if (modules.length) {
    modules.forEach((m) => addModule(m));
  } else {
    addModule();                        // one empty module to start
  }
}

function openEditor() {
  renderEditorFromState();              // load current saved state on every open
  editorEl.style.display = "block";
}

function closeEditor() {
  editorEl.style.display = "none";      // hide; unsaved edits are discarded
}

document.getElementById("manualEntryBtn").addEventListener("click", openEditor);
document.getElementById("closeEditorBtn").addEventListener("click", closeEditor);
document.getElementById("addModuleBtn").addEventListener("click", () => addModule());
document.getElementById("saveTimetableBtn").addEventListener("click", saveTimetable);
