// tests/mergeModules.test.js — UNIT tests for the timetable merge used by "Add another
// week" (web/logic/mergeModules.js). Verifies de-duplication + combining odd/even uploads.

import { test } from "node:test";
import assert from "node:assert/strict";
import { mergeModules, sameSlot } from "../web/logic/mergeModules.js";

const slotA = {
  day: "MON", start: "09:00", end: "10:00", location: "",
  sessionType: "lecture", classNo: "1", week: "all",
};

test("sameSlot is true only when every field matches", () => {
  assert.equal(sameSlot(slotA, { ...slotA }), true);
  assert.equal(sameSlot(slotA, { ...slotA, day: "TUE" }), false);
  assert.equal(sameSlot(slotA, { ...slotA, week: "odd" }), false);
});

test("mergeModules combines slots of the same module and skips exact duplicates", () => {
  const existing = [{ code: "CS2030S", name: "", slots: [slotA] }];
  const incoming = [{ code: "CS2030S", name: "", slots: [slotA, { ...slotA, day: "TUE" }] }];
  const merged = mergeModules(existing, incoming);
  assert.equal(merged.length, 1);            // same module → not duplicated
  assert.equal(merged[0].slots.length, 2);   // duplicate slotA skipped, the TUE slot added
});

test("mergeModules adds a separate module when code/name differ", () => {
  const merged = mergeModules(
    [{ code: "CS2030S", name: "", slots: [slotA] }],
    [{ code: "CS2040S", name: "", slots: [slotA] }],
  );
  assert.equal(merged.length, 2);
});

test("mergeModules does not mutate the original arrays/objects", () => {
  const existing = [{ code: "CS2030S", name: "", slots: [slotA] }];
  mergeModules(existing, [{ code: "CS2030S", name: "", slots: [{ ...slotA, day: "TUE" }] }]);
  assert.equal(existing[0].slots.length, 1); // original untouched (merge cloned it)
});
