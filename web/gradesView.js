// gradesView.js — the Grades view (#view-grades, Phase 17). The ONE impure caller of
// the pure GPA logic (logic/gpa.js): reads appState, renders the stat cards (and, from
// step 2, the per-module rows editor). Both GPAs are DERIVED on every redraw — nothing
// computed here is ever stored (derive-don't-store, same rule as the study totals).
// Redraws on modulo:datachanged, so handbook switches / edits update it automatically.

import { appState } from "./data.js";
import { schemeForLevel, computeGPA, cumulativeGPA } from "./logic/gpa.js";

const supportedWrap = document.getElementById("gradesSupported");
const unsupportedWrap = document.getElementById("gradesUnsupported");
const reasonEl = document.getElementById("gradesUnsupportedReason");
const scaleEl = document.getElementById("gradesScale");
const semGpaEl = document.getElementById("cardSemGpa");
const cumGpaEl = document.getElementById("cardCumGpa");

// Display formatting lives HERE, never in the logic/storage: 2 dp, and null → an
// em-dash ("no GPA yet" is not the same thing as a 0.00 GPA).
function formatGPA(gpa) {
  return gpa === null ? "—" : gpa.toFixed(2);
}

function render() {
  const { supported, scheme, reason } = schemeForLevel(appState.educationLevel);

  // Unsupported level (jc/secondary/primary, or no handbook yet): show the reason
  // panel instead of the calculator — the logic's message, verbatim.
  supportedWrap.style.display = supported ? "" : "none";
  unsupportedWrap.style.display = supported ? "none" : "";
  if (!supported) {
    reasonEl.textContent = reason;
    scaleEl.textContent = "";
    return;
  }

  // Say WHICH scale (SMU-limitation decision): "5.0 scale" / "4.0 scale".
  scaleEl.textContent = `${scheme.maxPoints.toFixed(1)} scale`;
  semGpaEl.textContent = formatGPA(computeGPA(appState.grades, scheme).gpa);
  cumGpaEl.textContent = formatGPA(cumulativeGPA(appState).gpa);
}

window.addEventListener("modulo:datachanged", render);
render();
