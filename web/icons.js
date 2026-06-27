// icons.js — Lucide icon rendering. The CDN UMD script (loaded in index.html) exposes a
// global `lucide`; lucide.createIcons() scans the DOM and replaces every element with a
// data-lucide="name" attribute (e.g. <i data-lucide="calendar">) with that icon's <svg>.
//
// We draw once at startup (the static sidebar nav) and again after modulo:datachanged, so
// any icons added by a later dynamic render also get drawn. Already-replaced icons have no
// data-lucide attribute anymore, so re-running is cheap and idempotent.

// Exported so modules that swap a button's innerHTML at runtime (e.g. the study-timer
// play/pause toggle) can redraw the new <i data-lucide> immediately.
export function drawIcons() {
  // Guard: if the CDN didn't load (offline), skip silently rather than throwing.
  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons();
  }
}

// Defer the first draw to a microtask so it runs AFTER every module's top-level code (e.g.
// theme.js setting the toggle's moon/sun placeholder) — import order then can't matter.
queueMicrotask(drawIcons);
window.addEventListener("modulo:datachanged", drawIcons);
