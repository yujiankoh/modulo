// tests/growth.test.js — UNIT tests for the generative study-city logic
// (web/logic/growth.js, redesign of 2026-07-08). Pure functions → no DOM needed.
// Randomness is tamed by INJECTING scripted rng functions — every test is
// deterministic and doubles as documentation of the pick algorithm.

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  totalStudyMins, earnedUpgrades, GRID_TIERS, gridTier, plotWeight,
  applyUpgrades, appliedUpgrades, cityState,
} from "../web/logic/growth.js";

// An rng that returns the given values in order (then repeats the last one).
function scriptedRng(...values) {
  let i = 0;
  return () => values[Math.min(i++, values.length - 1)];
}

const TIER5 = GRID_TIERS[0]; // { size: 5, floorCap: 5 }

test("totalStudyMins sums durationMins and survives missing/garbage records", () => {
  assert.equal(totalStudyMins(undefined), 0);
  assert.equal(totalStudyMins([]), 0);
  assert.equal(totalStudyMins([{ durationMins: 25 }, { durationMins: 5 }]), 30);
  assert.equal(
    totalStudyMins([{ durationMins: 10 }, {}, { durationMins: "45" }, { durationMins: NaN }, null]),
    10
  );
});

test("earnedUpgrades inverts the n²+9n pacing exactly at boundaries", () => {
  // Gaps are 10, 12, 14, … so cumulative thresholds are 10, 22, 36, 52, 70.
  assert.equal(earnedUpgrades(0), 0);
  assert.equal(earnedUpgrades(9), 0);
  assert.equal(earnedUpgrades(10), 1);    // >= at the boundary earns
  assert.equal(earnedUpgrades(21), 1);
  assert.equal(earnedUpgrades(22), 2);
  assert.equal(earnedUpgrades(36), 3);
  assert.equal(earnedUpgrades(51), 3);
  assert.equal(earnedUpgrades(52), 4);
  // 20 h = 1200 mins: 30² + 9·30 = 1170 ≤ 1200 < 31² + 9·31 = 1240.
  assert.equal(earnedUpgrades(1200), 30);
  assert.equal(earnedUpgrades(NaN), 0);
});

test("gridTier switches land size exactly at 20h and 100h", () => {
  assert.equal(gridTier(0).size, 5);
  assert.equal(gridTier(1199).size, 5);
  assert.equal(gridTier(1200).size, 7);   // 20 h
  assert.equal(gridTier(5999).size, 7);
  assert.equal(gridTier(6000).size, 9);   // 100 h
  // Floor cap rides the tier (the visual-tuning knob).
  assert.equal(gridTier(0).floorCap, 5);
  assert.equal(gridTier(6000).floorCap, 12);
});

test("plotWeight is centre-heavy: 9 / 4 / 1 on a 5×5, by ring", () => {
  assert.equal(plotWeight(0, 0, 5), 9);   // centre
  assert.equal(plotWeight(1, 0, 5), 4);   // inner ring
  assert.equal(plotWeight(-1, 1, 5), 4);  // ring by Chebyshev distance (max of |x|,|y|)
  assert.equal(plotWeight(2, 2, 5), 1);   // corner
  assert.equal(plotWeight(0, 0, 7), 16);  // bigger land → stronger centre pull
});

test("the founding building always lands dead centre", () => {
  const city = applyUpgrades({ buildings: [] }, 1, TIER5, scriptedRng(0.99));
  assert.deepEqual(city.buildings, [{ x: 0, y: 0, floors: 1 }]);
});

test("occupied pick grows a floor; empty pick spawns (scripted rolls)", () => {
  // After the centre exists, all 25 plots are candidates. Iteration order is
  // x=-2..2 outer, y=-2..2 inner; total weight 57 (16×1 + 8×4 + 9).
  // The centre's weight-interval is [24, 33) → rng 0.5 (roll 28.5) picks it: GROW.
  const one = applyUpgrades({ buildings: [{ x: 0, y: 0, floors: 1 }] }, 1, TIER5, scriptedRng(0.5));
  assert.deepEqual(one.buildings, [{ x: 0, y: 0, floors: 2 }]);

  // rng 0 (roll 0) picks the first candidate, the (-2,-2) corner: SPAWN.
  const two = applyUpgrades({ buildings: [{ x: 0, y: 0, floors: 1 }] }, 1, TIER5, scriptedRng(0));
  assert.equal(two.buildings.length, 2);
  assert.deepEqual(two.buildings[1], { x: -2, y: -2, floors: 1 });
});

test("capped plots are excluded from the pick", () => {
  // Centre at the tier-1 cap (5 floors). rng 0.5 would have picked it — but capped
  // plots aren't candidates, so the upgrade must land elsewhere and the centre stays.
  const city = { buildings: [{ x: 0, y: 0, floors: 5 }] };
  const next = applyUpgrades(city, 1, TIER5, scriptedRng(0.5));
  const centre = next.buildings.find((b) => b.x === 0 && b.y === 0);
  assert.equal(centre.floors, 5);
  assert.equal(appliedUpgrades(next), 6);
});

test("a fully maxed grid banks the remaining upgrades", () => {
  const full = { buildings: [] };
  for (let x = -2; x <= 2; x++)
    for (let y = -2; y <= 2; y++) full.buildings.push({ x, y, floors: 5 });
  const after = applyUpgrades(full, 10, TIER5, scriptedRng(0.5));
  assert.equal(appliedUpgrades(after), 125); // unchanged — nothing to apply onto
});

test("applyUpgrades applies count events and never mutates the input", () => {
  const input = { buildings: [{ x: 0, y: 0, floors: 1 }] };
  const before = JSON.stringify(input);
  const after = applyUpgrades(input, 5, TIER5, scriptedRng(0.5, 0.1, 0.9, 0.3, 0.7));
  assert.equal(JSON.stringify(input), before, "input city must be untouched");
  assert.equal(appliedUpgrades(after), 6); // 1 existing + 5 applied
});

test("appliedUpgrades = total floors, defensive against garbage", () => {
  assert.equal(appliedUpgrades(undefined), 0);
  assert.equal(appliedUpgrades({ buildings: [] }), 0);
  assert.equal(appliedUpgrades({ buildings: [{ floors: 3 }, { floors: 1 }, { floors: "2" }] }), 4);
});

test("cityState derives pending = earned − applied (never negative)", () => {
  // 36 mins → 3 earned; a city with 1 floor standing → 2 pending.
  const s = cityState([{ durationMins: 36 }], { buildings: [{ x: 0, y: 0, floors: 1 }] });
  assert.equal(s.earned, 3);
  assert.equal(s.applied, 1);
  assert.equal(s.pending, 2);
  assert.equal(s.tier.size, 5);
  // Bad data (more floors than earned) clamps pending to 0 rather than going negative.
  const bad = cityState([], { buildings: [{ x: 0, y: 0, floors: 7 }] });
  assert.equal(bad.pending, 0);
});
