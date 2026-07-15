// logic/notes.js — PURE notes logic (Phase 20).
// No DOM / Drive / appState here, so it's unit-testable in Node (tests/notes.test.js).
// The impure parts — the actual Drive calls (drive.js) and the rendering
// (notesView.js) — live elsewhere; this file owns the RULES: what may be
// uploaded, how sizes read, and which notes a view shows.
//
// Note objects here are what Drive's files.list returns for a note:
//   { id, name, size, mimeType, appProperties: { moduloKind, module, handbook },
//     modifiedTime }
// Drive is a trust boundary like modulo-data.json (another client wrote the
// appProperties) — so nothing in this file ever throws on a weird shape.

// ---------- upload rules ----------

// 5 MB cap — Google's own recommended ceiling for simple/multipart uploads
// (anything bigger wants resumable upload, which we don't build for MS3).
// Raising this number alone is NOT enough to lift the cap — see the roadmap.
export const MAX_NOTE_BYTES = 5 * 1024 * 1024;

// The allowlist (decision 1: PDFs + images first — both open natively in a
// browser tab from a blob URL). `accept` is the same rule spelled the way an
// <input type="file"> wants it; exported from HERE so the picker's filter and
// the real validation can never drift apart. (`accept` is only a UI hint — the
// user can still pick "All files" in the dialog, so validateNoteFile re-checks.)
export const NOTE_ACCEPT = "application/pdf,image/*";

function isAllowedType(mime) {
  return mime === "application/pdf" || (typeof mime === "string" && mime.startsWith("image/"));
}

// Can this file be uploaded as a note?
// Takes { name, size, type } — a browser File object has exactly those fields,
// so the UI passes the File itself; tests pass a plain literal.
// → { ok: true } or { ok: false, reason } — reason is user-facing, shown as-is.
export function validateNoteFile(file) {
  if (!file || typeof file.size !== "number" || !Number.isFinite(file.size)) {
    return { ok: false, reason: "That file couldn't be read." };
  }
  if (!isAllowedType(file.type)) {
    return { ok: false, reason: "Only PDFs and images can be uploaded for now." };
  }
  if (file.size === 0) {
    return { ok: false, reason: "That file is empty." };
  }
  if (file.size > MAX_NOTE_BYTES) {
    return {
      ok: false,
      reason: `That file is ${formatSize(file.size)} — the limit is ${formatSize(MAX_NOTE_BYTES)}. ` +
        "Notes use your own Google Drive storage.",
    };
  }
  return { ok: true };
}

// ---------- display helpers ----------

// Human-readable file size: 372 → "372 B", 52344 → "51.1 KB", 2097152 → "2 MB".
// Accepts a number OR a numeric string, because Drive's files.list returns
// `size` as a STRING ("52344") — int64 fields don't fit safely in JSON numbers,
// so Google quotes them. Junk in → "" out (the UI just shows nothing).
export function formatSize(bytes) {
  const n = typeof bytes === "string" && bytes !== "" ? Number(bytes) : bytes;
  if (typeof n !== "number" || !Number.isFinite(n) || n < 0) return "";
  if (n < 1024) return `${Math.round(n)} B`;
  const kb = n / 1024;
  // Math.round(x*10)/10 keeps one decimal; JS drops a trailing .0 by itself
  // when the number prints ("2", not "2.0").
  if (kb < 1024) return `${Math.round(kb * 10) / 10} KB`;
  return `${Math.round((kb / 1024) * 10) / 10} MB`;
}

// ---------- filtering (decision 4: stored global, shown per semester) ----------

// The notes a view should show, newest first. Non-mutating (like
// getVisibleTasks): the input array and its objects are never touched.
//   handbookId — only notes tagged with this handbook; null = ALL semesters.
//   module     — only notes tagged with this module label; null = all modules.
// A note with missing/garbled appProperties simply fails every specific filter
// (it still shows under "All semesters" — nothing is ever unreachable).
export function visibleNotes(notes, { handbookId = null, module = null } = {}) {
  if (!Array.isArray(notes)) return [];
  const shown = notes.filter((note) => {
    const props = note?.appProperties || {};
    if (handbookId !== null && props.handbook !== handbookId) return false;
    if (module !== null && props.module !== module) return false;
    return true;
  });
  // Newest first. Drive's modifiedTime is RFC 3339 UTC ("2026-07-15T09:30:00.000Z"),
  // and same-format ISO timestamps order correctly as plain strings — no Date
  // parsing needed. A missing timestamp sorts last ("" < any real time).
  shown.sort((a, b) => String(b?.modifiedTime || "").localeCompare(String(a?.modifiedTime || "")));
  return shown;
}

// The distinct module tags present in `notes` (sorted, empties skipped) — feeds
// the module-filter dropdown, so it only ever offers filters that match something.
export function noteModules(notes) {
  if (!Array.isArray(notes)) return [];
  const labels = new Set();
  for (const note of notes) {
    const module = note?.appProperties?.module;
    if (typeof module === "string" && module !== "") labels.add(module);
  }
  return [...labels].sort();
}
