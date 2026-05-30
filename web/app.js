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

// --- wire up the buttons ---
const dataInput = document.getElementById("dataInput");
const outputEl = document.getElementById("output");

document.getElementById("saveBtn").addEventListener("click", async () => {
  if (!accessToken) { outputEl.textContent = "Connect Google Drive first."; return; }
  const payload = { text: dataInput.value, savedAt: new Date().toISOString() };
  await saveData(payload);
  outputEl.textContent = "Saved to Drive:\n" + JSON.stringify(payload, null, 2);
});

document.getElementById("loadBtn").addEventListener("click", async () => {
  if (!accessToken) { outputEl.textContent = "Connect Google Drive first."; return; }
  const data = await loadData();
  outputEl.textContent = data
    ? "Loaded from Drive:\n" + JSON.stringify(data, null, 2)
    : "No data found in Drive yet.";
});