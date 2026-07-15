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

// ---------- Phase 20: note files ----------
// Each note = its OWN Drive file next to modulo-data.json (never inside it),
// identified by appProperties.moduloKind === "note" and tagged with the module
// label + the handbookId it was uploaded under. Contract documented in
// docs/modulo-data-schema.md ("Note files") — coordinate changes with Ling Song.
//
// Unlike saveData/loadData above, these throw on failure — with a message
// that's already user-readable, so notesView.js just catches and shows it.
// Callers are responsible for ensureToken() first (same rule as persist()).

// The fields we ask Drive to return for a note (files.list and upload both) —
// exactly the note shape logic/notes.js works on.
const NOTE_FIELDS = "id,name,size,mimeType,appProperties,modifiedTime";

// Turn a failed Drive response into a user-facing Error. Drive errors carry a
// machine `reason` (e.g. "storageQuotaExceeded") in their JSON body.
async function driveError(res) {
  let reason = "";
  try { reason = (await res.json())?.error?.errors?.[0]?.reason || ""; } catch { /* body wasn't JSON */ }
  if (res.status === 401) return new Error("Google sign-in expired — please reconnect and try again.");
  if (reason === "storageQuotaExceeded") {
    return new Error("Your Google Drive is full — free up space or delete some notes.");
  }
  return new Error(`Google Drive request failed (${res.status}). Please try again.`);
}

// Upload one note file. MULTIPART upload = metadata + bytes in ONE request
// (saveData's create-then-patch two-step would waste a round trip per note).
// FormData builds the multipart body for us: each append() becomes one part,
// and the browser sets the Content-Type header WITH its generated boundary —
// which is why we must not set Content-Type ourselves here.
// Returns the created note (NOTE_FIELDS shape) so the caller can add it to
// its list without re-fetching.
export async function uploadNote(file, module, handbookId) {
  const metadata = {
    name: file.name,               // keep the user's filename; Drive ids, not names, identify files
    parents: ["appDataFolder"],
    appProperties: {
      moduloKind: "note",
      module: module || "",        // "" = untagged, still listable
      handbook: handbookId || "",
    },
  };
  const body = new FormData();
  body.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
  body.append("file", file);       // the File IS a Blob — bytes + its own mimeType

  const res = await fetch(
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=" + NOTE_FIELDS,
    { method: "POST", headers: authHeaders(), body }
  );
  if (!res.ok) throw await driveError(res);
  return await res.json();
}

// List ALL notes (every semester/module — filtering is client-side in
// logic/notes.js, so the session cache holds everything once). Paginated:
// Drive returns at most `pageSize` files + a nextPageToken; loop until the
// token stops coming so a big collection is never silently truncated.
export async function listNotes() {
  const notes = [];
  let pageToken = "";
  do {
    const params = new URLSearchParams({
      spaces: "appDataFolder",
      q: "appProperties has { key='moduloKind' and value='note' }",
      fields: `nextPageToken,files(${NOTE_FIELDS})`,
      pageSize: "100",
    });
    if (pageToken) params.set("pageToken", pageToken);
    const res = await fetch("https://www.googleapis.com/drive/v3/files?" + params, {
      headers: authHeaders(),
    });
    if (!res.ok) throw await driveError(res);
    const data = await res.json();
    notes.push(...(data.files || []));
    pageToken = data.nextPageToken || "";
  } while (pageToken);
  return notes;
}

// Download a note's bytes as a Blob (same ?alt=media as loadData, minus the
// JSON parsing). The caller turns it into a blob URL to open — and revokes it.
export async function downloadNote(id) {
  const res = await fetch(
    "https://www.googleapis.com/drive/v3/files/" + id + "?alt=media",
    { headers: authHeaders() }
  );
  if (!res.ok) throw await driveError(res);
  return await res.blob();
}

// Delete a note file (reclaims the user's Drive quota; UI confirms first).
// A 404 counts as success: "already deleted on another device" reaches the
// same goal state, so erroring would only confuse.
export async function deleteNote(id) {
  const res = await fetch("https://www.googleapis.com/drive/v3/files/" + id, {
    method: "DELETE",
    headers: authHeaders(),
  });
  if (!res.ok && res.status !== 404) throw await driveError(res);
}
