// task.js — task logic + the task-list UI. Owns add/toggle/delete, renders #taskList,
// and wires the "Add Task" button. Reads shared state from data.js; redraws itself on
// the modulo:datachanged event (so it stays in sync without data.js knowing about it).

import { appState, persist, getStorageMode } from "./data.js";
import { moduleColor } from "./sidebar.js"; // shared module-colour palette (dots)

// Each action changes memory then saves. persist() fires modulo:datachanged, which
// re-runs renderTasks below — so the actions don't need to redraw themselves.
async function addTask(title, module, due, type) {
  const now = new Date().toISOString();
  appState.tasks.push({
    id: Date.now(),
    title,
    module,           // e.g. "CS2030S" (optional — may be "")
    due,
    type,
    done: false,
    createdAt: now,
    updatedAt: now,   // updated whenever the task changes
  });
  await persist();
}

// Toggle a task's completion status. Exported so the dashboard's due-soon checkboxes
// can complete a task too (it persists → both views redraw).
export async function toggleTask(id) {
  const task = appState.tasks.find((t) => t.id === id);
  if (task) {
    task.done = !task.done;
    task.updatedAt = new Date().toISOString();   // stamp the change
  }
  await persist();
}

async function deleteTask(id) {
  appState.tasks = appState.tasks.filter((t) => t.id !== id); // keep all EXCEPT this id
  await persist();
}

// Current sort mode, chosen by the #taskSort dropdown. Default: earliest due date first.
let sortBy = "due";

// A comparator is a function (a, b) => number that .sort() calls to order two items:
//   negative → a comes first, positive → b comes first, 0 → keep their order.
const comparators = {
  // Earliest due date first. Tasks with no date sink to the bottom (return +1/-1).
  due: (a, b) => {
    if (!a.due) return 1;
    if (!b.due) return -1;
    return a.due.localeCompare(b.due);   // ISO "YYYY-MM-DD" strings compare correctly
  },
  // Group by type, alphabetically.
  type: (a, b) => a.type.localeCompare(b.type),
  // Most recently created first (createdAt is an ISO timestamp; b-vs-a = descending).
  newest: (a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""),
};

// getVisibleTasks() returns the tasks to DISPLAY — derived from appState.tasks without
// mutating it. It reads the three filter dropdowns + the sort, applies them to the tasks,
// and returns the result. Each .filter() returns a NEW array, so the original is never
// touched — that's why we no longer need an explicit .slice() before .sort().
function getVisibleTasks() {
  const status = document.getElementById("taskFilterStatus").value;  // all | active | done
  const type   = document.getElementById("taskFilterType").value;    // all | <type>
  const module = document.getElementById("taskFilterModule").value;  // all | <module>

  return (appState.tasks || [])
    .filter((t) => status === "all" || (status === "done" ? t.done : !t.done))
    .filter((t) => type === "all" || t.type === type)
    .filter((t) => module === "all" || t.module === module)
    .sort(comparators[sortBy]);
}

// Fill one filter <select> with an "all" option + one option per distinct value, keeping
// the user's current pick if it still exists (otherwise fall back to "all").
function populateFilter(select, values, allLabel) {
  const current = select.value || "all";   // remember what was chosen
  select.innerHTML = "";                    // clear out the old options
  const allOpt = document.createElement("option");
  allOpt.value = "all";
  allOpt.textContent = allLabel;
  select.append(allOpt);
  for (const v of values) {
    const opt = document.createElement("option");
    opt.value = v;
    opt.textContent = v;
    select.append(opt);
  }
  select.value = values.includes(current) ? current : "all";  // restore, or reset to "all"
}

// Rebuild the Type + Module dropdowns from whatever tasks currently exist. new Set(...)
// removes duplicates; [...set] turns it back into an array; .filter(Boolean) drops blanks.
function refreshFilterOptions() {
  const tasks = appState.tasks || [];
  const types = [...new Set(tasks.map((t) => t.type).filter(Boolean))].sort();
  const modules = [...new Set(tasks.map((t) => t.module).filter(Boolean))].sort();
  populateFilter(document.getElementById("taskFilterType"), types, "All types");
  populateFilter(document.getElementById("taskFilterModule"), modules, "All modules");
}

// --- Dates: which bucket a task falls in + the relative "due" pill -------------

// Today at local midnight — the fixed reference point for whole-day comparisons.
function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

// Whole days from today to a "YYYY-MM-DD" due date: 0 = today, +1 = tomorrow,
// negative = overdue. Parsing at local midnight (T00:00:00) avoids time-of-day drift.
function daysUntil(due) {
  const d = new Date(due + "T00:00:00");
  return Math.round((d - startOfToday()) / 86400000); // 86,400,000 ms per day
}

