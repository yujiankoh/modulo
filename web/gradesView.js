// gradesView.js — the Grades view (#view-grades, Phase 17). The ONE impure caller of
// the pure GPA logic (logic/gpa.js): reads appState, renders the stat cards + the
// per-module rows editor, and persists edits. Both GPAs are DERIVED on every redraw —
// nothing computed here is ever stored (derive-don't-store, same rule as the study
// totals). Redraws on modulo:datachanged, so handbook switches update it automatically.
//
// Row model (decided 2026-07-13): PHANTOM rows. The table always SHOWS every timetable
// module, but a row is only STORED (in appState.grades) once it has a grade. Clearing a
// grade back to "—" deletes the stored entry; the phantom stays while the module is in
// the timetable. Edits persist LIVE per change (like tasks) — no Save button.

import { appState, persist } from "./data.js";
import { schemeForLevel, computeGPA, cumulativeGPA } from "./logic/gpa.js";
import { moduleColor } from "./sidebar.js";

const supportedWrap = document.getElementById("gradesSupported");
const unsupportedWrap = document.getElementById("gradesUnsupported");
const reasonEl = document.getElementById("gradesUnsupportedReason");
const scaleEl = document.getElementById("gradesScale");
const semGpaEl = document.getElementById("cardSemGpa");
const cumGpaEl = document.getElementById("cardCumGpa");
const editorEl = document.getElementById("gradesEditor");
const addInput = document.getElementById("gradeAddInput");
const addBtn = document.getElementById("gradeAddBtn");

// Modules added via "+ Add module" that aren't stored yet — SESSION memory only (like
// cityView's pop-in set, never persisted). A label leaves the set once its row is
// stored; unsaved ones simply vanish on reload, which is fine — you add a module in
// order to grade it right away.
const extraModules = new Set();

// Display formatting lives HERE, never in the logic/storage: 2 dp, and null → an
// em-dash ("no GPA yet" is not the same thing as a 0.00 GPA).
function formatGPA(gpa) {
  return gpa === null ? "—" : gpa.toFixed(2);
}

// Credits pre-fill for a NEW row (decided 2026-07-13): most NUS modules are 4 MCs, so
// defaulting saves the typing; poly credit units genuinely vary (2–6), so a default
// would be silently wrong more often than right — leave it blank there.
function defaultCredits() {
  return appState.educationLevel === "university" ? 4 : "";
}

// Distinct module labels the timetable knows (code for tertiary, name for school —
// same `code || name` rule as the sidebar). Deliberately does NOT filter
// appState.hiddenModules: hiding a module declutters the dashboard, but it still
// needs a grade.
function timetableModuleLabels() {
  const set = new Set();
  for (const m of appState.timetable?.modules || []) {
    const label = m.code || m.name;
    if (label) set.add(label);
  }
  return [...set];
}

// Merge stored grade entries with phantom rows: stored rows first (seen wins), then a
// phantom for each ungraded timetable module, then session-added extras. Sorted by
// module label so a phantom becoming stored doesn't make rows jump around.
function buildRows() {
  const rows = [];
  const seen = new Set();
  for (const g of appState.grades) {
    rows.push({ id: g.id, module: g.module, credits: g.credits, grade: g.grade,
      su: g.su === true, stored: true });
    seen.add(g.module);
  }
  for (const label of [...timetableModuleLabels(), ...extraModules]) {
    if (seen.has(label)) continue;
    rows.push({ module: label, credits: defaultCredits(), grade: "", su: false, stored: false });
    seen.add(label);
  }
  rows.sort((a, b) => a.module.localeCompare(b.module));
  return rows;
}

