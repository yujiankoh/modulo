// dashboard.js — the Dashboard landing view (Phase 12.3). Aggregates existing data into
// an at-a-glance home. 12.3a: a date/term-week eyebrow, a time-of-day greeting, and a
// one-line summary. (12.3b adds study cards; 12.3c the schedule + tasks-due-soon panels.)
//
// It only READS appState (plus writes the name field) and reuses the timetable's
// term-week math, so there's no second source of truth. Redraws on modulo:datachanged.

import { appState, persist } from "./data.js";
import { currentWeekInfo, totalTeachingWeeks } from "./timetableView.js";
import { moduleColor } from "./sidebar.js"; // shared module-colour palette

// --- shared helpers (also used by the schedule + tasks panels in 12.3c) ---

// Timetable days are "MON".."SUN"; JS getDay() is 0=Sun..6=Sat. Map today to that code.
const DAY_CODES = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];
function todayCode() { return DAY_CODES[new Date().getDay()]; }

// "HH:MM" -> minutes since midnight, for sorting by start time.
function toMinutes(t) {
  if (!t || !t.includes(":")) return 0;
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
}

// Today's classes: this weekday's slots, filtered by the current week's odd/even parity,
// sorted by start time. Empty when there's no timetable, or it's a break/outside week.
// Each item carries its module label so the schedule card (12.3c) can render directly.
export function classesToday() {
  const tt = appState.timetable;
  if (!tt) return [];
  const wk = currentWeekInfo();
  if (wk.status === "break" || wk.status === "outside") return []; // no classes this week
  const day = todayCode();
  const list = [];
  for (const m of tt.modules || []) {
    for (const s of m.slots || []) {
      if (s.day !== day) continue;
      const slotWeek = s.week || "all";
      // If we know the parity, drop slots that only run on the OTHER parity.
      if (wk.parity && slotWeek !== "all" && slotWeek !== wk.parity) continue;
      list.push({ module: m.code || m.name || "", ...s });
    }
  }
  return list.sort((a, b) => toMinutes(a.start) - toMinutes(b.start));
}

// Whole days from local-midnight today to a "YYYY-MM-DD" date. 0=today, +1=tomorrow.
function daysUntil(due) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const d = new Date(due + "T00:00:00"); // parse at local midnight (no timezone drift)
  return Math.round((d - today) / 86400000); // ms per day
}

// Not-done tasks due within the next 7 days, soonest first.
export function tasksDueSoon() {
  return (appState.tasks || [])
    .filter((t) => !t.done && t.due)
    .filter((t) => { const d = daysUntil(t.due); return d >= 0 && d <= 7; })
    .sort((a, b) => a.due.localeCompare(b.due)); // ISO date strings sort chronologically
}

// --- study stats: this week's hours + the current streak (from studySessions) ---

// Local midnight today, and the Monday of this week (Monday-first, like the rest of the app).
function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}
function startOfWeek() {
  const d = startOfToday();
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); // step back to this week's Monday
  return d;
}

// Minutes studied since a cutoff Date (sums durationMins of sessions starting on/after it).
function studyMinsSince(cutoff) {
  return (appState.studySessions || [])
    .filter((s) => new Date(s.start) >= cutoff)
    .reduce((sum, s) => sum + (s.durationMins || 0), 0);
}

// "14.5h" / "3h" / "0h" — hours studied this week, to one decimal (trailing .0 dropped).
function weekHoursText() {
  const hours = studyMinsSince(startOfWeek()) / 60;
  return `${Math.round(hours * 10) / 10}h`;
}

