// Your app's PUBLIC client ID — the same one the Android app uses. Safe to be here.
const CLIENT_ID = "332114614658-87cqh1e2u8luh9b5q15sf22sb30i3nda.apps.googleusercontent.com";

// The permission we're requesting: access ONLY to our app's private Drive folder.
const SCOPES = "https://www.googleapis.com/auth/drive.appdata";

let accessToken = null;   // we'll store the token here once we get it
let tokenClient;          // Google's helper object, set up once the library loads

const statusEl = document.getElementById("status");
const connectBtn = document.getElementById("connectBtn");

// Runs after the page + Google's library have finished loading
window.onload = () => {
  tokenClient = google.accounts.oauth2.initTokenClient({
    client_id: CLIENT_ID,
    scope: SCOPES,
    callback: (response) => {
      // This fires once the user finishes the Google popup
      if (response.error) {
        statusEl.textContent = "Error: " + response.error;
        return;
      }
      accessToken = response.access_token;
      statusEl.textContent = "Connected! Token: " + accessToken.slice(0, 12) + "...";
      console.log("Full access token:", accessToken);
      loadInitialData();
    },
  });
};

// Clicking the button opens Google's account chooser + consent popup
connectBtn.addEventListener("click", () => {
  tokenClient.requestAccessToken();
});

// The single file we'll store MODULO's data in, inside the hidden app folder.
const FILE_NAME = "modulo-data.json";

// Every Drive request needs the token to prove the user authorized us.
function authHeaders(extra = {}) {
  return { Authorization: "Bearer " + accessToken, ...extra };
}

// Look inside the app folder and find our data file. Returns its id, or null if it doesn't exist yet.
async function findFileId() {
  const res = await fetch(
    "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name)",
    { headers: authHeaders() }
  );
  const data = await res.json();
  const file = data.files.find((f) => f.name === FILE_NAME);
  return file ? file.id : null;
}

// Save an object as JSON into the app folder. Creates the file if missing, otherwise overwrites it.
async function saveData(obj) {
  let fileId = await findFileId();

  // First time only: create the empty file with the right name + location.
  if (!fileId) {
    const createRes = await fetch("https://www.googleapis.com/drive/v3/files", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        name: FILE_NAME,
        parents: ["appDataFolder"], // <-- this is what puts it in the hidden app folder
      }),
    });
    const created = await createRes.json();
    fileId = created.id;
  }

  // Write the actual content into the file (this is also how updates work).
  await fetch(
    "https://www.googleapis.com/upload/drive/v3/files/" + fileId + "?uploadType=media",
    {
      method: "PATCH",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(obj),
    }
  );
}

// Read the JSON back out of the app folder.
async function loadData() {
  const fileId = await findFileId();
  if (!fileId) return null; // nothing saved yet
  const res = await fetch(
    "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media",
    { headers: authHeaders() }
  );
  return await res.json();
}

// ===== MODULO app state: the single source of truth, held in memory =====
let appState = {
  schemaVersion: 1,
  tasks: [],
  timetable: null,
  updatedAt: null,
};

// Save the WHOLE current state to Drive (stamping the time first).
async function persist() {
  appState.updatedAt = new Date().toISOString();
  await saveData(appState);
}

// Load the saved state from Drive into memory, then draw it.
async function loadInitialData() {
  const saved = await loadData();
  if (saved) {
    appState = saved;
    if (!appState.tasks) appState.tasks = []; // safety if the file is old/empty
  }
  render();
}

// --- actions: each one changes memory, saves, then re-draws ---
async function addTask(title, due, type) {
  const now = new Date().toISOString();
  appState.tasks.push({
    id: Date.now(),
    title,
    due,
    type,
    done: false,
    createdAt: now,
    updatedAt: now,   // same as createdAt at first; updated whenever the task changes
  });
  await persist();
  render();
}

async function toggleTask(id) {
  const task = appState.tasks.find((t) => t.id === id);
  if (task) {
    task.done = !task.done;
    task.updatedAt = new Date().toISOString();   // <-- stamp the change
  }
  await persist();
  render();
}

async function deleteTask(id) {
  appState.tasks = appState.tasks.filter((t) => t.id !== id); // keep all EXCEPT this id
  await persist();
  render();
}

// --- draw the current state onto the page ---
function render() {
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

// --- wire up the buttons ---
document.getElementById("addBtn").addEventListener("click", () => {
  if (!accessToken) { alert("Connect Google Drive first."); return; }
  const title = document.getElementById("taskTitle").value.trim();
  const due = document.getElementById("taskDue").value;
  const type = document.getElementById("taskType").value;
  if (!title) { alert("Enter a task title."); return; }
  addTask(title, due, type);
  document.getElementById("taskTitle").value = ""; // clear the input
});

document.getElementById("reloadBtn").addEventListener("click", () => {
  if (!accessToken) { alert("Connect Google Drive first."); return; }
  loadInitialData();
});