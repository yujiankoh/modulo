// tests/academicYear.test.js — UNIT tests for the level-aware academic-year logic
// (web/logic/academicYear.js). Pure functions → no DOM needed.

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  isTertiary, formatAcademicYear, parseStartYear, formatHeaderLabel,
} from "../web/logic/academicYear.js";

test("isTertiary is true only for university and poly", () => {
  assert.equal(isTertiary("university"), true);
  assert.equal(isTertiary("poly"), true);
  assert.equal(isTertiary("secondary"), false);
  assert.equal(isTertiary("jc"), false);
});

test("formatAcademicYear: tertiary spans two years, school is a single year", () => {
  assert.equal(formatAcademicYear(2025, "university"), "25/26");
  assert.equal(formatAcademicYear(2025, "poly"), "25/26");
  assert.equal(formatAcademicYear(2026, "secondary"), "2026");
  assert.equal(formatAcademicYear(null, "university"), null); // nothing entered yet
});

test("parseStartYear is the inverse of formatAcademicYear", () => {
  assert.equal(parseStartYear("25/26", "university"), 2025);
  assert.equal(parseStartYear("2026", "secondary"), 2026);
  assert.equal(parseStartYear(null, "university"), null);
});

test("formatHeaderLabel formats per level and is empty until complete", () => {
  assert.equal(formatHeaderLabel("university", "25/26", 1), "AY25/26 · S1");
  assert.equal(formatHeaderLabel("secondary", "2026", 2), "2026 · Sem 2");
  assert.equal(formatHeaderLabel("university", null, 1), "");  // no year yet
  assert.equal(formatHeaderLabel("university", "25/26", null), ""); // no semester yet
});
