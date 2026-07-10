// logic/growth.js — PURE logic for the generative study city (Phase 14, redesigned
// 2026-07-08: grid city, superseding the fixed 12-stage design — see the legacy
// section at the bottom, deleted in step 3).
// No DOM / appState here, so it's unit-testable in Node (tests/growth.test.js).
//
// Model: total study minutes EARN upgrade events (pacing: the (x+1)-th upgrade costs
// 2x+10 mins). Each event picks ONE plot on the grid, centre-weighted: empty plot →
// a 1-floor building SPAWNS; occupied → it GROWS a floor. Upgrades apply
// AUTOMATICALLY (no button): the view reconciles earned vs applied on redraw and
// stores the resulting grid (randomness can't be re-derived, so the grid itself is
// the ONE stored city field — appState.city).
//
// These rules are a shared contract with the Android app (docs/study-city-growth.md)
// — changing them is a two-platform decision.

// --- fuel -------------------------------------------------------------------------

// All-time study minutes: the plain sum of durationMins. Number.isFinite (not ||0)
// so a string/NaN in a record counts as 0 instead of corrupting the sum.
export function totalStudyMins(sessions) {
  return (sessions || []).reduce(
    (sum, s) => sum + (Number.isFinite(s?.durationMins) ? s.durationMins : 0),
    0
  );
}

// --- pacing -----------------------------------------------------------------------

// n upgrades need n² + 9n total minutes (sum of 10, 12, 14, …). This inverts that:
// how many upgrades has totalMins earned? sqrt gives the candidate; the while-loops
// correct any float error at exact boundaries (integer math must be exact — the
// Android app computes the same n from the same minutes).
export function earnedUpgrades(totalMins) {
  if (!Number.isFinite(totalMins) || totalMins < 10) return 0;
  let n = Math.floor((-9 + Math.sqrt(81 + 4 * totalMins)) / 2);
  while ((n + 1) * (n + 1) + 9 * (n + 1) <= totalMins) n++; // sqrt rounded low
  while (n > 0 && n * n + 9 * n > totalMins) n--;           // sqrt rounded high
  return n;
}

// --- the land ---------------------------------------------------------------------

// Land tiers: the grid grows with total study time, and taller buildings become
// possible on bigger land. floorCap is the visual-tuning knob (YJ may adjust once
// the skyline is drawn) — tune it HERE only; nothing else encodes it.
export const GRID_TIERS = [
  { minMins: 0,    size: 5, floorCap: 5 },
  { minMins: 1200, size: 7, floorCap: 8 },  // 20 h
  { minMins: 6000, size: 9, floorCap: 12 }, // 100 h
];

export function gridTier(totalMins) {
  let tier = GRID_TIERS[0];
  for (const t of GRID_TIERS) if (totalMins >= t.minMins) tier = t;
  return tier;
}

// --- the pick ---------------------------------------------------------------------

// Centre-weighting: a plot's weight is (R − d + 1)² where d is its ring distance
// from the centre (Chebyshev: max of |x|,|y| — "which square ring am I on") and
// R the grid radius. 5×5: centre 9, inner ring 4, edge 1 — the centre is picked
// 9× as often as a corner, which is what builds the skyline centre-out.
export function plotWeight(x, y, size) {
  const R = (size - 1) / 2;
  const d = Math.max(Math.abs(x), Math.abs(y));
  const w = R - d + 1;
  return w * w;
}

// --- applying upgrades ------------------------------------------------------------

// Apply `count` upgrade events to a city, returning a NEW city (input untouched).
// `tier` = gridTier(totalMins). `rng` = a () => [0,1) function, INJECTED so tests
// pass a scripted sequence (deterministic) and only the real caller passes
// Math.random. Rules per event:
//   - very first building ever → dead centre (0,0), by design (YJ's spec);
//   - otherwise weighted-pick ONE plot among those below the floor cap
//     (excluding capped plots = the "re-roll until valid" rule, done exactly);
//   - empty → spawn { floors: 1 }, occupied → floors + 1;
//   - grid fully maxed → stop early (remaining upgrades stay banked; the next
//     land expansion absorbs them).
export function applyUpgrades(city, count, tier, rng) {
  const buildings = (city?.buildings || []).map((b) => ({ ...b })); // copy, don't share
  const byKey = new Map(buildings.map((b) => [`${b.x},${b.y}`, b]));
  const R = (tier.size - 1) / 2;

  const place = (x, y) => {
    const existing = byKey.get(`${x},${y}`);
    if (existing) { existing.floors += 1; return; }
    const b = { x, y, floors: 1 };
    buildings.push(b);
    byKey.set(`${x},${y}`, b);
  };

  for (let i = 0; i < count; i++) {
    if (byKey.size === 0) { place(0, 0); continue; }  // the founding building

    // Candidates: every plot still below the cap, with its weight.
    const candidates = [];
    let totalWeight = 0;
    for (let x = -R; x <= R; x++) {
      for (let y = -R; y <= R; y++) {
        const b = byKey.get(`${x},${y}`);
        if (b && b.floors >= tier.floorCap) continue;
        const w = plotWeight(x, y, tier.size);
        candidates.push({ x, y, w });
        totalWeight += w;
      }
    }
    if (candidates.length === 0) break;               // fully maxed — bank the rest

    // Weighted roll: scale rng to the total weight, walk candidates subtracting
    // each weight — the candidate that takes the roll below 0 is picked.
    let roll = rng() * totalWeight;
    let picked = candidates[candidates.length - 1];   // guard: rng() returning ~1.0
    for (const c of candidates) {
      roll -= c.w;
      if (roll < 0) { picked = c; break; }
    }
    place(picked.x, picked.y);
  }
  return { buildings };
}

// --- derived counts ---------------------------------------------------------------

// Upgrades already applied = total floors standing (each event adds exactly 1 floor).
export function appliedUpgrades(city) {
  return (city?.buildings || []).reduce(
    (sum, b) => sum + (Number.isInteger(b?.floors) ? b.floors : 0),
    0
  );
}

// One bundle for the view: everything derived, nothing stored.
// pending > 0 means the caller should applyUpgrades + persist (reconcile-on-redraw).
export function cityState(sessions, city) {
  const totalMins = totalStudyMins(sessions);
  const earned = earnedUpgrades(totalMins);
  const applied = appliedUpgrades(city);
  return {
    totalMins,
    earned,
    applied,
    pending: Math.max(earned - applied, 0), // applied can exceed earned only on bad data
    tier: gridTier(totalMins),
  };
}

// ====================================================================================
// LEGACY — the superseded fixed-stage design. cityView.js still imports these; both
// are DELETED together in step 3. Do not build on anything below this line.
// ====================================================================================

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

export function claimUpgrade(builtIndex, earnedIndex) {
  const built = Number.isInteger(builtIndex) ? Math.max(builtIndex, 0) : 0;
  return Math.min(built + 1, earnedIndex);
}
