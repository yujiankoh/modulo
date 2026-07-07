// tests/handbooks.test.js — UNIT tests for the multi-handbook snapshot/swap logic
// (web/logic/handbooks.js). Pure functions → no DOM needed.

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  HANDBOOK_FIELDS, snapshotHandbook, blankHandbook, switchHandbook,
} from "../web/logic/handbooks.js";

// A realistic state-shaped object: active handbook "A" in the flat fields, stored
// handbook "B" in otherHandbooks, plus GLOBAL fields that must never be swapped.
function sampleState() {
  return {
    schemaVersion: 2,
    handbookId: "A",
    educationLevel: "university",
    academicYear: "26/27",
    semester: 1,
    termStart: "2026-08-10",
    termEnd: "2026-11-27",
    breaks: [{ start: "2026-09-21", end: "2026-09-27" }],
    timetable: {
      educationLevel: "university",
      modules: [{ code: "CS2040S", name: "Data Structures", slots: [
        { day: "MON", start: "16:00", end: "18:00", location: "UT-AUD2",
          sessionType: "lecture", classNo: "1", week: "all" },
      ] }],
    },
    tasks: [{ id: 1, title: "Lab 1", module: "CS2040S", due: "2026-08-20",
      type: "assignment", done: false }],
    hiddenModules: ["GER1000"],
    handbookSetup: true,
    otherHandbooks: [{
      id: "B",
      educationLevel: "jc",
      academicYear: "2025",
      semester: 2,
      termStart: "2025-01-13",
      termEnd: "2025-05-30",
      breaks: [],
      timetable: { educationLevel: "jc", modules: [] },
      tasks: [{ id: 2, title: "GP essay", module: "", due: "", type: "assignment", done: true }],
      hiddenModules: [],
      handbookSetup: true,
    }],
    studySessions: [{ id: "s1", start: "2026-06-25T13:08:00Z", end: "2026-06-25T13:09:00Z",
      durationMins: 1, rating: 5, createdAt: "2026-06-25T13:09:00Z" }],
    updatedAt: "2026-07-05T00:00:00Z",
  };
}

test("snapshotHandbook captures the id and every handbook field", () => {
  const snap = snapshotHandbook(sampleState());
  assert.equal(snap.id, "A");
  for (const field of HANDBOOK_FIELDS) {
    assert.ok(field in snap, `snapshot is missing "${field}"`);
  }
  assert.equal(snap.educationLevel, "university");
  assert.equal(snap.tasks.length, 1);
});

test("snapshotHandbook excludes global fields (studySessions et al.)", () => {
  const snap = snapshotHandbook(sampleState());
  assert.ok(!("studySessions" in snap), "studySessions must stay global");
  assert.ok(!("schemaVersion" in snap));
  assert.ok(!("updatedAt" in snap));
  assert.ok(!("otherHandbooks" in snap), "a handbook must not nest the other handbooks");
});

test("snapshotHandbook deep-copies: later edits to the state don't reach the snapshot", () => {
  const state = sampleState();
  const snap = snapshotHandbook(state);
  state.timetable.modules[0].name = "CHANGED";   // edit the live state after snapshotting
  state.tasks.push({ id: 99, title: "new" });
  assert.equal(snap.timetable.modules[0].name, "Data Structures");
  assert.equal(snap.tasks.length, 1);
  snap.tasks[0].title = "MUTATED SNAPSHOT";      // and the reverse direction
  assert.equal(state.tasks[0].title, "Lab 1");
});

test("blankHandbook is a fresh, not-set-up handbook with the given id", () => {
  const blank = blankHandbook("NEW");
  assert.equal(blank.id, "NEW");
  assert.equal(blank.educationLevel, null);
  assert.equal(blank.handbookSetup, false);      // false → first-run onboarding opens
  assert.deepEqual(blank.tasks, []);
  assert.deepEqual(blank.breaks, []);
  assert.equal(blank.timetable, null);
  for (const field of HANDBOOK_FIELDS) {
    assert.ok(field in blank, `blank handbook is missing "${field}"`);
  }
});

test("switchHandbook swaps active and stored handbooks", () => {
  const state = sampleState();
  const result = switchHandbook(state, "B");
  assert.ok(result, "switch to a known id must not be a no-op");

  // The flat fields become handbook B...
  assert.equal(result.flat.handbookId, "B");
  assert.equal(result.flat.educationLevel, "jc");
  assert.equal(result.flat.tasks[0].title, "GP essay");
  // ...and otherHandbooks now holds A (and no longer B).
  const ids = result.otherHandbooks.map((h) => h.id);
  assert.deepEqual(ids, ["A"]);
  assert.equal(result.otherHandbooks[0].educationLevel, "university");
});

test("switchHandbook round-trips: A→B then B→A restores the original data", () => {
  const state = sampleState();
  const first = switchHandbook(state, "B");
  // Apply the result the way the caller will (assign flat + replace the array).
  const afterFirst = { ...state, ...first.flat, otherHandbooks: first.otherHandbooks };
  const second = switchHandbook(afterFirst, "A");
  const afterSecond = { ...afterFirst, ...second.flat, otherHandbooks: second.otherHandbooks };

  // Every handbook field is back to the original value.
  const original = sampleState();
  for (const field of HANDBOOK_FIELDS) {
    assert.deepEqual(afterSecond[field], original[field], `"${field}" did not round-trip`);
  }
  assert.equal(afterSecond.handbookId, "A");
  // Globals rode along untouched the whole time.
  assert.deepEqual(afterSecond.studySessions, original.studySessions);
});

test("switchHandbook does not mutate the input state", () => {
  const state = sampleState();
  const before = JSON.stringify(state);
  switchHandbook(state, "B");
  assert.equal(JSON.stringify(state), before, "input state must be untouched");
});

test("switchHandbook returns null for unknown or already-active ids", () => {
  const state = sampleState();
  assert.equal(switchHandbook(state, "NOPE"), null);
  assert.equal(switchHandbook(state, "A"), null);   // already active
});
