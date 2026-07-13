// tests/gpa.test.js — UNIT tests for the grade-calculator logic (web/logic/gpa.js).
// Pure functions → no DOM needed. Worked examples are hand-computed in comments.

import { test } from "node:test";
import assert from "node:assert/strict";
import { SCHEMES, schemeForLevel, computeGPA, cumulativeGPA } from "../web/logic/gpa.js";

// ---------- schemeForLevel ----------

test("schemeForLevel maps university → nus5 and polytechnic → poly4", () => {
  const uni = schemeForLevel("university");
  assert.equal(uni.supported, true);
  assert.equal(uni.scheme.id, "nus5");
  assert.equal(uni.scheme.maxPoints, 5.0);

  const poly = schemeForLevel("polytechnic");
  assert.equal(poly.supported, true);
  assert.equal(poly.scheme.id, "poly4");
  assert.equal(poly.scheme.maxPoints, 4.0);
});

test("schemeForLevel stubs jc/secondary/primary with a human-readable reason", () => {
  for (const level of ["jc", "secondary", "primary"]) {
    const result = schemeForLevel(level);
    assert.equal(result.supported, false, `${level} must be unsupported`);
    assert.ok(result.reason.length > 0, `${level} needs a reason to show`);
    assert.ok(!("scheme" in result), "unsupported result must not carry a scheme");
  }
});

test("schemeForLevel treats a null/unknown level as unsupported, not an error", () => {
  assert.equal(schemeForLevel(null).supported, false);
  assert.equal(schemeForLevel(undefined).supported, false);
  assert.equal(schemeForLevel("hogwarts").supported, false);
});

// ---------- computeGPA: worked examples ----------

test("NUS worked example: A+, B+, A-, C at 4 MCs each = 3.875", () => {
  // (5.0×4 + 4.0×4 + 4.5×4 + 2.0×4) / 16 = (20+16+18+8)/16 = 62/16 = 3.875
  const entries = [
    { id: "1", module: "CS2030S", credits: 4, grade: "A+" },
    { id: "2", module: "MA1521", credits: 4, grade: "B+" },
    { id: "3", module: "CS2040S", credits: 4, grade: "A-" },
    { id: "4", module: "GEA1000", credits: 4, grade: "C" },
  ];
  const result = computeGPA(entries, SCHEMES.nus5);
  assert.equal(result.gpa, 3.875);
  assert.equal(result.gradedCredits, 16);
  assert.equal(result.gradedCount, 4);
  assert.equal(result.excludedCount, 0);
  assert.equal(result.skippedCount, 0);
});

test("credit weighting: a bigger module pulls the average harder", () => {
  // A (5.0) at 8 MCs + F (0.0) at 4 MCs = 40/12 — NOT the unweighted (5+0)/2.
  const entries = [
    { id: "1", module: "CP2106", credits: 8, grade: "A" },
    { id: "2", module: "CS1101S", credits: 4, grade: "F" },
  ];
  const result = computeGPA(entries, SCHEMES.nus5);
  assert.equal(result.gpa, 40 / 12);
  assert.equal(result.gradedCredits, 12);
});

test("poly worked example: DIST, B, C+ with mixed credits", () => {
  // (4.0×5 + 3.0×4 + 2.5×3) / 12 = (20+12+7.5)/12 = 39.5/12
  const entries = [
    { id: "1", module: "IT1234", credits: 5, grade: "DIST" },
    { id: "2", module: "MS5678", credits: 4, grade: "B" },
    { id: "3", module: "LC9012", credits: 3, grade: "C+" },
  ];
  const result = computeGPA(entries, SCHEMES.poly4);
  assert.equal(result.gpa, 39.5 / 12);
  assert.equal(result.gradedCount, 3);
});

// ---------- computeGPA: exclusion (S/U et al.) ----------

test("S/U entries are excluded: the GPA is identical with or without them", () => {
  const graded = [
    { id: "1", module: "CS2030S", credits: 4, grade: "A" },
    { id: "2", module: "MA1521", credits: 4, grade: "B" },
  ];
  const withSU = [
    ...graded,
    { id: "3", module: "GEC1015", credits: 4, grade: "S" },
    { id: "4", module: "ES1103", credits: 4, grade: "U" },
    { id: "5", module: "CP2201", credits: 2, grade: "CS" },
  ];
  const base = computeGPA(graded, SCHEMES.nus5);
  const result = computeGPA(withSU, SCHEMES.nus5);
  assert.equal(result.gpa, base.gpa);
  assert.equal(result.gradedCredits, base.gradedCredits, "excluded credits must not enter the denominator");
  assert.equal(result.excludedCount, 3);
  assert.equal(result.skippedCount, 0, "excluded is not skipped — S/U are valid grades");
});

test("poly P (pass/fail) is excluded the same way", () => {
  const entries = [
    { id: "1", module: "IT1234", credits: 4, grade: "A" },
    { id: "2", module: "LC0001", credits: 2, grade: "P" },
  ];
  const result = computeGPA(entries, SCHEMES.poly4);
  assert.equal(result.gpa, 4.0);
  assert.equal(result.excludedCount, 1);
});

