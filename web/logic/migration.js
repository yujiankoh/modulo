// logic/migration.js — PURE connect-moment decision logic (Phase 21).
// No DOM / Drive / appState here, so it's unit-testable in Node
// (tests/migration.test.js). The impure caller is connectDrive() in main.js:
// it reads both sides (localStorage raw + Drive) and acts on the plan; this
// file owns the RULES: what counts as data, which of the four plans applies,
// and what evidence the conflict dialog shows.
//
// Both inputs are a trust boundary (localStorage was written by an older
// version of us; Drive possibly by Android) — nothing here ever throws on a
// weird shape. Junk in → the SAFE answer out.

import { formatHeaderLabel } from "./academicYear.js";

// ---------- what counts as data ----------

// "Meaningful" = the user actually did something: finished handbook onboarding,
// added a task, or has a timetable. An untouched default file must NOT trigger
// a migration upload or a conflict dialog — hence this test, not "file exists".
//
// Pre-Phase-13 files have no handbookSetup field at all; loadInitialData
// migrates that as "a chosen educationLevel means onboarding happened"
// (data.js). Same rule here, so a side is judged exactly as loadInitialData
// would treat it after defaults — without this, an old-but-real Drive file
// could be judged empty and overwritten.
export function hasMeaningfulData(data) {
  if (!data || typeof data !== "object") return false;
  const setup = data.handbookSetup === undefined ? !!data.educationLevel : !!data.handbookSetup;
  const hasTasks = Array.isArray(data.tasks) && data.tasks.length > 0;
  return setup || hasTasks || !!data.timetable;
}

// ---------- the decision matrix ----------

// Which of the four connect-moment plans applies. Runs only when the device
// was NOT already in drive mode (main.js checks that — a returning drive
// device skips straight to loading).
//
//   local  drive  → plan
//   none   none   → "fresh"         proceed empty → first-run handbook modal
//   none   exists → "use-drive"     load Drive (today's behaviour)
//   exists none   → "upload-local"  copy this device's data up, keep using it
//   exists exists → "ask"           conflict dialog — the user picks a side
//
// "none"/"exists" mean MEANINGFUL (above) on BOTH sides: a Drive file holding
// only untouched defaults counts as none, so it's safely replaced instead of
// raising a dialog about nothing.
export function migrationPlan(localData, driveData) {
  const local = hasMeaningfulData(localData);
  const drive = hasMeaningfulData(driveData);
  if (!local) return drive ? "use-drive" : "fresh";
  return drive ? "ask" : "upload-local";
}

// ---------- evidence for the conflict dialog ----------

// "18 Jul 2026, 22:14" — formatted BY HAND, not toLocaleString(), so the
// output (and the tests) can't vary with the machine's locale. Local timezone
// on purpose: "when was this saved" should read in the user's own clock.
// Trap guarded below: new Date(null) is NOT invalid in JS — it's the 1970
// epoch — so junk must be rejected BEFORE constructing the Date, or a missing
// timestamp would show as "1 Jan 1970, 07:30".
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
export function formatUpdatedAt(iso) {
  if (typeof iso !== "string" || iso === "") return "Unknown";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "Unknown";
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}, ${hh}:${mm}`;
}

// One side's evidence card in the conflict dialog: when it was saved, how many
// tasks it holds, and which handbook it's on (same label format as the sidebar
// header — formatHeaderLabel is the one source of truth; "" when the handbook
// fields aren't set, and the dialog just omits the line).
export function dataSummary(data) {
  const d = data && typeof data === "object" ? data : {};
  return {
    updatedAt: formatUpdatedAt(d.updatedAt),
    taskCount: Array.isArray(d.tasks) ? d.tasks.length : 0,
    handbookLabel: formatHeaderLabel(d.educationLevel, d.academicYear, d.semester),
  };
}

// ---------- the extra Replace-Drive warning ----------

// True only when BOTH sides carry a parseable updatedAt and Drive's is
// strictly newer — that's the "you're about to replace a MORE RECENT save"
// case that earns an extra warning line in the confirm. Date.parse (not
// string comparison) because the two sides' ISO strings may differ in
// precision (Android vs web). Any missing/junk timestamp → false: never
// build a warning on garbage.
export function driveIsNewer(localData, driveData) {
  const local = Date.parse(localData?.updatedAt);
  const drive = Date.parse(driveData?.updatedAt);
  return Number.isFinite(local) && Number.isFinite(drive) && drive > local;
}
