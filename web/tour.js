// tour.js — first-run feature walkthrough (Phase 22). A NEW user, after picking a
// storage mode, steps Next/Back through a short guide to each feature, then is handed
// off to handbook setup. Returning / already-set-up users never see it. Reopenable
// from Settings → Help. Persistence is a per-DEVICE localStorage flag (like the theme
// pref) — NO schema change, so the tour is unrelated to which handbook/account is active.

import { appState, getStorageMode } from "./data.js";
import { drawIcons } from "./icons.js"; // turn the per-step <i data-lucide> into an <svg>

const TOUR_KEY = "modulo-tour-seen"; // "1" once finished/skipped on THIS device

// The steps. Each: a Lucide `icon` (shown when there's no image), a `title`, a
// plain-language `desc`, and an optional `image`/`alt` screenshot (shown INSTEAD of the
// icon when present). Welcome + the closing "Get started" are text-only on purpose;
// the feature steps get a screenshot each (only Dashboard exists so far — add the rest
// as they're produced). Order follows the sidebar.
const STEPS = [
  { icon: "sparkles", title: "Welcome to MODULO",
    desc: "Your timetable, tasks, grades and study time — all in one place. Here's a quick tour of what you can do." },
  { icon: "layout-dashboard", image: "tour_ss/Dashboard.png", alt: "The MODULO dashboard",
    title: "Your dashboard",
    desc: "Your home base: today's classes, tasks due soon, and a card for every module — all at a glance." },
  { icon: "calendar-clock", image: "tour_ss/Timetable.png", alt: "The weekly timetable grid",
    title: "Timetable",
    desc: "Snap a photo of your school timetable and MODULO turns it into a clean weekly grid — no typing." },
  { icon: "list-checks", image: "tour_ss/Tasks.png", alt: "The tasks list",
    title: "Tasks",
    desc: "Add assignments and deadlines, filter and sort them by module, and tick them off as you go." },
  { icon: "calendar", image: "tour_ss/Calendar.png", alt: "The month calendar",
    title: "Calendar",
    desc: "See everything that's due at a glance on a monthly calendar." },
  { icon: "timer", image: "tour_ss/Study%20session.png", alt: "The study session timer and city",
    title: "Study Session",
    desc: "Run the focus timer while you study — and watch a little city grow the more hours you put in." },
  { icon: "star", image: "tour_ss/Productivity%20rating.png", alt: "Rating a study session's productivity",
    title: "Productivity rating",
    desc: "After each session, rate how focused you were from 1 to 5. Your daily average shows up on the calendar, so you can spot your most productive days." },
  { icon: "graduation-cap", image: "tour_ss/GPA.png", alt: "The grade calculator",
    title: "Grades",
    desc: "Enter your module grades and see your GPA update live, this semester and across your course." },
  { icon: "file-text", image: "tour_ss/Notes.png", alt: "The notes list",
    title: "Notes",
    desc: "Upload lecture PDFs and photos — they sync privately through your own Google Drive." },
  { icon: "book-open", title: "Let's set up your handbook",
    desc: "A handbook holds one semester — your education level, academic year and term dates. Set yours up and MODULO tailors your calendar, weeks and GPA to it." },
];

// DOM handles (all inside #tourModal, plus the Settings button).
const modal = document.getElementById("tourModal");
const iconEl = document.getElementById("tourIcon");
const imageEl = document.getElementById("tourImage");
const titleEl = document.getElementById("tourTitle");
const descEl = document.getElementById("tourDesc");
const dotsEl = document.getElementById("tourDots");
const backBtn = document.getElementById("tourBack");
const nextBtn = document.getElementById("tourNext");
const skipBtn = document.getElementById("tourSkip");

let stepIndex = 0;
let firstRun = false; // true = the auto first-run tour (hands off to handbook); false = Settings reopen

// True exactly when a new user hasn't seen the tour and isn't set up yet — i.e. the
// tour is going to show. handbook.js reads this to hold its first-run modal back until
// the tour finishes (both listen to the same modulo:datachanged).
export function tourWillShow() {
  return !localStorage.getItem(TOUR_KEY) && !appState.handbookSetup;
}

