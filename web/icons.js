// icons.js — Lucide icon rendering. The CDN UMD script (loaded in index.html) exposes a
// global `lucide`; lucide.createIcons() scans the DOM and replaces every element with a
// data-lucide="name" attribute (e.g. <i data-lucide="calendar">) with that icon's <svg>.
//
// We draw once at startup (the static sidebar nav) and again after modulo:datachanged, so
// any icons added by a later dynamic render also get drawn. Already-replaced icons have no
// data-lucide attribute anymore, so re-running is cheap and idempotent.

function drawIcons() {
  // Guard: if the CDN didn't load (offline), skip silently rather than throwing.
  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons();
  }
}

drawIcons();
window.addEventListener("modulo:datachanged", drawIcons);