// Read one row's inputs back into appState (the harvest). Storage rule:
//   grade chosen + row unstored  → CREATE an entry
//   grade chosen + row stored    → UPDATE it
//   grade cleared + row stored   → DELETE it (phantom reappears via the merge)
// Credits are sanitised to a positive number or 0 — NaN must never be stored because
// JSON.stringify(NaN) is null, which Android's non-nullable Double would choke on.
function harvestRow(rowEl, row) {
  const rawCredits = Number(rowEl.querySelector(".grade-credits").value);
  const credits = Number.isFinite(rawCredits) && rawCredits > 0 ? rawCredits : 0;
  const grade = rowEl.querySelector(".grade-grade").value;
  // The S/U checkbox only exists on suElection schemes; elsewhere su is simply false.
  const suBox = rowEl.querySelector(".grade-su");
  const su = suBox ? suBox.checked : false;

  if (grade === "") {
    if (!row.stored) return; // phantom with no grade: nothing to store yet (su rides along later)
    appState.grades = appState.grades.filter((g) => g.id !== row.id);
  } else if (row.stored) {
    const entry = appState.grades.find((g) => g.id === row.id);
    entry.credits = credits;
    entry.grade = grade;
    // Lean JSON: store the key only when true — absent means false (schema default),
    // so un-elected rows don't carry `su: false` noise into the sync file.
    if (su) entry.su = true;
    else delete entry.su;
  } else {
    const entry = { id: crypto.randomUUID(), module: row.module, credits, grade };
    if (su) entry.su = true;
    appState.grades.push(entry);
    extraModules.delete(row.module); // stored now — session memory no longer needed
  }
  persist(); // fires modulo:datachanged → cards + rows re-render from the new state
}

// The grade dropdown: "—" (no grade), then TWO <optgroup> sections (decided
// 2026-07-13) — an optgroup is a native, non-selectable section header inside a
// select, so letter grades and the not-counted grades (S/U…) read as separate groups
// in the popup. If a stored row somehow carries a grade the scheme doesn't know
// (another client wrote it), append it as an option — the select must SHOW the stored
// truth, not silently display "—" over it.
function buildGradeSelect(row, scheme) {
  const sel = document.createElement("select");
  sel.className = "grade-grade";
  sel.append(new Option("—", ""));

  const letters = document.createElement("optgroup");
  letters.label = "Letter grades";
  for (const g of Object.keys(scheme.points)) letters.append(new Option(g, g));

  const excluded = document.createElement("optgroup");
  excluded.label = "Not counted in GPA";
  // With the S/U election column (nus5), "S"/"U" leave the dropdown — the checkbox
  // owns the election now. CS/CU stay: pass/fail-ONLY modules have no letter to keep.
  // Old rows storing a literal "S"/"U" still display via the unknown-grade fallback.
  const dropdownExcluded = scheme.suElection
    ? scheme.excluded.filter((g) => g !== "S" && g !== "U")
    : scheme.excluded;
  for (const g of dropdownExcluded) excluded.append(new Option(g, g));

  sel.append(letters, excluded); // sel.options still lists every option across groups
  if (row.grade && ![...sel.options].some((o) => o.value === row.grade)) {
    sel.append(new Option(row.grade, row.grade));
  }
  sel.value = row.grade;
  return sel;
}

function buildRow(row, scheme) {
  const rowEl = document.createElement("div");
  rowEl.className = "grade-row";

  const dot = document.createElement("span");
  dot.className = "grade-dot";
  dot.style.background = moduleColor(row.module); // palette sample — inline is correct here

  const label = document.createElement("span");
  label.className = "grade-mod";
  label.textContent = row.module;

  const credits = document.createElement("input");
  credits.className = "grade-credits";
  credits.type = "number";
  credits.min = "0";
  credits.step = "0.5";
  credits.value = row.credits === 0 ? "" : row.credits; // 0 = "invalid" marker, show empty
  credits.setAttribute("aria-label", `${row.module} credits`);

  const grade = buildGradeSelect(row, scheme);
  grade.setAttribute("aria-label", `${row.module} grade`);

  // "change" (not "input") so credits persist on blur/Enter — persisting per keystroke
  // would re-render mid-typing and steal focus.
  credits.addEventListener("change", () => harvestRow(rowEl, row));
  grade.addEventListener("change", () => harvestRow(rowEl, row));

  const remove = document.createElement("button");
  remove.className = "grade-remove";
  remove.type = "button";
  remove.textContent = "×";
  remove.title = `Remove ${row.module}`;
  // × ONLY on a manually-added row that isn't stored yet (decided 2026-07-13): every
  // other row is removed by setting its grade back to "—" (which deletes the stored
  // entry; timetable phantoms re-derive anyway), so a button would be redundant. An
  // ungraded manual add is the ONE case with no other undo. Hidden — not absent —
  // elsewhere, so the grid columns stay aligned.
  if (!row.stored && extraModules.has(row.module)) {
    remove.addEventListener("click", () => {
      extraModules.delete(row.module);
      render();
    });
  } else {
    remove.style.visibility = "hidden";
  }

  const cells = [dot, label, credits, grade];
  // S/U election column (nus5 only): tick = keep the letter, exclude from the GPA.
  if (scheme.suElection) {
    const su = document.createElement("input");
    su.type = "checkbox";
    su.className = "grade-su";
    su.checked = row.su;
    su.title = `S/U ${row.module}`;
    su.setAttribute("aria-label", `S/U ${row.module}`);
    su.addEventListener("change", () => harvestRow(rowEl, row));
    cells.push(su);
  }
  cells.push(remove);
  rowEl.append(...cells);
  return rowEl;
}