function renderStep() {
  const step = STEPS[stepIndex];
  // A screenshot (if this step has one) REPLACES the icon; otherwise show the icon.
  if (step.image) {
    imageEl.src = step.image;
    imageEl.alt = step.alt || step.title;
    imageEl.style.display = "";
    iconEl.style.display = "none";
  } else {
    // Replacing the <i> then redrawing is how a Lucide icon swaps (data-lucide → svg).
    iconEl.innerHTML = `<i data-lucide="${step.icon}"></i>`;
    iconEl.style.display = "";
    imageEl.style.display = "none";
  }
  titleEl.textContent = step.title;
  descEl.textContent = step.desc;

  // Progress dots — one per step, the current one filled.
  dotsEl.innerHTML = STEPS.map((_, i) => `<span class="tour-dot${i === stepIndex ? " is-active" : ""}"></span>`).join("");

  // Back is hidden (not removed) on the first step; Next becomes the terminal action
  // on the last — "Set up my handbook" on first run, "Done" on a Settings reopen.
  backBtn.classList.toggle("is-hidden", stepIndex === 0);
  const last = stepIndex === STEPS.length - 1;
  nextBtn.textContent = last ? (firstRun ? "Set up my handbook" : "Done") : "Next";

  drawIcons(); // render the icon we just injected
}

// Open the tour. asFirstRun=true = the auto onboarding (hands off to handbook, Skip
// shown); false = a Settings reopen (review mode — no handoff, Skip hidden).
function openTour(asFirstRun) {
  firstRun = asFirstRun;
  stepIndex = 0;
  skipBtn.style.display = asFirstRun ? "" : "none";
  renderStep();
  modal.style.display = "flex"; // .tcal-popup centres the card
}

// Finish the FIRST-RUN tour: remember it (so it never shows again), close, then fire
// the same event the handbook's first-run listener waits for. tourWillShow() is now
// false (flag set), so the handbook opens instead of deferring — the handoff.
function finishTour() {
  localStorage.setItem(TOUR_KEY, "1");
  modal.style.display = "none";
  window.dispatchEvent(new Event("modulo:datachanged"));
}

// Close a Settings reopen (review mode): the user is already set up, so there's no
// handoff — just put it away.
function closeReview() {
  modal.style.display = "none";
}

// Next: step forward, or take the terminal action on the last step.
nextBtn.addEventListener("click", () => {
  if (stepIndex < STEPS.length - 1) { stepIndex++; renderStep(); return; }
  firstRun ? finishTour() : closeReview();
});
backBtn.addEventListener("click", () => {
  if (stepIndex > 0) { stepIndex--; renderStep(); }
});
// Skip (first-run only): a way straight to the handbook. Same as finishing.
skipBtn.addEventListener("click", finishTour);
// Backdrop click closes ONLY in review mode — a first-run user shouldn't skip
// onboarding by a stray click outside the card (Skip is the deliberate way).
modal.addEventListener("click", (e) => { if (e.target === modal && !firstRun) closeReview(); });
// Settings → "Show feature tour" opens it in review mode.
document.getElementById("showTourBtn").addEventListener("click", () => openTour(false));

// First-run trigger. Same guard as the handbook's (a storage mode must exist — the
// theme toggle fires this event too, and there's nowhere to persist without a mode).
// Also sets the flag SILENTLY for existing users (already set up) so the tour never
// surprises them, and so a later "Start new semester" won't re-trigger it.
window.addEventListener("modulo:datachanged", () => {
  if (!getStorageMode()) return;
  if (localStorage.getItem(TOUR_KEY)) return;   // already seen/skipped on this device
  if (appState.handbookSetup) {                 // existing user — not new; don't ever nag
    localStorage.setItem(TOUR_KEY, "1");
    return;
  }
  if (modal.style.display !== "none") return;   // already open
  openTour(true);
});
