// drive.js — read/write MODULO's data file in the hidden Drive appDataFolder.
// Depends on auth.js only for the Authorization header.

import { authHeaders } from "./auth.js";

// The single file we store MODULO's data in, inside the hidden app folder.
const FILE_NAME = "modulo-data.json";

// Look inside the app folder for our data file. Returns its id, or null if it
// doesn't exist yet. Private to this module.
async function findFileId() {
  const res = await fetch(
    "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name)",
    { headers: authHeaders() }
  );
  const data = await res.json();
  const file = data.files.find((f) => f.name === FILE_NAME);
  return file ? file.id : null;
}

// Save an object as JSON into the app folder. Creates the file if missing,
// otherwise overwrites it.
export async function saveData(obj) {
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
export async function loadData() {
  const fileId = await findFileId();
  if (!fileId) return null; // nothing saved yet
  const res = await fetch(
    "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media",
    { headers: authHeaders() }
  );
  return await res.json();
}
