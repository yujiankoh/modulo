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
import { CITY_SCHEMES, getSavedScheme, setSavedScheme, applyScheme } from "./citySchemes.js";

const scene = document.getElementById("cityScene");
const blurb = document.getElementById("cityBlurb");

// --- colour-scheme stepper: "▶ NAME 1/6", click cycles through the schemes -------
const schemeBtn = document.getElementById("citySchemeBtn");
const schemeName = document.getElementById("cscName");
const schemeCount = document.getElementById("cscCount");
const SCHEME_ORDER = Object.keys(CITY_SCHEMES);

function updateSchemeBtn() {
  const key = getSavedScheme();
  const scheme = CITY_SCHEMES[key];
  schemeName.textContent = scheme.name.toUpperCase();
  schemeCount.textContent = `${SCHEME_ORDER.indexOf(key) + 1}/${SCHEME_ORDER.length}`;
  // Arrow + name glow in the scheme's accent (a literal palette sample, like the
  // module-colour dots — inline is correct here).
  schemeName.style.color = scheme.accent;
  schemeBtn.querySelector(".csc-arrow").style.color = scheme.accent;
  schemeBtn.setAttribute("aria-label", `Colour scheme: ${scheme.name}. Click for the next one.`);
}

schemeBtn.addEventListener("click", () => {
  const next = SCHEME_ORDER[(SCHEME_ORDER.indexOf(getSavedScheme()) + 1) % SCHEME_ORDER.length];
  setSavedScheme(next);
  applyScheme();        // live: the SVG reads var(--city-…), no re-render needed
  updateSchemeBtn();
});
updateSchemeBtn();

// Plots changed by the last reconcile ("x,y" keys) — animated on the next draw.
// Presentation memory only (like the old shownPct): never persisted.
let justChanged = [];

// Which plots differ between two cities (new building, or floors changed).
function changedPlots(before, after) {
  const old = new Map((before?.buildings || []).map((b) => [`${b.x},${b.y}`, b.floors]));
  return (after.buildings || [])
    .filter((b) => old.get(`${b.x},${b.y}`) !== b.floors)
    .map((b) => `${b.x},${b.y}`);
}

async function render() {
  // Re-assert the palette every draw — covers theme toggles (theme.js flips
  // data-theme then fires modulo:datachanged) without any coupling to theme.js.
  applyScheme();

  const s = cityState(appState.studySessions, appState.city);

  if (s.pending > 0) {
    // Earned-but-unapplied upgrades: roll them onto the grid and save. The city is
    // assigned synchronously (before any await), so a re-entrant render computing
    // pending from the updated state can never double-apply.
    const before = appState.city;
    appState.city = applyUpgrades(appState.city, s.pending, s.tier, Math.random);
    justChanged = changedPlots(before, appState.city);
    await persist(); // fires modulo:datachanged → render runs again with pending 0
    return;          // the follow-up pass does the drawing (and the pop-in)
  }

  // Draw: regenerate the whole scene from the stored city. innerHTML is safe and
  // cheap here — the scene card contains nothing but the SVG (≤ ~600 shapes at 9×9),
  // and replacing wholesale keeps the renderer stateless.
  scene.innerHTML = renderScene(appState.city, s.tier);

  // Pop in whatever the reconcile just built, staggered so a batch reads as a
  // construction sequence rather than one blink.
  justChanged.forEach((key, i) => {
    const g = scene.querySelector(`[data-plot="${key}"]`);
    if (g) {
      g.style.animationDelay = `${i * 120}ms`;
      g.classList.add("city-pop");
    }
  });
  justChanged = [];

  // The blurb adapts for a brand-new city (vague on numbers, per the standing rule).
  blurb.textContent = (appState.city.buildings || []).length === 0
    ? "Your island is waiting — finish your first study session to raise a building."
    : "Your city grows as you study — keep the sessions coming and watch it rise.";
}

window.addEventListener("modulo:datachanged", render);
render(); // initial paint
