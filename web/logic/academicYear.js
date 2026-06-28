// logic/academicYear.js — PURE helpers for the handbook's level-aware academic year.
// No DOM / appState here, so they're unit-testable in Node (see tests/academicYear.test.js)
// and shared by handbook.js (modal + Settings summary) and sidebar.js (header label).

// Tertiary levels (uni/poly) span TWO calendar years -> "25/26". School levels
// (primary/secondary/jc) are a SINGLE calendar year -> "2026".
export const TERTIARY = ["university", "poly"];
export function isTertiary(level) { return TERTIARY.includes(level); }

// Build the STORED academicYear string from a start year + level.
//   tertiary 2025 -> "25/26"      school 2026 -> "2026"
export function formatAcademicYear(startYear, level) {
  if (!startYear) return null;
  if (isTertiary(level)) {
    const a = String(startYear).slice(-2);        // 2025 -> "25"
    const b = String(startYear + 1).slice(-2);    // 2026 -> "26"
    return `${a}/${b}`;
  }
  return String(startYear);                        // "2026"
}

// The reverse: from a stored academicYear back to the start-year NUMBER for the input.
//   "25/26" -> 2025      "2026" -> 2026
export function parseStartYear(academicYear, level) {
  if (!academicYear) return null;
  if (isTertiary(level)) return 2000 + parseInt(academicYear.split("/")[0], 10);
  return parseInt(academicYear, 10);
}

// The label shown in the sidebar header + Settings: "AY25/26 · S1" (tertiary) or
// "2026 · Sem 1" (school). "" when academicYear/semester aren't both set yet.
export function formatHeaderLabel(level, academicYear, semester) {
  if (!academicYear || !semester) return "";
  return isTertiary(level)
    ? `AY${academicYear} · S${semester}`
    : `${academicYear} · Sem ${semester}`;
}
