// notesView.js — the Notes view (#view-notes, Phase 20) + the upload-note modal.
// The ONE impure caller of the notes machinery: it talks to Drive (via the store
// below), reads appState for the semester scope, and renders. All RULES (what may
// be uploaded, filtering, sizes) live in logic/notes.js — pure and unit-tested.
//
// Notes are files in the user's Drive appDataFolder, NOT part of modulo-data.json
// — so unlike every other view, this one has data the modulo:datachanged event
// doesn't carry. It keeps a SESSION CACHE (decision 2026-07-15): fetched lazily on
// the first visit to #notes, updated in place by upload/delete (the Drive response
// tells us the result — no re-fetch), and re-fetched only via the Refresh button.
// Nothing about notes is ever persisted in appState — nothing to drift or migrate.

import { appState, getStorageMode } from "./data.js";
import { ensureToken } from "./auth.js";
import { uploadNote, listNotes, downloadNote, deleteNote, renameNote } from "./drive.js";
import { drawIcons } from "./icons.js";
import {
  NOTE_ACCEPT,
  validateNoteFile,
  formatSize,
  visibleNotes,
  noteModules,
} from "./logic/notes.js";
import { moduleColor } from "./sidebar.js";

// The storage backend behind ONE narrow interface (decision 2026-07-15's hedge):
// everything below talks to `store`, never to drive.js directly — so a future
// local/IndexedDB backend is a swap here, not a rewrite of the view.
const store = {
  list: listNotes,
  upload: uploadNote,
  download: downloadNote,
  rename: renameNote,
  remove: deleteNote,
};

// ---- session cache ----------------------------------------------------------
let cache = null;      // null = never fetched this session; [] = fetched, none exist
let loading = false;   // guards against double fetches (visit + quick Refresh)

// ---- elements ----------------------------------------------------------------
const controlsEl = document.getElementById("notesControls");
const semFilterEl = document.getElementById("notesSemFilter");
const modFilterEl = document.getElementById("notesModFilter");
const sortEl = document.getElementById("notesSort");
const refreshBtn = document.getElementById("notesRefreshBtn");
const uploadBtn = document.getElementById("notesUploadBtn");
const statusEl = document.getElementById("notesStatus");
const listEl = document.getElementById("notesList");
const emptyEl = document.getElementById("notesEmpty");
const emptyTitleEl = document.getElementById("notesEmptyTitle");
const emptyTextEl = document.getElementById("notesEmptyText");
const gateEl = document.getElementById("notesGate");

const renameModal = document.getElementById("noteRenameModal");
const renameClose = document.getElementById("noteRenameClose");
const renameCancel = document.getElementById("noteRenameCancel");
const renameSave = document.getElementById("noteRenameSave");
const renameInput = document.getElementById("noteRenameInput");
const renameError = document.getElementById("noteRenameError");

const modal = document.getElementById("noteUploadModal");
const modalClose = document.getElementById("noteUploadClose");
const modalCancel = document.getElementById("noteUploadCancel");
const modalGo = document.getElementById("noteUploadGo");
const modalError = document.getElementById("noteUploadError");
const fileInput = document.getElementById("noteFile");
const moduleSelect = document.getElementById("noteModule");
const moduleOtherField = document.getElementById("noteModuleOtherField");
const moduleOtherInput = document.getElementById("noteModuleOther");

// The picker's filter comes from the SAME constant the validation uses — one
// allowlist, two consumers, no drift. (accept is only a dialog hint; the real
// check is validateNoteFile on the picked file.)
fileInput.accept = NOTE_ACCEPT;

const OTHER = "__other__"; // "+ Add other…" sentinel, same pattern as task.js

// ---- fetching ----------------------------------------------------------------

function showStatus(text) {
  statusEl.style.display = text ? "" : "none";
  statusEl.textContent = text || "";
}

// Fetch the list ONCE per session (or again after refresh() clears the cache).
async function ensureCache() {
  if (cache !== null || loading || getStorageMode() !== "drive") return;
  loading = true;
  showStatus("Loading notes…");
  try {
    if (!(await ensureToken())) throw new Error("Google sign-in expired — please reconnect and try again.");
    cache = await store.list();
    showStatus("");
  } catch (e) {
    showStatus(`${e.message} (Refresh to retry.)`);
  } finally {
    loading = false;
    render();
  }
}

function refresh() {
  cache = null;   // forget everything…
  render();
  ensureCache();  // …and fetch anew (also picks up changes made on other devices)
}

// ---- rendering ----------------------------------------------------------------

// The semester scope for logic/notes.js: the active handbook's id, or null = all.
function scopeHandbookId() {
  return semFilterEl.value === "current" ? appState.handbookId : null;
}

// Rebuild the module-filter options from the notes IN the current semester scope,
// so it only offers filters that match something. The selection survives the
// rebuild when its module still exists; otherwise it falls back to "all".
function rebuildModuleFilter() {
  const selected = modFilterEl.value || "all";
  modFilterEl.innerHTML = "";
  modFilterEl.append(new Option("All modules", "all"));
  for (const m of noteModules(visibleNotes(cache || [], { handbookId: scopeHandbookId() }))) {
    modFilterEl.append(new Option(m, m));
  }
  modFilterEl.value = [...modFilterEl.options].some((o) => o.value === selected) ? selected : "all";
}