// A local-date key ("year-month-day") for grouping sessions by calendar day.
function dayKey(d) {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

// Current streak = consecutive days, ending today (or yesterday if today is still empty),
// that each have >= 1 study session. So studying daily grows it; a gap resets it; and not
// having studied *yet* today doesn't kill a streak that stands on yesterday.
function studyStreak() {
  const days = new Set((appState.studySessions || []).map((s) => dayKey(new Date(s.start))));
  const cursor = startOfToday();
  if (!days.has(dayKey(cursor))) cursor.setDate(cursor.getDate() - 1); // lean on yesterday
  let streak = 0;
  while (days.has(dayKey(cursor))) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

function streakText() {
  const n = studyStreak();
  return `${n} ${n === 1 ? "day" : "days"}`;
}

// --- header: eyebrow + greeting + summary ---

const MONTHS = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"];
const WEEKDAYS = ["SUN","MON","TUE","WED","THU","FRI","SAT"];

// "WED 25 JUN · WEEK 6 OF 13" (teaching week), "… · RECESS WEEK", or just the date when
// there's no term anchor / we're outside term.
function eyebrowText() {
  const now = new Date();
  const date = `${WEEKDAYS[now.getDay()]} ${now.getDate()} ${MONTHS[now.getMonth()]}`;
  const wk = currentWeekInfo();
  if (wk.status === "normal") {
    const total = totalTeachingWeeks();
    return `${date} · ${total ? `WEEK ${wk.number} OF ${total}` : `WEEK ${wk.number}`}`;
  }
  if (wk.status === "break") return `${date} · RECESS WEEK`;
  return date;
}

// Greeting by time of day. (A name can be added later — handbook/profile, Phase 13.)
function greetingText() {
  const h = new Date().getHours();
  const part = h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
  return `${part}.`;
}

// "You have 4 classes today and 5 tasks due this week." (singular/plural aware.)
function summaryText() {
  const c = classesToday().length;
  const t = tasksDueSoon().length;
  const cls = `${c} ${c === 1 ? "class" : "classes"}`;
  const tsk = `${t} ${t === 1 ? "task" : "tasks"}`;
  return `You have ${cls} today and ${tsk} due this week.`;
}

// --- panels: today's schedule + tasks due soon ---

// "lecture" -> "Lecture" (just the first letter, for display).
function capitalize(s) {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : "";
}

// Friendly relative due text: "Today" / "Tomorrow" / "Fri 27 Jun".
function dueLabel(due) {
  const d = daysUntil(due);
  if (d === 0) return "Today";
  if (d === 1) return "Tomorrow";
  return new Date(due + "T00:00:00").toLocaleDateString("en-GB", {
    weekday: "short", day: "2-digit", month: "short",
  });
}

// Today's schedule: one row per class — time on the left, module/type + location on the right.
function renderSchedule() {
  const list = document.getElementById("dashSchedule");
  list.innerHTML = "";
  const classes = classesToday();
  if (classes.length === 0) {
    list.innerHTML = "<li class='dash-empty'>No classes today.</li>";
    return;
  }
  for (const c of classes) {
    const li = document.createElement("li");
    li.className = "dash-class";

    const time = document.createElement("span");
    time.className = "dash-class-time";
    time.textContent = `${c.start}–${c.end}`;

    const main = document.createElement("div");
    const title = document.createElement("div");
    title.textContent = `${c.module} · ${capitalize(c.sessionType)}`;
    const loc = document.createElement("div");
    loc.className = "dash-class-loc";
    loc.textContent = c.location || "";
    main.append(title, loc);

    li.append(time, main);
    list.append(li);
  }
}

// Tasks due soon: the soonest few not-done tasks, each with a relative-date pill.
function renderTasksDue() {
  const list = document.getElementById("dashTasks");
  list.innerHTML = "";
  const tasks = tasksDueSoon().slice(0, 5); // keep the panel short
  if (tasks.length === 0) {
    list.innerHTML = "<li class='dash-empty'>Nothing due this week.</li>";
    return;
  }
  for (const t of tasks) {
    const li = document.createElement("li");
    li.className = "dash-task";

    const main = document.createElement("div");
    const title = document.createElement("div");
    title.textContent = t.title;
    const meta = document.createElement("div");
    meta.className = "dash-task-meta";
    meta.textContent = t.module ? `${t.module} · ${t.type}` : t.type;
    main.append(title, meta);

    const pill = document.createElement("span");
    // Soonest tasks (due today/tomorrow) get the blue accent pill; others stay neutral.
    pill.className = "dash-task-pill" + (daysUntil(t.due) <= 1 ? " dash-task-pill--soon" : "");
    pill.textContent = dueLabel(t.due);

    li.append(main, pill);
    list.append(li);
  }
}

// --- modules section + detail modal ---

// One entry per distinct module: { label, name, tasks }. Name comes from the timetable;
// tasks are matched by task.module. Sorted by label.
function moduleInfos() {
  const map = new Map();
  for (const m of appState.timetable?.modules || []) {
    const label = m.code || m.name;
    if (!label) continue;
    if (!map.has(label)) map.set(label, { label, name: m.name || "", tasks: [] });
    else if (!map.get(label).name && m.name) map.get(label).name = m.name;
  }
  for (const t of appState.tasks || []) {
    if (!t.module) continue;
    if (!map.has(t.module)) map.set(t.module, { label: t.module, name: "", tasks: [] });
    map.get(t.module).tasks.push(t);
  }
  return [...map.values()].sort((a, b) => a.label.localeCompare(b.label));
}

const moduleModal = document.getElementById("moduleModal");

// Open the module detail modal: notes placeholder (filled in HTML) + this module's tasks.
function openModuleModal(m) {
  document.getElementById("moduleModalTitle").textContent =
    m.name ? `${m.label} · ${m.name}` : m.label;

  const list = document.getElementById("moduleModalTasks");
  list.innerHTML = "";
  const tasks = m.tasks.slice().sort((a, b) => (a.due || "").localeCompare(b.due || ""));
  if (tasks.length === 0) {
    list.innerHTML = "<li class='dash-empty'>No tasks for this module.</li>";
  } else {
    for (const t of tasks) {
      const li = document.createElement("li");
      li.className = "dash-task";
      const main = document.createElement("div");
      const title = document.createElement("div");
      title.textContent = t.title;
      if (t.done) title.style.textDecoration = "line-through";
      const meta = document.createElement("div");
      meta.className = "dash-task-meta";
      meta.textContent = t.type;
      main.append(title, meta);
      const pill = document.createElement("span");
      pill.className = "dash-task-pill";
      pill.textContent = t.done ? "Done" : t.due ? dueLabel(t.due) : "No date";
      li.append(main, pill);
      list.append(li);
    }
  }
  moduleModal.style.display = "flex";
}
function closeModuleModal() { moduleModal.style.display = "none"; }

// Render the Modules cards (coloured header + active-task count; click → detail modal).
// Hidden modules (appState.hiddenModules) are filtered out here and in the sidebar.
function renderModules() {
  const grid = document.getElementById("dashModules");
  grid.innerHTML = "";
  const hidden = new Set(appState.hiddenModules || []);
  const all = moduleInfos();
  const mods = all.filter((m) => !hidden.has(m.label));
  const hiddenCount = all.length - mods.length;

  document.getElementById("dashModulesCount").textContent =
    `${mods.length} ${mods.length === 1 ? "module" : "modules"} this semester` +
    (hiddenCount ? ` · ${hiddenCount} hidden` : "");

  if (mods.length === 0) {
    grid.innerHTML = all.length > 0
      ? "<p class='dash-empty'>All modules hidden — use Manage to show some.</p>"
      : "<p class='dash-empty'>No modules yet — parse a timetable or add tasks with a module.</p>";
    return;
  }
  for (const m of mods) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "module-card";

    const head = document.createElement("div");
    head.className = "module-card-head";
    head.style.background = moduleColor(m.label);
    const code = document.createElement("div");
    code.className = "module-card-code";
    code.textContent = m.label;
    const name = document.createElement("div");
    name.className = "module-card-name";
    name.textContent = m.name || " "; // non-breaking space keeps the header height
    head.append(code, name);

    const foot = document.createElement("div");
    foot.className = "module-card-foot";
    const active = m.tasks.filter((t) => !t.done).length;
    const left = document.createElement("span");
    left.textContent = `${active} active`;
    const right = document.createElement("span");
    right.textContent = "open →";
    foot.append(left, right);

    card.append(head, foot);
    card.addEventListener("click", () => openModuleModal(m));
    grid.append(card);
  }
}

// Close the module modal via ✕, backdrop click, or Esc.
document.getElementById("moduleModalClose").addEventListener("click", closeModuleModal);
moduleModal.addEventListener("click", (e) => { if (e.target === moduleModal) closeModuleModal(); });
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && moduleModal.style.display !== "none") closeModuleModal();
});

