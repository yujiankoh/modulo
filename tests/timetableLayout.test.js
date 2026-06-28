// tests/timetableLayout.test.js — UNIT tests for overlap-lane assignment
// (web/logic/timetableLayout.js). This is the logic behind classes sitting side by side.

import { test } from "node:test";
import assert from "node:assert/strict";
import { layoutColumns } from "../web/logic/timetableLayout.js";

test("non-overlapping classes each take the full width (1 lane)", () => {
  const events = [
    { startMin: 480, endMin: 540 }, // 08:00–09:00
    { startMin: 600, endMin: 660 }, // 10:00–11:00
  ];
  layoutColumns(events);
  assert.deepEqual(events.map((e) => e.cols), [1, 1]);
  assert.deepEqual(events.map((e) => e.col), [0, 0]);
});

test("two overlapping classes split into two lanes", () => {
  const events = [
    { startMin: 480, endMin: 600 }, // 08:00–10:00
    { startMin: 540, endMin: 660 }, // 09:00–11:00  (overlaps the first)
  ];
  layoutColumns(events);
  assert.deepEqual(events.map((e) => e.cols), [2, 2]);          // both know there are 2 lanes
  assert.deepEqual(events.map((e) => e.col).sort(), [0, 1]);    // they occupy different lanes
});

test("a class that starts exactly when another ends does NOT overlap", () => {
  const events = [
    { startMin: 480, endMin: 540 }, // 08:00–09:00
    { startMin: 540, endMin: 600 }, // 09:00–10:00 (touches, but no overlap)
  ];
  layoutColumns(events);
  assert.deepEqual(events.map((e) => e.cols), [1, 1]);
});

test("three mutually overlapping classes use three lanes", () => {
  const events = [
    { startMin: 480, endMin: 660 },
    { startMin: 500, endMin: 660 },
    { startMin: 520, endMin: 660 },
  ];
  layoutColumns(events);
  assert.deepEqual(events.map((e) => e.cols), [3, 3, 3]);
  assert.deepEqual(events.map((e) => e.col).sort(), [0, 1, 2]);
});
