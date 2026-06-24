// task.js — task logic + the task-list UI. Owns add/toggle/delete, renders #taskList,
// and wires the "Add Task" button. Reads shared state from data.js; redraws itself on
// the modulo:datachanged event (so it stays in sync without data.js knowing about it).

import { appState, persist, getStorageMode } from "./data.js";

// Each action changes memory then saves. persist() fires modulo:datachanged, which
// re-runs renderTasks below — so the actions don't need to redraw themselves.
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
}

// Toggle a task's completion status.
async function toggleTask(id) {
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

// Draw the current tasks into #taskList.
function renderTasks() {
  const list = document.getElementById("taskList");
  list.innerHTML = ""; // wipe the list, then rebuild it from appState

  if (!appState.tasks || appState.tasks.length === 0) {
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

// Wire the "Add Task" button (this module owns it).
document.getElementById("addBtn").addEventListener("click", () => {
  if (!getStorageMode()) { alert("Choose Google Drive or local mode first."); return; }
  const title = document.getElementById("taskTitle").value.trim();
  const due = document.getElementById("taskDue").value;
  const type = document.getElementById("taskType").value;
  if (!title) { alert("Enter a task title."); return; }
  addTask(title, due, type);
  document.getElementById("taskTitle").value = ""; // clear the input
});

// Redraw whenever state is saved/loaded, then draw once now.
window.addEventListener("modulo:datachanged", renderTasks);
renderTasks();
