// Your app's PUBLIC client ID — the same one the Android app uses. Safe to be here.
const CLIENT_ID = "332114614658-87cqh1e2u8luh9b5q15sf22sb30i3nda.apps.googleusercontent.com";

// The permission we're requesting: access ONLY to our app's private Drive folder.
const SCOPES = "https://www.googleapis.com/auth/drive.appdata";

const LOCAL_KEY = "modulo-data";   // where local data is stored
const MODE_KEY = "modulo-mode";    // remembers the user's choice
let storageMode = null;            // "drive" | "local" | null (not chosen yet)
let accessToken = null;
let tokenExpiry = 0;       // timestamp (ms) when the current token goes stale
let tokenClient;
let resolveToken = null;   // used to "wait" for a token (explained below)

const statusEl = document.getElementById("status");
const connectBtn = document.getElementById("connectBtn");

// Runs after the page + Google's library have finished loading
window.onload = () => {
  tokenClient = google.accounts.oauth2.initTokenClient({
    client_id: CLIENT_ID,
    scope: SCOPES,
    callback: (response) => {
      if (response.error) {
        statusEl.textContent = "Sign-in error: " + response.error;
        if (resolveToken) { resolveToken(false); resolveToken = null; }
        return;
      }
      accessToken = response.access_token;
      tokenExpiry = Date.now() + (response.expires_in - 60) * 1000;
      statusEl.textContent = "Connected to Google Drive.";
      if (resolveToken) { resolveToken(true); resolveToken = null; }
    },
  });

  // --- NEW: restore the user's saved mode on reload ---
  const savedMode = localStorage.getItem(MODE_KEY);
  if (savedMode === "local") {
    storageMode = "local";
    statusEl.textContent = "Local mode (this device only).";
    loadInitialData();
  } else if (savedMode === "drive") {
    statusEl.textContent = "Click Connect Google Drive to resume sync.";
    // (Drive needs a fresh token, so we wait for the user to click Connect.)
  }
};

// Ask Google for a token, wrapped so we can `await` it.
function getToken() {
  return new Promise((resolve) => {
    resolveToken = resolve;             // remember how to finish this promise
    tokenClient.requestAccessToken();   // triggers Google; the callback finishes it
  });
}

// Guarantee a valid, unexpired token before any Drive call.
async function ensureToken() {
  if (accessToken && Date.now() < tokenExpiry) return true; // still good
  return await getToken();                                  // stale/missing → ask again
}

// Clicking the button opens Google's account chooser + consent popup
connectBtn.addEventListener("click", async () => {
  const ok = await getToken();
  if (ok) {
    storageMode = "drive";
    localStorage.setItem(MODE_KEY, "drive");
    loadInitialData();
  }
});

const localBtn = document.getElementById("localBtn");
localBtn.addEventListener("click", () => {
  storageMode = "local";
  localStorage.setItem(MODE_KEY, "local");
  statusEl.textContent = "Local mode (this device only).";
  loadInitialData();
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
  if (storageMode === "drive") {
    if (!(await ensureToken())) { statusEl.textContent = "Please reconnect Google Drive."; return; }
    await saveData(appState);                              // → Google Drive
  } else if (storageMode === "local") {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(appState)); // → this device
  }
}

// Load the saved state from Drive into memory, then draw it.
async function loadInitialData() {
  let saved = null;
  if (storageMode === "drive") {
    if (!(await ensureToken())) { statusEl.textContent = "Please reconnect Google Drive."; return; }
    saved = await loadData();                              // ← Google Drive
  } else if (storageMode === "local") {
    const raw = localStorage.getItem(LOCAL_KEY);
    saved = raw ? JSON.parse(raw) : null;                 // ← this device
  }
  if (saved) {
    appState = saved;
    if (!appState.tasks) appState.tasks = [];
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