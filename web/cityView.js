// cityView.js — Phase 15: the Study City section INSIDE the Study Session view
// (#view-study — one tab, decided 2026-07-08). Renders the growth status (built state
// name, progress bar, "until next upgrade") and owns the Upgrade button — the ONE
// impure caller of the pure growth logic: a press persists a new cityLevel.
// Everything shown is derived on each redraw (only cityLevel is stored); redraws on
// modulo:datachanged like every other view. The scene itself lands in steps 4–5.

import { appState, persist } from "./data.js";
import { growthState, claimUpgrade } from "./logic/growth.js";

const stateName = document.getElementById("cityStateName");
const upgradeBtn = document.getElementById("cityUpgradeBtn");
const upgradeLabel = document.getElementById("cityUpgradeLabel");
const progressFill = document.getElementById("cityProgressFill");
const progressPct = document.getElementById("cityProgressPct");
const nextText = document.getElementById("cityNextText");

// The bar width currently on screen — lets render() tell "bar got spent" (new value
// lower → restart the fill from 0) apart from ordinary forward progress.
let shownPct = 0;

function render() {
  const s = growthState(appState.studySessions, appState.cityLevel);

  // Header: the BUILT state (what the scene shows), e.g. "Boat houses".
  stateName.textContent = s.built.name;

  // The Upgrade button: muted + disabled until an upgrade is earned. Multiple
  // pending (studied while away) → the label shows how many presses are queued.
  const pending = s.earnedIndex - s.builtIndex;
  upgradeBtn.disabled = !s.canUpgrade;
  upgradeBtn.classList.toggle("is-ready", s.canUpgrade);
  upgradeBtn.title = s.canUpgrade ? "" : "Keep studying to earn an upgrade";
  upgradeLabel.textContent = pending > 1 ? `Upgrade (${pending})` : "Upgrade";

  // The EXP bar + one hint line. The bar is BUILT-relative: a pending upgrade shows
  // a FULL bar (each hammer press visually "spends" one), and the earned-band
  // progressPct applies only once fully built (the bands coincide then). Deliberately
  // NO absolute time-to-next-upgrade anywhere (decided 2026-07-08): the user
  // shouldn't grind toward a known number — the bar filling is the whole signal.
  const barPct = s.canUpgrade ? 100 : s.progressPct;
  if (barPct < shownPct) {
    // The bar just got SPENT (final press) — restart from empty instead of shrinking:
    // snap to 0 with the transition off, force a reflow so the snap is committed,
    // then re-enable the transition and let it fill 0 → barPct.
    progressFill.style.transition = "none";
    progressFill.style.width = "0%";
    progressFill.offsetWidth; // reading layout flushes the pending style change
    progressFill.style.transition = "";
  }
  progressFill.style.width = `${barPct}%`;
  progressPct.textContent = `${barPct}%`;
  shownPct = barPct;
  if (s.maxed) {
    nextText.textContent = s.canUpgrade
      ? "All eras earned — press the hammer to finish building!"
      : "Your city is complete. New eras are coming in a future update!";
  } else {
    nextText.textContent = s.canUpgrade
      ? "Upgrade ready — press the hammer to build it!"
      : "Earn EXP as you study — fill the bar to unlock your next upgrade.";
  }
}

// The press: compute the new built level (one step, never past earned), store it,
// persist ONCE. persist() fires modulo:datachanged, which re-renders this view
// (button count/visibility included) — no manual redraw needed.
upgradeBtn.addEventListener("click", async () => {
  const s = growthState(appState.studySessions, appState.cityLevel);
  if (!s.canUpgrade) return; // stale click (e.g. double-click mid-persist)
  appState.cityLevel = claimUpgrade(s.builtIndex, s.earnedIndex);
  await persist();
});

window.addEventListener("modulo:datachanged", render);
render(); // initial paint (empty coastline until data loads)