// --- Manage modules (show/hide) ---
const manageModal = document.getElementById("manageModulesModal");

// Add/remove a module label from appState.hiddenModules, then save (re-renders both views).
async function setModuleHidden(label, hide) {
  const set = new Set(appState.hiddenModules || []);
  if (hide) set.add(label);
  else set.delete(label);
  appState.hiddenModules = [...set];
  await persist();
}

// Build the checkbox list: one row per module, ticked = shown.
function renderManageList() {
  const list = document.getElementById("manageModulesList");
  list.innerHTML = "";
  const mods = moduleInfos();
  if (mods.length === 0) {
    list.innerHTML = "<li class='dash-empty'>No modules yet.</li>";
    return;
  }
  const hidden = new Set(appState.hiddenModules || []);
  for (const m of mods) {
    const li = document.createElement("li");
    li.className = "manage-row";
    const label = document.createElement("label");
    label.className = "manage-label";

    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = !hidden.has(m.label);                 // ticked = shown
    cb.addEventListener("change", () => setModuleHidden(m.label, !cb.checked));

    const dot = document.createElement("span");
    dot.className = "module-dot";
    dot.style.background = moduleColor(m.label);

    const name = document.createElement("span");
    name.textContent = m.name ? `${m.label} · ${m.name}` : m.label;

    label.append(cb, dot, name);
    li.append(label);
    list.append(li);
  }
}

function openManageModal() { renderManageList(); manageModal.style.display = "flex"; }
function closeManageModal() { manageModal.style.display = "none"; }

document.getElementById("manageModulesBtn").addEventListener("click", openManageModal);
document.getElementById("manageModulesClose").addEventListener("click", closeManageModal);
manageModal.addEventListener("click", (e) => { if (e.target === manageModal) closeManageModal(); });
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && manageModal.style.display !== "none") closeManageModal();
});

function render() {
  document.getElementById("dashEyebrow").textContent = eyebrowText();
  document.getElementById("dashGreeting").textContent = greetingText();
  document.getElementById("dashSummary").textContent = summaryText();
  document.getElementById("cardStreak").textContent = streakText();
  document.getElementById("cardWeekHours").textContent = weekHoursText();
  renderSchedule();
  renderTasksDue();
  renderModules();
}

// Redraw whenever state changes, and once now.
window.addEventListener("modulo:datachanged", render);
render();