// The buckets, in display order. Each task lands in exactly one (see bucketOf).
const BUCKETS = [
  { key: "overdue",   label: "OVERDUE" },
  { key: "thisWeek",  label: "DUE THIS WEEK" },
  { key: "later",     label: "LATER" },
  { key: "completed", label: "COMPLETED" },
];

// Decide a task's bucket. Done wins first; then the due date decides the rest.
function bucketOf(task) {
  if (task.done) return "completed";
  if (!task.due) return "later";          // no date → Later
  const d = daysUntil(task.due);
  if (d < 0) return "overdue";
  if (d <= 7) return "thisWeek";
  return "later";
}

// Text for the right-hand pill: friendly relative words near today, else a date.
function dueLabel(task) {
  if (task.done) return "Done";
  if (!task.due) return "No date";
  const d = daysUntil(task.due);
  if (d === 0) return "Today";
  if (d === 1) return "Tomorrow";
  if (d === -1) return "Yesterday";
  return new Date(task.due + "T00:00:00").toLocaleDateString("en-GB", {
    weekday: "short", day: "2-digit", month: "short",  // e.g. "Fri 30 May"
  });
}

// Build one task's <li> row: checkbox + title/meta block + due pill + Delete (mockup layout).
function renderTaskRow(task) {
  const li = document.createElement("li");
  li.className = "task-row";
  // Module colour as a left accent bar (polish 2026-07-15) — the same visual
  // language as the timetable's session blocks. CSS default is transparent, so
  // module-less rows keep their alignment without a stray grey bar.
  if (task.module) li.style.borderLeftColor = moduleColor(task.module);

  // Done is now a checkbox (replaces the old Done/Undo button). Ticking it toggles done.
  const checkbox = document.createElement("input");
  checkbox.type = "checkbox";
  checkbox.className = "task-check";
  checkbox.checked = task.done;
  checkbox.addEventListener("change", () => toggleTask(task.id));

  // Middle block: title on top, "module · type" beneath it.
  const main = document.createElement("div");
  main.className = "task-main";
  const title = document.createElement("div");
  title.className = "task-title";
  title.textContent = task.title;
  if (task.done) title.style.textDecoration = "line-through";
  const meta = document.createElement("div");
  meta.className = "task-meta";
  if (task.module) {
    // The module NAME carries its colour (replaced the dot, 2026-07-15 — with the
    // accent bar a dot was double bookkeeping). The type stays muted text.
    const mod = document.createElement("span");
    mod.className = "task-meta-mod";
    mod.style.color = moduleColor(task.module);
    mod.textContent = task.module;
    meta.append(mod, document.createTextNode(` · ${task.type}`));
  } else {
    meta.append(document.createTextNode(task.type));
  }
  main.append(title, meta);

  // Right side: the relative-date pill + a Delete button.
  const d = task.due ? daysUntil(task.due) : null;
  const pill = document.createElement("span");
  // Blue accent pill for not-done tasks due today/tomorrow; everything else stays neutral.
  pill.className = "task-pill" + (!task.done && (d === 0 || d === 1) ? " task-pill--soon" : "");
  pill.textContent = dueLabel(task);

  const delBtn = document.createElement("button");
  delBtn.className = "task-del";
  delBtn.textContent = "Delete";
  delBtn.addEventListener("click", () => deleteTask(task.id));

  li.append(checkbox, main, pill, delBtn);
  return li;
}

// Draw the current tasks into #taskList, grouped into buckets with a header per group.
function renderTasks() {
  const list = document.getElementById("taskList");
  list.innerHTML = ""; // wipe the list, then rebuild it from the derived view

  // Header eyebrow count = number of not-done ("open") tasks.
  const open = (appState.tasks || []).filter((t) => !t.done).length;
  document.getElementById("tasksEyebrow").textContent = `ALL TASKS · ${open} OPEN`;

  refreshFilterOptions(); // keep the Type/Module dropdowns in sync with current tasks

  const visible = getVisibleTasks();
  if (visible.length === 0) {
    // Distinguish "you have no tasks" from "filters hid them all".
    const hasAnyTasks = (appState.tasks || []).length > 0;
    list.innerHTML = hasAnyTasks
      ? "<p class='dash-empty'>No tasks match your filters.</p>"
      : "<p class='dash-empty'>No tasks yet.</p>";
    return;
  }

  // Make one white card (a <ul>) holding the given rows, and append it to #taskList.
  function appendCard(tasks) {
    const card = document.createElement("ul");
    card.className = "task-card";
    for (const task of tasks) card.append(renderTaskRow(task));
    list.append(card);
  }
  // A section = a header label above its own card.
  function appendGroup(label, tasks) {
    const header = document.createElement("div");
    header.className = "task-group";
    header.textContent = `${label} · ${tasks.length}`;
    list.append(header);
    appendCard(tasks);
  }

  // Each group gets its own card with the header above it. Due → date buckets; Type →
  // one card per type; Newest → a single flat card (no meaningful grouping).
  if (sortBy === "due") {
    for (const bucket of BUCKETS) {
      const inBucket = visible.filter((t) => bucketOf(t) === bucket.key);
      if (inBucket.length > 0) appendGroup(bucket.label, inBucket);
    }
  } else if (sortBy === "type") {
    // visible is already sorted by type, so the Set keeps the types in that order.
    const types = [...new Set(visible.map((t) => t.type))];
    for (const type of types) {
      appendGroup(type.toUpperCase(), visible.filter((t) => t.type === type));
    }
  } else {
    appendCard(visible); // Newest → flat
  }
}

