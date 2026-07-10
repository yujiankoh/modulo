// cityView.js — the Study City section inside the Study Session view (#view-study).
// Generative-grid design (2026-07-08): the city grows AUTOMATICALLY. This module is
// the ONE impure caller of the pure city logic — on every redraw it RECONCILES:
// if study minutes have earned upgrades that aren't applied to the stored grid yet,
// it rolls their placements (Math.random — outcomes get persisted, so they never
// need re-deriving), writes appState.city, and persists ONCE. The persist re-fires
// modulo:datachanged; the follow-up render sees pending 0 and just draws. That
// pending-0 check is the loop guard — reconcile can never persist twice in a row.

import { appState, persist } from "./data.js";
import { cityState, applyUpgrades } from "./logic/growth.js";
import { renderScene } from "./cityScene.js";

const scene = document.getElementById("cityScene");

async function render() {
  const s = cityState(appState.studySessions, appState.city);

  if (s.pending > 0) {
    // Earned-but-unapplied upgrades: roll them onto the grid and save. The city is
    // assigned synchronously (before any await), so a re-entrant render computing
    // pending from the updated state can never double-apply.
    appState.city = applyUpgrades(appState.city, s.pending, s.tier, Math.random);
    await persist(); // fires modulo:datachanged → render runs again with pending 0
    return;          // the follow-up pass does the drawing
  }

  // Draw: regenerate the whole scene from the stored city. innerHTML is safe and
  // cheap here — the scene card contains nothing but the SVG (≤ ~600 shapes at 9×9),
  // and replacing wholesale keeps the renderer stateless.
  scene.innerHTML = renderScene(appState.city, s.tier);
}

window.addEventListener("modulo:datachanged", render);
render(); // initial paint
