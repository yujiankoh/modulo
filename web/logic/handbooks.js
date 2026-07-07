// logic/handbooks.js — PURE snapshot/swap helpers for multiple handbooks (Phase 13.5).
// No DOM / appState here, so it's unit-testable in Node (tests/handbooks.test.js).
// The impure part — writing the result onto appState and persisting — stays with the
// caller (the Settings switcher UI).
//
// Model: the ACTIVE handbook lives in appState's flat fields (the Android contract);
// appState.otherHandbooks[] holds the inactive ones; appState.handbookId names the
// active one. Switching = snapshot the flat fields into otherHandbooks, pull the
// chosen entry out. studySessions are GLOBAL and deliberately NOT part of a handbook.

// The canonical list of per-handbook flat fields — the ONE place that defines what a
// handbook is. snapshotHandbook and switchHandbook both read it, so they can never
// disagree. (Phase 16 adds "grades" here and the swap carries grades automatically.)
export const HANDBOOK_FIELDS = [
  "educationLevel",
  "academicYear",
  "semester",
  "termStart",
  "termEnd",
  "breaks",
  "timetable",
  "tasks",
  "hiddenModules",
  "handbookSetup",   // per-handbook: a stored, completed handbook doesn't re-run onboarding
];

// Deep copy a JSON-safe value (all handbook data is plain JSON by construction — it
// round-trips through modulo-data.json). A stored snapshot must NOT share object
// references with the live state, or editing the active semester would silently
// corrupt the stored one.
function deepCopy(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

// Snapshot the active handbook out of a state-shaped object: { id, ...HANDBOOK_FIELDS }.
// Only handbook fields are copied — global fields (studySessions, schemaVersion,
// updatedAt) never end up inside a handbook.
export function snapshotHandbook(state) {
  const snapshot = { id: state.handbookId };
  for (const field of HANDBOOK_FIELDS) snapshot[field] = deepCopy(state[field]);
  return snapshot;
}

// A fresh, not-yet-set-up handbook (what "Start new semester" resets the flat fields to).
// Mirrors the defaults in data.js's appState initializer.
export function blankHandbook(id) {
  return {
    id,
    educationLevel: null,
    academicYear: null,
    semester: null,
    termStart: null,
    termEnd: null,
    breaks: [],
    timetable: null,
    tasks: [],
    hiddenModules: [],
    handbookSetup: false,   // false → the first-run modal opens = new-semester onboarding
  };
}

// Compute a switch to the handbook with id `targetId`. Returns
//   { flat, otherHandbooks }
// where `flat` is the target's fields (with handbookId) ready to assign onto appState,
// and `otherHandbooks` has the target removed and the current handbook's snapshot added.
// Returns null when there's nothing to do (unknown id, or already active) — the caller
// treats null as a no-op. Pure: mutates neither `state` nor its arrays.
export function switchHandbook(state, targetId) {
  if (targetId === state.handbookId) return null;              // already active
  const others = state.otherHandbooks || [];
  const target = others.find((h) => h.id === targetId);
  if (!target) return null;                                    // unknown id

  const stored = snapshotHandbook(state);                      // current → snapshot
  const { id, ...fields } = deepCopy(target);                  // target out (copied)
  return {
    flat: { handbookId: id, ...fields },
    otherHandbooks: [...others.filter((h) => h.id !== targetId), stored],
  };
}
