// tests/migration.test.js — UNIT tests for the connect-moment decision logic
// (web/logic/migration.js). Pure functions → no DOM/Drive needed. Data inputs
// are plain literals shaped like modulo-data.json (or junk — trust boundary).

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  hasMeaningfulData,
  migrationPlan,
  formatUpdatedAt,
  dataSummary,
  driveIsNewer,
} from "../web/logic/migration.js";

// An untouched default file — what data.js starts with before the user does
// anything. Must count as NOT meaningful everywhere.
function defaults() {
  return {
    schemaVersion: 2, educationLevel: null, academicYear: null, semester: null,
    handbookSetup: false, handbookId: "u-u-i-d", termStart: null, termEnd: null,
    breaks: [], tasks: [], grades: [], studySessions: [], city: { buildings: [] },
    hiddenModules: [], otherHandbooks: [], timetable: null, updatedAt: null,
  };
}

// ---------- hasMeaningfulData ----------

test("hasMeaningfulData: untouched defaults and junk are NOT meaningful", () => {
  assert.equal(hasMeaningfulData(defaults()), false);
  assert.equal(hasMeaningfulData(null), false);
  assert.equal(hasMeaningfulData(undefined), false);
  assert.equal(hasMeaningfulData("soon"), false);
  assert.equal(hasMeaningfulData({}), false);
  // tasks present but EMPTY still doesn't count.
  assert.equal(hasMeaningfulData({ ...defaults(), tasks: [] }), false);
});

test("hasMeaningfulData: any one of handbookSetup / tasks / timetable counts", () => {
  assert.equal(hasMeaningfulData({ ...defaults(), handbookSetup: true }), true);
  assert.equal(hasMeaningfulData({ ...defaults(), tasks: [{ id: 1 }] }), true);
  assert.equal(hasMeaningfulData({ ...defaults(), timetable: { modules: [] } }), true);
});

test("hasMeaningfulData: pre-Phase-13 files (no handbookSetup field) judged by educationLevel", () => {
  // Same migration rule as loadInitialData — an old-but-real file must not be
  // judged empty (that misread would let upload-local overwrite it).
  assert.equal(hasMeaningfulData({ schemaVersion: 2, educationLevel: "university", tasks: [] }), true);
  assert.equal(hasMeaningfulData({ schemaVersion: 2, educationLevel: null, tasks: [] }), false);
});

// ---------- migrationPlan ----------

test("migrationPlan: all four matrix cells", () => {
  const used = { ...defaults(), handbookSetup: true, tasks: [{ id: 1 }] };
  assert.equal(migrationPlan(null, null), "fresh");
  assert.equal(migrationPlan(null, used), "use-drive");
  assert.equal(migrationPlan(used, null), "upload-local");
  assert.equal(migrationPlan(used, used), "ask");
});

test("migrationPlan: a side holding only untouched defaults counts as none", () => {
  const used = { ...defaults(), handbookSetup: true };
  // Drive file EXISTS but is meaningless → replaced without a dialog…
  assert.equal(migrationPlan(used, defaults()), "upload-local");
  // …and a meaningless local side never triggers an upload or a dialog.
  assert.equal(migrationPlan(defaults(), used), "use-drive");
  assert.equal(migrationPlan(defaults(), defaults()), "fresh");
});

// ---------- formatUpdatedAt ----------

test("formatUpdatedAt: worked example, in the machine's own timezone", () => {
  // Build the ISO from LOCAL components, so the expected string is the same
  // whatever timezone the test machine is in.
  const iso = new Date(2026, 6, 18, 22, 14).toISOString(); // 18 Jul 2026 22:14 local
  assert.equal(formatUpdatedAt(iso), "18 Jul 2026, 22:14");
  // Single-digit hour/minute pad to two ("09:05", not "9:5").
  const early = new Date(2026, 0, 3, 9, 5).toISOString();
  assert.equal(formatUpdatedAt(early), "3 Jan 2026, 09:05");
});

test("formatUpdatedAt: junk → 'Unknown', never the 1970 epoch", () => {
  // new Date(null) is the epoch, NOT invalid — the guard must catch these
  // BEFORE constructing the Date.
  assert.equal(formatUpdatedAt(null), "Unknown");
  assert.equal(formatUpdatedAt(undefined), "Unknown");
  assert.equal(formatUpdatedAt(""), "Unknown");
  assert.equal(formatUpdatedAt("not a date"), "Unknown");
  assert.equal(formatUpdatedAt(1234567890), "Unknown");
});

// ---------- dataSummary ----------

test("dataSummary: evidence for one side of the conflict dialog", () => {
  const iso = new Date(2026, 6, 18, 22, 14).toISOString();
  const s = dataSummary({
    ...defaults(),
    updatedAt: iso,
    tasks: [{ id: 1 }, { id: 2 }, { id: 3 }],
    educationLevel: "university", academicYear: "25/26", semester: 1,
  });
  assert.deepEqual(s, {
    updatedAt: "18 Jul 2026, 22:14",
    taskCount: 3,
    handbookLabel: "AY25/26 · S1", // same formatter as the sidebar header
  });
});

test("dataSummary: junk input never throws — safe blanks out", () => {
  assert.deepEqual(dataSummary(null), { updatedAt: "Unknown", taskCount: 0, handbookLabel: "" });
  assert.deepEqual(dataSummary({ tasks: "soon" }).taskCount, 0);
  // Handbook fields not set → empty label; the dialog just omits the line.
  assert.equal(dataSummary(defaults()).handbookLabel, "");
});

// ---------- driveIsNewer ----------

test("driveIsNewer: strictly newer Drive timestamp → true, else false", () => {
  const older = { updatedAt: "2026-07-18T10:00:00.000Z" };
  const newer = { updatedAt: "2026-07-19T10:00:00.000Z" };
  assert.equal(driveIsNewer(older, newer), true);
  assert.equal(driveIsNewer(newer, older), false);
  assert.equal(driveIsNewer(older, older), false); // equal is NOT newer
  // Differing precision still compares correctly (Date.parse, not strings).
  assert.equal(driveIsNewer({ updatedAt: "2026-07-18T10:00:00Z" }, newer), true);
});

test("driveIsNewer: any missing/junk timestamp → false (no warning built on garbage)", () => {
  const real = { updatedAt: "2026-07-19T10:00:00.000Z" };
  assert.equal(driveIsNewer(null, real), false);
  assert.equal(driveIsNewer(real, null), false);
  assert.equal(driveIsNewer({}, real), false);
  assert.equal(driveIsNewer({ updatedAt: "soon" }, real), false);
  assert.equal(driveIsNewer(defaults(), real), false); // updatedAt: null
});
