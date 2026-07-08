// tests/growth.test.js — UNIT tests for the study-city growth logic
// (web/logic/growth.js). Pure functions → no DOM needed.

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  GROWTH_STAGES, totalStudyMins, growthState, claimUpgrade,
} from "../web/logic/growth.js";

// Shorthand: a sessions array totalling the given minutes (split across two sessions
// so the tests always exercise real summing, not a single-element pass-through).
function sessions(mins) {
  if (mins === 0) return [];
  const first = Math.min(mins, 1);
  return [{ durationMins: first }, { durationMins: mins - first }];
}

test("table sanity: starts at row 0 / 0 mins, thresholds strictly increase, capped at stage 3", () => {
  assert.deepEqual(GROWTH_STAGES[0], { stage: 0, level: 0, name: "Empty coastline", minMins: 0 });
  for (let i = 1; i < GROWTH_STAGES.length; i++) {
    assert.ok(
      GROWTH_STAGES[i].minMins > GROWTH_STAGES[i - 1].minMins,
      `row ${i} threshold must be greater than row ${i - 1}'s`
    );
  }
  // The MS3 cap (Option A): the top row is Art Deco shophouses at 3300 mins (55h).
  // Appending stages 4–6 later is a deliberate two-platform change — update this too.
  const top = GROWTH_STAGES[GROWTH_STAGES.length - 1];
  assert.equal(top.stage, 3);
  assert.equal(top.minMins, 3300);
});

test("totalStudyMins sums durationMins and survives missing/garbage records", () => {
  assert.equal(totalStudyMins(undefined), 0);
  assert.equal(totalStudyMins([]), 0);
  assert.equal(totalStudyMins([{ durationMins: 25 }, { durationMins: 5 }]), 30);
  // Pre-Phase-10-polish records: missing, non-numeric, or null durations count as 0.
  assert.equal(
    totalStudyMins([{ durationMins: 10 }, {}, { durationMins: "45" }, { durationMins: NaN }, null]),
    10
  );
});

test("no study yet → empty coastline, nothing to upgrade, 0% toward boat houses", () => {
  const s = growthState([], 0);
  assert.equal(s.earnedIndex, 0);
  assert.equal(s.builtIndex, 0);
  assert.equal(s.built.name, "Empty coastline");
  assert.equal(s.totalMins, 0);
  assert.equal(s.nextThresholdMins, 30);
  assert.equal(s.progressPct, 0);
  assert.equal(s.canUpgrade, false);
  assert.equal(s.maxed, false);
});

test("the >= earn rule: 29 mins is still coastline, exactly 30 earns boat houses", () => {
  assert.equal(growthState(sessions(29), 0).earnedIndex, 0);
  const s = growthState(sessions(30), 0);
  assert.equal(s.earnedIndex, 1);
  assert.equal(s.earned.name, "Boat houses");
  assert.equal(s.canUpgrade, true);        // earned but not built → button appears
  assert.equal(s.builtIndex, 0);           // the scene still shows the coastline
});

test("progressPct is floored progress within the EARNED band", () => {
  // 45 mins: earned row 1 (30) → next 90. (45-30)/(90-30) = 25%.
  const s = growthState(sessions(45), 1);
  assert.equal(s.earnedIndex, 1);
  assert.equal(s.nextThresholdMins, 90);
  assert.equal(s.progressPct, 25);
  // 89 mins: 59/60 = 98.3% → floors to 98, never a premature 100.
  assert.equal(growthState(sessions(89), 1).progressPct, 98);
});

test("builtIndex clamps: garbage → 0, above earned → earned", () => {
  // Stored cityLevel says 5 but only 30 mins studied → clamp to earned (1).
  const high = growthState(sessions(30), 5);
  assert.equal(high.builtIndex, 1);
  assert.equal(high.canUpgrade, false);    // clamped = fully built, nothing pending
  // Negative or non-integer stored values → 0.
  assert.equal(growthState(sessions(30), -3).builtIndex, 0);
  assert.equal(growthState(sessions(30), "2").builtIndex, 0);
  assert.equal(growthState(sessions(30), undefined).builtIndex, 0);
});

test("top row: maxed, no next threshold, progress reads 100", () => {
  const s = growthState(sessions(3300), 11);
  assert.equal(s.earnedIndex, GROWTH_STAGES.length - 1);
  assert.equal(s.earned.name, "Art Deco shophouses");
  assert.equal(s.maxed, true);
  assert.equal(s.nextThresholdMins, null);
  assert.equal(s.progressPct, 100);
  assert.equal(s.canUpgrade, false);
});

test("claimUpgrade builds exactly one level and never passes earned", () => {
  assert.equal(claimUpgrade(0, 3), 1);     // three pending → still one at a time
  assert.equal(claimUpgrade(2, 3), 3);
  assert.equal(claimUpgrade(3, 3), 3);     // fully built → press is a no-op
  assert.equal(claimUpgrade(-5, 2), 1);    // garbage stored value → treated as 0
  assert.equal(claimUpgrade("x", 0), 0);
});
