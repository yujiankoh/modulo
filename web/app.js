// App's PUBLIC client ID, same as Android app
const CLIENT_ID = "332114614658-87cqh1e2u8luh9b5q15sf22sb30i3nda.apps.googleusercontent.com";

// Asking for access to app's private Drive folder.
const SCOPES = "https://www.googleapis.com/auth/drive.appdata";

const LOCAL_KEY = "modulo-data";   // local data
const MODE_KEY = "modulo-mode";    // Mode of saving data
let storageMode = null;            // "drive" | "local" | null
let accessToken = null;
let tokenExpiry = 0;       // timestamp (ms) when the current token goes stale
let tokenClient;
let resolveToken = null;   // used to "wait" for a token

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

  // Restore the user's saved mode on reload
  const savedMode = localStorage.getItem(MODE_KEY);
  if (savedMode === "local") {
    storageMode = "local";
    statusEl.textContent = "Local mode (this device only).";
    loadInitialData();
  } else if (savedMode === "drive") {
    statusEl.textContent = "Click Connect Google Drive to resume sync.";
    // Need to connect first and get a token
  }
};

// Ask Google for a token
function getToken() {
  return new Promise((resolve) => {
    resolveToken = resolve;             // promise is saved here
    tokenClient.requestAccessToken();   // triggers Google; the callback finishes it
  });
}

// Guarantee a valid, unexpired token before any Drive call.
async function ensureToken() {
  if (accessToken && Date.now() < tokenExpiry) return true; 
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

// The single file we'll store MODULO's data in, the hidden app folder.
const FILE_NAME = "modulo-data.json";

// Ensure the user has a token access
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

  // First time only: create the empty file.
  if (!fileId) {
    const createRes = await fetch("https://www.googleapis.com/drive/v3/files", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        name: FILE_NAME,
        parents: ["appDataFolder"], // putting data in the hidden app folder
      }),
    });
    const created = await createRes.json();
    fileId = created.id;
  }

  // Write the actual content into the file (how updates work).
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

// MODULO app state: what is held in memory
let appState = {
  schemaVersion: 2,        // was 1 — we finalized the timetable structure + added educationLevel
  educationLevel: null,
  tasks: [],
  timetable: null,
  updatedAt: null,
};

// Save the WHOLE current state to Drive (stamping the time first).
async function persist() {
  appState.updatedAt = new Date().toISOString();
  if (storageMode === "drive") {
    if (!(await ensureToken())) { statusEl.textContent = "Please reconnect Google Drive."; return; }
    await saveData(appState);                             
  } else if (storageMode === "local") {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(appState));
  }
}

// Load the saved state from Drive into memory, then draw it.
async function loadInitialData() {
  let saved = null;
  if (storageMode === "drive") {
    if (!(await ensureToken())) { statusEl.textContent = "Please reconnect Google Drive."; return; }
    saved = await loadData();                        
  } else if (storageMode === "local") {
    const raw = localStorage.getItem(LOCAL_KEY);
    saved = raw ? JSON.parse(raw) : null;           
  }
  if (saved) {
    appState = saved;
    if (appState.educationLevel) eduLevelEl.value = appState.educationLevel;
    if (!appState.tasks) appState.tasks = [];
  }
  render();
}

// Function for the different actions, each one changes memory, saves, then re-writes
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
  await persist(); // Saving
  render();  // Loading
}

// Toggles between the task's completion status
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

// Show the current state onto the page
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

// Connecting up the buttons
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

// Timetable uploading
let selectedImage = null;   // will hold { base64, mimeType } once an image is chosen

// Education level: save the choice so it syncs and the parser can use it (will change such that education level is selected somewhere else)
const eduLevelEl = document.getElementById("eduLevel");
eduLevelEl.addEventListener("change", () => {
  appState.educationLevel = eduLevelEl.value || null;
  persist();  
});

// Read an image file into a base64 data URL (To encode data as palin text). Wrapped in a Promise so we can await it.
function readImageAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);  // "data:image/png;base64,AAAA..."
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

// When the user picks an image: preview it and prep it for parsing
const imageInput = document.getElementById("timetableImage");
const previewEl = document.getElementById("timetablePreview");

imageInput.addEventListener("change", async () => {
  const file = imageInput.files[0];
  if (!file) return;

  const dataUrl = await readImageAsDataURL(file);
  previewEl.src = dataUrl;             // a data URL works directly as an <img> source
  previewEl.style.display = "block";

  // Gemini wants the mime type (png/jpeg/webp) and the raw base64 separately, so split the data URL
  // "data:image/png;base64,AAAA..."  ->  meta="data:image/png;base64"  data="AAAA..."
  const [meta, base64] = dataUrl.split(",");
  const mimeType = meta.match(/data:(.*);base64/)[1];
  selectedImage = { base64, mimeType };

  document.getElementById("parseOutput").textContent =
    `Image ready (${mimeType}, ~${Math.round(base64.length / 1024)} KB). Parsing is the next step.`;
});

// parse button: sends the image to the deployed proxy, which calls Gemini and returns parsed modules
const PROXY_URL = "https://modulo-proxy.onrender.com/parse-timetable";

document.getElementById("parseBtn").addEventListener("click", async () => {
  if (!appState.educationLevel) { alert("Choose your education level first."); return; }
  if (!selectedImage) { alert("Choose a timetable image first."); return; }

  const out = document.getElementById("parseOutput");
  out.textContent = "Parsing… (can take a few seconds)";

  try {
    const res = await fetch(PROXY_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        image: selectedImage.base64,
        mimeType: selectedImage.mimeType,
        educationLevel: appState.educationLevel,
      }),
    });
    if (!res.ok) throw new Error("Proxy returned " + res.status);
    const data = await res.json();

    appState.timetable = { modules: data.modules };  // store the parsed result in state
    await persist();                                  // save it (Drive or local)
    render();
    out.textContent = `Parsed ${data.modules.length} module(s):\n` +
      JSON.stringify(data.modules, null, 2);
  } catch (err) {
    out.textContent = "Parse failed: " + err.message;
  }
});