// logic/gpa.js — PURE grade-calculator logic (Phase 16).
// No DOM / appState here, so it's unit-testable in Node (tests/gpa.test.js).
// The impure part — reading appState.grades and rendering the numbers — is the
// Phase 17 UI's job. NOTHING computed here is ever stored (derive-don't-store,
// same rule as the study totals): GPAs are recomputed from the raw rows on every
// redraw, so they can never drift out of sync with the data.
//
// Model: a SCHEME is one grading system as plain data — a grade→points map plus
// the grades that are valid but never counted (S/U etc). computeGPA is ONE
// generic credit-weighted average; every per-school difference lives in the
// scheme objects, never in the math.

// The two supported schemes. `id` is the combining key for the cumulative GPA:
// handbooks whose levels map to the SAME scheme id average together (decided
// with Ling Song 2026-07-07); different ids never mix — a poly 4.0 and an NUS
// 5.0 are different yardsticks even though both are numbers.
export const SCHEMES = {
  nus5: {
    id: "nus5",
    name: "NUS 5.0 GPA",
    maxPoints: 5.0,
    points: {
      "A+": 5.0, "A": 5.0, "A-": 4.5,
      "B+": 4.0, "B": 3.5, "B-": 3.0,
      "C+": 2.5, "C": 2.0,
      "D+": 1.5, "D": 1.0,
      "F": 0.0,
    },
    // Valid grades that DON'T enter the average: S/U (S/U'd modules) and CS/CU
    // (pass/fail-only modules). They appear on the transcript but contribute to
    // neither the numerator nor the denominator.
    excluded: ["S", "U", "CS", "CU"],
  },
  poly4: {
    id: "poly4",
    name: "Polytechnic 4.0 GPA",
    maxPoints: 4.0,
    // SP's published table — the five polys share this core. DIST (distinction)
    // caps at the same 4.0 as A.
    points: {
      "DIST": 4.0, "A": 4.0,
      "B+": 3.5, "B": 3.0,
      "C+": 2.5, "C": 2.0,
      "D+": 1.5, "D": 1.0,
      "F": 0.0,
    },
    excluded: ["P"],   // pass/fail modules
  },
};

// Which education level uses which scheme. JC (rank points) and secondary
// (L1R5) are AGGREGATE systems, not credit-weighted averages — they don't fit
// computeGPA and stay unsupported stubs until they're built as their own thing.
const LEVEL_TO_SCHEME = {
  university: "nus5",
  polytechnic: "poly4",
};

const UNSUPPORTED_REASONS = {
  jc: "JC rank points aren't supported yet.",
  secondary: "Secondary school L1R5 isn't supported yet.",
  primary: "Primary school grading isn't supported yet.",
};

// Map a handbook's education level to its scheme.
// → { supported: true, scheme } or { supported: false, reason } — one shape the
// UI can branch on. A null/unknown level (fresh handbook mid-onboarding) is
// just another unsupported case, not an error.
export function schemeForLevel(level) {
  const schemeId = LEVEL_TO_SCHEME[level];
  if (schemeId) return { supported: true, scheme: SCHEMES[schemeId] };
  return {
    supported: false,
    reason: UNSUPPORTED_REASONS[level] || "No grading scheme for this education level yet.",
  };
}

// Credit-weighted GPA over grade rows [{ credits, grade }].
// → { gpa, gradedCredits, gradedCount, excludedCount, skippedCount }
//   gpa is null (NOT 0) when nothing counted — 0.0 is a real GPA, an empty
//   list isn't. Full float precision; the UI rounds for display.
//
// Rows come from modulo-data.json, which Android also writes — a trust
// boundary. So this never throws: excluded grades (S/U/P…) are counted in
// excludedCount; anything unusable (unknown grade, missing grade, credits not
// a finite number > 0) is counted in skippedCount and left out. Grades are
// normalised (trim + uppercase) so "a+" from another client still counts.
export function computeGPA(entries, scheme) {
  const result = { gpa: null, gradedCredits: 0, gradedCount: 0, excludedCount: 0, skippedCount: 0 };
  if (!Array.isArray(entries) || !scheme || !scheme.points) return result;

  let weightedPoints = 0;
  for (const entry of entries) {
    const grade = typeof entry?.grade === "string" ? entry.grade.trim().toUpperCase() : null;

    // Excluded first: an S/U'd module is excluded no matter what its credits say.
    if (grade !== null && scheme.excluded.includes(grade)) {
      result.excludedCount += 1;
      continue;
    }

    const credits = entry?.credits;
    const creditsValid = typeof credits === "number" && Number.isFinite(credits) && credits > 0;
    const hasPoints = grade !== null && Object.hasOwn(scheme.points, grade);
    if (!hasPoints || !creditsValid) {
      result.skippedCount += 1;
      continue;
    }

    weightedPoints += scheme.points[grade] * credits;
    result.gradedCredits += credits;
    result.gradedCount += 1;
  }

  if (result.gradedCredits > 0) result.gpa = weightedPoints / result.gradedCredits;
  return result;
}

// Cumulative GPA across the whole duration of study (Ling Song's requirement):
// the ACTIVE handbook's grades pooled with every stored handbook in
// otherHandbooks whose level maps to the SAME scheme — then ONE computeGPA over
// the pool (a true credit-weighted average across semesters, not an average of
// per-semester GPAs, which would weight semesters wrongly).
//
// Takes a state-shaped object (like switchHandbook does) — pure, so tests hand
// it a literal and the UI passes appState. Returns null when the active level
// has no scheme (the UI already shows schemeForLevel's reason). Stored
// handbooks from before Phase 16 may have no `grades` key at all — treated as
// empty, never a crash.
export function cumulativeGPA(state) {
  const active = schemeForLevel(state?.educationLevel);
  if (!active.supported) return null;

  const pooled = [...(state.grades || [])];
  for (const handbook of state.otherHandbooks || []) {
    const other = schemeForLevel(handbook?.educationLevel);
    if (other.supported && other.scheme.id === active.scheme.id) {
      pooled.push(...(handbook.grades || []));
    }
  }
  return computeGPA(pooled, active.scheme);
}