// --- Add Task modal: open / close / save -------------------------------------
const taskModal = document.getElementById("taskModal");
const OTHER = "__other__"; // sentinel value for the "+ Add other…" option

// Distinct module labels known to the app: from the parsed timetable (code or name) +
// any module already used on a task. Sorted, de-duplicated (Set drops repeats).
function knownModules() {
  const set = new Set();
  for (const m of appState.timetable?.modules || []) {
    const label = m.code || m.name;
    if (label) set.add(label);
  }
  for (const t of appState.tasks || []) {
    if (t.module) set.add(t.module);
  }
  return [...set].sort();
}

// (Re)build the Module dropdown: "— None —", each known module, then "+ Add other…".
// Resets the selection to None and hides the free-text "other" field.
function populateModuleSelect() {
  const sel = document.getElementById("taskModule");
  sel.innerHTML = "";
  sel.append(new Option("— None —", ""));                 // new Option(text, value)
  for (const m of knownModules()) sel.append(new Option(m, m));
  sel.append(new Option("+ Add other…", OTHER));
  sel.value = "";
  document.getElementById("taskModuleOtherField").style.display = "none";
  document.getElementById("taskModuleOther").value = "";
}

// Show the free-text field only when "+ Add other…" is picked, and focus it.
document.getElementById("taskModule").addEventListener("change", (e) => {
  const isOther = e.target.value === OTHER;
  document.getElementById("taskModuleOtherField").style.display = isOther ? "" : "none";
  if (isOther) document.getElementById("taskModuleOther").focus();
});

function openTaskModal() {
  if (!getStorageMode()) { alert("Choose Google Drive or local mode first."); return; }
  populateModuleSelect();                        // refresh the module list each open
  taskModal.style.display = "flex";              // CSS .tcal-popup centres the card
  document.getElementById("taskTitle").focus();  // cursor ready in the first field
}

function closeTaskModal() {
  taskModal.style.display = "none";
}

// Open from the "+ Add Task" button.
document.getElementById("openAddTask").addEventListener("click", openTaskModal);

// Close via ✕, Cancel, clicking the dim backdrop, or pressing Esc.
document.getElementById("taskModalClose").addEventListener("click", closeTaskModal);
document.getElementById("cancelAddTask").addEventListener("click", closeTaskModal);
taskModal.addEventListener("click", (e) => {
  if (e.target === taskModal) closeTaskModal();   // backdrop only, not the card
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && taskModal.style.display !== "none") closeTaskModal();
});

// Save: read the fields, add the task, reset the form, close the modal.
document.getElementById("addBtn").addEventListener("click", () => {
  const title = document.getElementById("taskTitle").value.trim();
  const moduleSel = document.getElementById("taskModule").value;
  // If "+ Add other…" is chosen, take the typed name; otherwise use the picked option.
  const module = moduleSel === OTHER
    ? document.getElementById("taskModuleOther").value.trim()
    : moduleSel;
  const due = document.getElementById("taskDue").value;
  const type = document.getElementById("taskType").value;
  if (!title) { alert("Enter a task title."); return; }
  addTask(title, module, due, type);

  document.getElementById("taskTitle").value = "";
  populateModuleSelect();   // resets the module dropdown to None + hides the other field
  document.getElementById("taskDue").value = "";
  document.getElementById("taskType").value = "assignment";
  closeTaskModal();
});

// When the user picks a different sort, remember it and redraw.
document.getElementById("taskSort").addEventListener("change", (e) => {
  sortBy = e.target.value;
  renderTasks();
});

// Redraw whenever any filter changes. The filters' values are read straight
// from the dropdowns inside getVisibleTasks(), so there's no state variable to update.
["taskFilterStatus", "taskFilterType", "taskFilterModule"].forEach((id) => {
  document.getElementById(id).addEventListener("change", renderTasks);
});

// Redraw whenever state is saved/loaded, then draw once now.
window.addEventListener("modulo:datachanged", renderTasks);
renderTasks();