// Open a note in a new tab. The tab is opened SYNCHRONOUSLY (inside the click)
// because after an `await` the browser no longer treats window.open as
// user-initiated and may block it as a popup — then we point it at a blob URL
// once the bytes arrive. The URL is revoked afterwards (blob URLs pin the bytes
// in memory until revoked); a minute is comfortably after the tab has loaded.
async function openNote(note) {
  const tab = window.open("", "_blank");
  if (!tab) { showStatus("Your browser blocked the new tab — allow pop-ups for this site."); return; }
  try {
    if (!(await ensureToken())) throw new Error("Google sign-in expired — please reconnect and try again.");
    const blob = await store.download(note.id);
    const url = URL.createObjectURL(blob);
    tab.location = url;
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    tab.close();
    showStatus(e.message);
  }
}

// Rename (mid-phase addition 2026-07-15; prompt() → modal same day, YJ's call —
// the native prompt can't be styled and reads as foreign next to the app's
// dialogs). Which note is being renamed lives here between open and save.
let renameTarget = null;

function openRenameModal(note) {
  renameTarget = note;
  renameInput.value = note.name;
  renameError.textContent = "";
  renameModal.style.display = "flex";
  renameInput.focus();
  renameInput.select(); // the whole name pre-selected — typing replaces it outright
}

function closeRenameModal() {
  renameModal.style.display = "none";
  renameTarget = null;
}

// An unchanged or emptied name is a no-op. The Drive response replaces the
// cache entry, so the row re-renders with the stored truth, not what we hope.
async function saveRename() {
  if (!renameTarget) return;
  const name = renameInput.value.trim();
  if (!name) { renameError.textContent = "Enter a name."; return; }
  if (name === renameTarget.name) { closeRenameModal(); return; }

  renameSave.disabled = true;
  const saveLabel = renameSave.innerHTML; // keep the icon; restore after
  renameSave.textContent = "Saving…";
  try {
    if (!(await ensureToken())) throw new Error("Google sign-in expired — please reconnect and try again.");
    const updated = await store.rename(renameTarget.id, name);
    cache = (cache || []).map((n) => (n.id === renameTarget.id ? updated : n));
    closeRenameModal();
    showStatus("");
    render();
  } catch (e) {
    renameError.textContent = e.message;
  } finally {
    renameSave.disabled = false;
    renameSave.innerHTML = saveLabel;
  }
}

async function removeNote(note) {
  if (!confirm(`Delete "${note.name}"?\nThis removes it from your Google Drive.`)) return;
  try {
    if (!(await ensureToken())) throw new Error("Google sign-in expired — please reconnect and try again.");
    await store.remove(note.id);
    cache = (cache || []).filter((n) => n.id !== note.id);
    showStatus("");
    render();
  } catch (e) {
    showStatus(e.message);
  }
}

function buildRow(note) {
  const row = document.createElement("div");
  row.className = "note-row";

  const module = note.appProperties?.module || "";
  const dot = document.createElement("span");
  dot.className = "grade-dot"; // same 14px colour dot as the grades rows
  dot.style.background = module ? moduleColor(module) : "var(--border)";

  // Name cell = the filename (the "open" affordance, a button styled as a link)
  // + the rename pencil right beside it (moved from the row's end, YJ 2026-07-15).
  // One flex wrapper occupies the grid's 1fr column so the name still ellipsizes.
  const nameCell = document.createElement("span");
  nameCell.className = "note-name-cell";

  const name = document.createElement("button");
  name.className = "note-name";
  name.type = "button";
  name.textContent = note.name;
  name.title = `Open ${note.name}`;
  name.addEventListener("click", () => openNote(note));

  const rename = document.createElement("button");
  rename.className = "note-action";
  rename.type = "button";
  rename.innerHTML = `<i data-lucide="pencil"></i>`; // drawIcons() turns this into the svg
  rename.title = `Rename ${note.name}`;
  rename.addEventListener("click", () => openRenameModal(note));

  nameCell.append(name, rename);

  const mod = document.createElement("span");
  mod.className = "note-mod";
  mod.textContent = module;

  const size = document.createElement("span");
  size.className = "note-meta";
  size.textContent = formatSize(note.size);

  const date = document.createElement("span");
  date.className = "note-meta";
  const d = new Date(note.modifiedTime);
  date.textContent = Number.isNaN(d.getTime())
    ? ""
    : d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

  const remove = document.createElement("button");
  remove.className = "note-action note-remove";
  remove.type = "button";
  remove.textContent = "×";
  remove.title = `Delete ${note.name}`;
  remove.addEventListener("click", () => removeNote(note));

  row.append(dot, nameCell, mod, size, date, remove);
  return row;
}

