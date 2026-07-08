// logic/growth.js — PURE growth logic for the study city (Phase 14).
// No DOM / appState here, so it's unit-testable in Node (tests/growth.test.js).
// The impure part — reading appState.studySessions/cityLevel and persisting a claimed
// upgrade — stays with the caller (the Phase 15 #city view).
//
// Model: cumulative study minutes EARN city levels (fully derived, never stored);
// the user presses Upgrade to BUILD them one at a time (stored as the single flat
// field appState.cityLevel — global like studySessions, NOT per-handbook).
// This table + these rules are a shared contract with the Android app
// (docs/study-city-growth.md) — changing them is a two-platform decision.

// The growth table: Singapore's development history, capped at stage 3 for MS3
// (stages 4–6 are appended later, web + Android together). Thresholds are in MINUTES
// (the unit studySessions store) — a row is EARNED when totalMins >= minMins.
export const GROWTH_STAGES = [
  { stage: 0, level: 0, name: "Empty coastline",            minMins: 0 },
  { stage: 1, level: 1, name: "Boat houses",                minMins: 30 },
  { stage: 1, level: 2, name: "Stilt houses",               minMins: 90 },
  { stage: 2, level: 1, name: "Kampung houses",             minMins: 180 },
  { stage: 2, level: 2, name: "More kampung houses",        minMins: 360 },
  { stage: 2, level: 3, name: "Dirt road",                  minMins: 600 },
  { stage: 3, level: 1, name: "Early shophouses",           minMins: 900 },
  { stage: 3, level: 2, name: "Better road + rickshaw",     minMins: 1260 },
  { stage: 3, level: 3, name: "Traditional shophouses I",   minMins: 1680 },
  { stage: 3, level: 4, name: "Traditional shophouses II",  minMins: 2160 },
  { stage: 3, level: 5, name: "Traditional shophouses III", minMins: 2700 },
  { stage: 3, level: 6, name: "Art Deco shophouses",        minMins: 3300 },
];

// All-time study minutes: the plain sum of durationMins. The ONE definition of
// "how much have I studied, ever". Defensive: a session with a missing or
// non-numeric durationMins counts as 0 — one bad record must not NaN-freeze the city.
export function totalStudyMins(sessions) {
  return (sessions || []).reduce(
    (sum, s) => sum + (Number.isFinite(s?.durationMins) ? s.durationMins : 0),
    0
  );
}

// The full city state for a given sessions array + stored built level (cityLevel).
//   earnedIndex  — highest row with minMins <= totalMins (derived entitlement)
//   builtIndex   — the stored level, CLAMPED to [0, earnedIndex] (a bad or too-high
//                  stored value can never render an unearned city)
//   earned/built — the corresponding GROWTH_STAGES rows; the scene renders `built`,
//                  the progress numbers describe `earned`
//   canUpgrade   — an earned-but-unbuilt level is waiting (drives the Upgrade button)
//   nextThresholdMins / progressPct — hour progress from the earned row toward the
//                  next one (null / 100 once the top row is earned). progressPct is
//                  FLOORED, so it only reads 100 when the next level is actually
//                  banked (99 max mid-band) — part of the Android contract.
export function growthState(sessions, builtIndex) {
  const totalMins = totalStudyMins(sessions);

  let earnedIndex = 0;
  for (let i = 0; i < GROWTH_STAGES.length; i++) {
    if (totalMins >= GROWTH_STAGES[i].minMins) earnedIndex = i;
  }

  const stored = Number.isInteger(builtIndex) ? builtIndex : 0;
  const built = Math.min(Math.max(stored, 0), earnedIndex);

  const maxed = earnedIndex === GROWTH_STAGES.length - 1;
  const nextThresholdMins = maxed ? null : GROWTH_STAGES[earnedIndex + 1].minMins;

  let progressPct = 100;
  if (!maxed) {
    const bandStart = GROWTH_STAGES[earnedIndex].minMins;
    progressPct = Math.floor(((totalMins - bandStart) / (nextThresholdMins - bandStart)) * 100);
  }

  return {
    earnedIndex,
    builtIndex: built,
    earned: GROWTH_STAGES[earnedIndex],
    built: GROWTH_STAGES[built],
    totalMins,
    nextThresholdMins,
    progressPct,
    canUpgrade: built < earnedIndex,
    maxed,
  };
}

// One Upgrade press builds exactly ONE level, never past what's earned.
// The caller persists the result as appState.cityLevel (one persist() per press).
export function claimUpgrade(builtIndex, earnedIndex) {
  const built = Number.isInteger(builtIndex) ? Math.max(builtIndex, 0) : 0;
  return Math.min(built + 1, earnedIndex);
}
