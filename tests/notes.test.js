// tests/notes.test.js — UNIT tests for the notes logic (web/logic/notes.js).
// Pure functions → no DOM/Drive needed. File inputs are plain literals shaped
// like a browser File ({ name, size, type }).

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  MAX_NOTE_BYTES,
  validateNoteFile,
  formatSize,
  visibleNotes,
  noteModules,
} from "../web/logic/notes.js";

// Note-shaped helper: what Drive's files.list returns for one note.
function note(id, module, handbook, modifiedTime) {
  return {
    id,
    name: `${id}.pdf`,
    size: "1024",
    mimeType: "application/pdf",
    appProperties: { moduloKind: "note", module, handbook },
    modifiedTime,
  };
}

// ---------- validateNoteFile ----------

test("validateNoteFile accepts PDFs and images under the cap", () => {
  assert.equal(validateNoteFile({ name: "lect4.pdf", size: 200_000, type: "application/pdf" }).ok, true);
  assert.equal(validateNoteFile({ name: "board.jpg", size: 3_000_000, type: "image/jpeg" }).ok, true);
  assert.equal(validateNoteFile({ name: "scan.png", size: 1, type: "image/png" }).ok, true);
});

test("validateNoteFile rejects disallowed types with a reason", () => {
  const docx = validateNoteFile({
    name: "notes.docx",
    size: 1000,
    type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  });
  assert.equal(docx.ok, false);
  assert.ok(docx.reason.length > 0, "needs a user-facing reason");
  // The browser reports "" for types it doesn't recognise — also rejected.
  assert.equal(validateNoteFile({ name: "mystery.xyz", size: 1000, type: "" }).ok, false);
});

test("validateNoteFile cap boundary: exactly 5 MB ok, one byte over rejected", () => {
  assert.equal(validateNoteFile({ name: "a.pdf", size: MAX_NOTE_BYTES, type: "application/pdf" }).ok, true);
  const over = validateNoteFile({ name: "a.pdf", size: MAX_NOTE_BYTES + 1, type: "application/pdf" });
  assert.equal(over.ok, false);
  assert.ok(over.reason.includes("5 MB"), "reason must state the limit");
});

test("validateNoteFile rejects empty files and unreadable input", () => {
  assert.equal(validateNoteFile({ name: "empty.pdf", size: 0, type: "application/pdf" }).ok, false);
  assert.equal(validateNoteFile(null).ok, false);
  assert.equal(validateNoteFile({ name: "x.pdf", size: "big", type: "application/pdf" }).ok, false);
});

// ---------- formatSize ----------

test("formatSize worked examples: B / KB / MB, one decimal, no trailing .0", () => {
  assert.equal(formatSize(372), "372 B");
  assert.equal(formatSize(52344), "51.1 KB");      // 52344/1024 = 51.117…
  assert.equal(formatSize(2 * 1024 * 1024), "2 MB"); // exactly 2.0 → "2"
  assert.equal(formatSize(1_450_000), "1.4 MB");     // 1.3828… MB rounds to 1.4
  assert.equal(formatSize(0), "0 B");
});

test("formatSize accepts Drive's string sizes; junk → empty string", () => {
  assert.equal(formatSize("52344"), "51.1 KB"); // files.list returns size as a STRING
  assert.equal(formatSize(""), "");
  assert.equal(formatSize("soon"), "");
  assert.equal(formatSize(-5), "");
  assert.equal(formatSize(undefined), "");
});

// ---------- visibleNotes ----------

test("visibleNotes filters by handbook, by module, and by both (A–Z default)", () => {
  const notes = [
    note("a", "CS2030S", "hb1", "2026-07-01T10:00:00.000Z"),
    note("b", "CS2030S", "hb2", "2026-07-02T10:00:00.000Z"),
    note("c", "MA1521", "hb1", "2026-07-03T10:00:00.000Z"),
  ];
  assert.deepEqual(visibleNotes(notes, { handbookId: "hb1" }).map((n) => n.id), ["a", "c"]);
  assert.deepEqual(visibleNotes(notes, { module: "CS2030S" }).map((n) => n.id), ["a", "b"]);
  assert.deepEqual(visibleNotes(notes, { handbookId: "hb1", module: "CS2030S" }).map((n) => n.id), ["a"]);
});

test("visibleNotes sorts: A–Z by default (numeric-aware), newest on request; input not mutated", () => {
  const notes = [
    { ...note("old", "CS2030S", "hb1", "2026-06-01T10:00:00.000Z"), name: "Lec 10.pdf" },
    { ...note("new", "MA1521", "hb2", "2026-07-10T10:00:00.000Z"), name: "lec 2.pdf" },
    { ...note("mid", "CS2030S", "hb1", "2026-07-01T10:00:00.000Z"), name: "Aims.pdf" },
  ];
  const before = notes.map((n) => n.id).join(",");
  // Default = alphabetical: Aims, then lec 2 BEFORE Lec 10 (numeric-aware +
  // case-insensitive — plain string order would put "Lec 10" first).
  assert.deepEqual(visibleNotes(notes).map((n) => n.id), ["mid", "new", "old"]);
  // sort:"newest" = by modifiedTime, most recent first.
  assert.deepEqual(visibleNotes(notes, { sort: "newest" }).map((n) => n.id), ["new", "mid", "old"]);
  assert.equal(notes.map((n) => n.id).join(","), before, "input order must be untouched");
});

test("visibleNotes trust boundary: junk input and missing appProperties never throw", () => {
  assert.deepEqual(visibleNotes(null), []);
  assert.deepEqual(visibleNotes("soon"), []);
  const stray = { id: "s", name: "s.pdf", modifiedTime: "2026-07-01T10:00:00.000Z" }; // no appProperties
  // A specific filter can't match it…
  assert.deepEqual(visibleNotes([stray], { handbookId: "hb1" }), []);
  // …but "All semesters" (no filters) still shows it — nothing is unreachable.
  assert.deepEqual(visibleNotes([stray]).map((n) => n.id), ["s"]);
});

// ---------- noteModules ----------

test("noteModules: distinct tags, sorted, empties and junk skipped", () => {
  const notes = [
    note("a", "MA1521", "hb1", ""),
    note("b", "CS2030S", "hb1", ""),
    note("c", "CS2030S", "hb2", ""), // duplicate label
    note("d", "", "hb1", ""),        // untagged → skipped
    { id: "e" },                     // no appProperties → skipped
  ];
  assert.deepEqual(noteModules(notes), ["CS2030S", "MA1521"]);
  assert.deepEqual(noteModules(undefined), []);
});