function render() {
  // The gate first (decision 2026-07-15): notes ARE Drive files — in local mode
  // there is nothing to list, so the whole view is one explanatory panel.
  const gated = getStorageMode() !== "drive";
  gateEl.style.display = gated ? "" : "none";
  controlsEl.style.display = gated ? "none" : "";
  if (gated) {
    listEl.style.display = "none";
    emptyEl.style.display = "none";
    showStatus("");
    return;
  }

  rebuildModuleFilter();
  const module = modFilterEl.value === "all" ? null : modFilterEl.value;
  const shown = visibleNotes(cache || [], {
    handbookId: scopeHandbookId(),
    module,
    sort: sortEl.value, // "name" (A–Z, the default) or "newest"
  });

  listEl.innerHTML = "";
  listEl.style.display = shown.length ? "" : "none";
  for (const note of shown) listEl.append(buildRow(note));

  // Empty states: nothing uploaded at all vs nothing matching the filters.
  const anyAtAll = (cache || []).length > 0;
  emptyEl.style.display = cache !== null && shown.length === 0 ? "" : "none";
  emptyTitleEl.textContent = anyAtAll ? "Nothing here" : "No notes yet";
  emptyTextEl.textContent = anyAtAll
    ? "No notes match these filters — try All semesters or All modules."
    : "Upload a PDF or a photo of your notes to keep it synced with this device.";

  // The rename buttons are <i data-lucide> placeholders; render() also runs
  // WITHOUT a modulo:datachanged (filter change, fetch landing), so icons.js's
  // event listener won't always fire — draw them ourselves (idempotent).
  drawIcons();
}

// ---- upload modal ---------------------------------------------------------------

// Module labels the app knows: the timetable's + any tag already on a note —
// so re-tagging a second file to the same module is a pick, not typing.
function knownModuleLabels() {
  const set = new Set();
  for (const m of appState.timetable?.modules || []) {
    const label = m.code || m.name;
    if (label) set.add(label);
  }
  for (const m of noteModules(cache || [])) set.add(m);
  return [...set].sort();
}

function openUploadModal() {
  moduleSelect.innerHTML = "";
  moduleSelect.append(new Option("— None —", ""));
  for (const m of knownModuleLabels()) moduleSelect.append(new Option(m, m));
  moduleSelect.append(new Option("+ Add other…", OTHER));
  moduleSelect.value = "";
  moduleOtherField.style.display = "none";
  moduleOtherInput.value = "";
  fileInput.value = ""; // stale pick from a cancelled attempt must not linger
  modalError.textContent = "";
  modal.style.display = "flex";
}

function closeUploadModal() {
  modal.style.display = "none";
}

moduleSelect.addEventListener("change", () => {
  const isOther = moduleSelect.value === OTHER;
  moduleOtherField.style.display = isOther ? "" : "none";
  if (isOther) moduleOtherInput.focus();
});

async function doUpload() {
  const file = fileInput.files[0];
  if (!file) { modalError.textContent = "Choose a file first."; return; }
  const check = validateNoteFile(file);
  if (!check.ok) { modalError.textContent = check.reason; return; }
  const module = moduleSelect.value === OTHER ? moduleOtherInput.value.trim() : moduleSelect.value;

  modalGo.disabled = true;
  modalError.textContent = "";
  const goLabel = modalGo.innerHTML; // keep the icon; restore after
  modalGo.textContent = "Uploading…";
  try {
    if (!(await ensureToken())) throw new Error("Google sign-in expired — please reconnect and try again.");
    const created = await store.upload(file, module, appState.handbookId);
    if (cache !== null) cache.push(created); // the response IS the new list entry — no re-fetch
    closeUploadModal();
    render();
  } catch (e) {
    modalError.textContent = e.message;
  } finally {
    modalGo.disabled = false;
    modalGo.innerHTML = goLabel;
  }
}

uploadBtn.addEventListener("click", openUploadModal);
modalGo.addEventListener("click", doUpload);
modalClose.addEventListener("click", closeUploadModal);
modalCancel.addEventListener("click", closeUploadModal);
renameSave.addEventListener("click", saveRename);
renameClose.addEventListener("click", closeRenameModal);
renameCancel.addEventListener("click", closeRenameModal);
renameInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter") saveRename();
});
refreshBtn.addEventListener("click", refresh);
semFilterEl.addEventListener("change", render);
modFilterEl.addEventListener("change", render);
sortEl.addEventListener("change", render);

// Lazy fetch: the first time the route is #notes this session, load the list.
// (Cheaper than fetching at boot for users who never open the view.)
function maybeFetch() {
  if (location.hash === "#notes") ensureCache();
}
window.addEventListener("hashchange", maybeFetch);
maybeFetch(); // the page may LOAD on #notes (refresh / shared link)

// Storage-mode changes and handbook switches both announce themselves through
// modulo:datachanged — re-render so the gate and the semester scope follow.
window.addEventListener("modulo:datachanged", () => {
  render();
  maybeFetch(); // just connected Drive while ON the notes view → fetch now
});
render();