function renderEditor(scheme, skippedCount) {
  editorEl.innerHTML = ""; // wipe, then rebuild from state (house pattern)
  // Rows have an extra column on suElection schemes — the class switches the grid.
  editorEl.classList.toggle("grades-editor--su", scheme.suElection);

  const rows = buildRows();
  if (rows.length === 0) {
    const empty = document.createElement("p");
    empty.className = "grades-empty";
    empty.textContent = "No modules yet — parse your timetable, or add a module below.";
    editorEl.append(empty);
    return;
  }

  // Column headers (styled like the card labels). Each cell aligns like its column's
  // CONTENT: an empty first cell keeps "Module" over the labels (not the dots);
  // Credits/Grade are inset to match the inputs' text; S/U centres over the checkbox.
  const head = document.createElement("div");
  head.className = "grade-row grade-row--head";
  const headings = [
    ["", ""],
    ["Module", ""],
    ["Credits", "grade-th--inset"],
    ["Grade", "grade-th--inset"],
    ...(scheme.suElection ? [["S/U", "grade-th--center"]] : []),
    ["", ""],
  ];
  for (const [text, cls] of headings) {
    const cell = document.createElement("span");
    if (cls) cell.className = cls;
    cell.textContent = text;
    head.append(cell);
  }
  editorEl.append(head);

  for (const row of rows) editorEl.append(buildRow(row, scheme));

  // Safety hint (narrowed 2026-07-13 after YJ's feedback): phantoms are obvious from
  // their "—" grade, so they get NO hint. It only appears for stored rows computeGPA
  // SKIPPED — a row with a grade but unusable credits (e.g. poly's blank default)
  // looks finished while silently not counting, and nothing else would say so.
  if (skippedCount > 0) {
    const hint = document.createElement("p");
    hint.className = "grades-hint";
    hint.textContent = skippedCount === 1
      ? "1 graded module isn't counted — check its credits."
      : `${skippedCount} graded modules aren't counted — check their credits.`;
    editorEl.append(hint);
  }
}

function render() {
  const { supported, scheme, reason } = schemeForLevel(appState.educationLevel);

  // Unsupported level (jc/secondary/primary, or no handbook yet): show the reason
  // panel instead of the calculator — the logic's message, verbatim.
  supportedWrap.style.display = supported ? "" : "none";
  unsupportedWrap.style.display = supported ? "none" : "";
  if (!supported) {
    reasonEl.textContent = reason;
    scaleEl.textContent = "";
    return;
  }

  // Say WHICH scale (SMU-limitation decision): "5.0 scale" / "4.0 scale".
  scaleEl.textContent = `${scheme.maxPoints.toFixed(1)} scale`;
  const semester = computeGPA(appState.grades, scheme); // once — card AND hint use it
  semGpaEl.textContent = formatGPA(semester.gpa);
  cumGpaEl.textContent = formatGPA(cumulativeGPA(appState).gpa);
  renderEditor(scheme, semester.skippedCount);
}

// "+ Add module": a module not in the timetable (dropped module, school subject, …).
// It appears as a phantom row; it's only STORED once a grade is picked.
function addModule() {
  const label = addInput.value.trim();
  if (!label) return;
  addInput.value = "";
  extraModules.add(label); // Set: adding a duplicate is a no-op, the merge dedupes too
  render();
}
addBtn.addEventListener("click", addModule);
addInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter") addModule();
});

window.addEventListener("modulo:datachanged", render);
render();