test("all-excluded entries → gpa null (no denominator), not 0", () => {
  const entries = [
    { id: "1", module: "GEC1015", credits: 4, grade: "S" },
    { id: "2", module: "ES1103", credits: 4, grade: "U" },
  ];
  const result = computeGPA(entries, SCHEMES.nus5);
  assert.equal(result.gpa, null);
  assert.equal(result.excludedCount, 2);
});

// ---------- computeGPA: empty + defensive skipping ----------

test("empty or missing entries → gpa null, all counts 0", () => {
  for (const entries of [[], null, undefined]) {
    const result = computeGPA(entries, SCHEMES.nus5);
    assert.equal(result.gpa, null);
    assert.equal(result.gradedCount, 0);
    assert.equal(result.skippedCount, 0);
  }
});

test("junk rows are skipped and reported, never thrown, and don't taint the average", () => {
  // Only the first row is countable: A at 4 MCs → gpa exactly 5.0.
  const entries = [
    { id: "1", module: "CS2030S", credits: 4, grade: "A" },
    { id: "2", module: "X1", credits: 4, grade: "A*" },      // unknown grade
    { id: "3", module: "X2", credits: 0, grade: "B" },       // zero credits
    { id: "4", module: "X3", credits: -4, grade: "B" },      // negative credits
    { id: "5", module: "X4", credits: "4", grade: "B" },     // credits as a string
    { id: "6", module: "X5", credits: 4 },                   // no grade at all
    { id: "7", module: "X6", credits: NaN, grade: "B" },     // NaN credits
    null,                                                    // not even an object
  ];
  const result = computeGPA(entries, SCHEMES.nus5);
  assert.equal(result.gpa, 5.0);
  assert.equal(result.gradedCount, 1);
  assert.equal(result.skippedCount, 7);
});

test("grades are normalised: lowercase and padded strings still count", () => {
  const entries = [
    { id: "1", module: "CS2030S", credits: 4, grade: "a+" },
    { id: "2", module: "MA1521", credits: 4, grade: " b " },
    { id: "3", module: "GEC1015", credits: 4, grade: "s" },
  ];
  const result = computeGPA(entries, SCHEMES.nus5);
  // (5.0×4 + 3.5×4) / 8 = 34/8 = 4.25; the lowercase "s" is excluded, not skipped.
  assert.equal(result.gpa, 4.25);
  assert.equal(result.excludedCount, 1);
  assert.equal(result.skippedCount, 0);
});

test("computeGPA does not mutate its input entries", () => {
  const entries = [{ id: "1", module: "CS2030S", credits: 4, grade: "a+" }];
  const before = JSON.stringify(entries);
  computeGPA(entries, SCHEMES.nus5);
  assert.equal(JSON.stringify(entries), before, "input rows must be untouched");
});

// ---------- cumulativeGPA ----------

// State-shaped literal: active university handbook + one stored university
// handbook (combines), one stored JC handbook (never combines), one stored
// pre-Phase-16 handbook with NO grades key (tolerated as empty).
function multiSemesterState() {
  return {
    handbookId: "NOW",
    educationLevel: "university",
    grades: [{ id: "g1", module: "CS2030S", credits: 4, grade: "A" }],      // 5.0×4
    otherHandbooks: [
      {
        id: "PAST-UNI",
        educationLevel: "university",
        grades: [{ id: "g2", module: "MA1521", credits: 4, grade: "B" }],   // 3.5×4
      },
      {
        id: "PAST-JC",
        educationLevel: "jc",
        grades: [{ id: "g3", module: "H2 Math", credits: 4, grade: "A" }],  // must NOT count
      },
      { id: "PRE-16", educationLevel: "university" },                       // no grades key
    ],
    studySessions: [],
  };
}

test("cumulativeGPA pools the active handbook with same-scheme stored handbooks only", () => {
  // (5.0×4 + 3.5×4) / 8 = 4.25 — the JC handbook's A contributes nothing.
  const result = cumulativeGPA(multiSemesterState());
  assert.equal(result.gpa, 4.25);
  assert.equal(result.gradedCredits, 8);
  assert.equal(result.gradedCount, 2);
});

test("cumulativeGPA: poly and university never combine even though both are numeric", () => {
  const state = multiSemesterState();
  state.otherHandbooks = [{
    id: "PAST-POLY",
    educationLevel: "polytechnic",
    grades: [{ id: "g4", module: "IT1234", credits: 20, grade: "DIST" }],
  }];
  const result = cumulativeGPA(state);
  assert.equal(result.gpa, 5.0, "only the active handbook's A must count");
  assert.equal(result.gradedCredits, 4);
});

test("cumulativeGPA returns null when the active level has no scheme", () => {
  const state = multiSemesterState();
  state.educationLevel = "jc";
  assert.equal(cumulativeGPA(state), null);
});

test("cumulativeGPA does not mutate the state (active grades stay un-pooled)", () => {
  const state = multiSemesterState();
  const before = JSON.stringify(state);
  cumulativeGPA(state);
  assert.equal(JSON.stringify(state), before, "input state must be untouched");
});
