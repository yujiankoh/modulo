// theme.js — light/dark theme toggle, persisted per device in localStorage.
// The saved theme is applied BEFORE paint by a tiny inline script in index.html (so there's
// no flash); this module just wires the toggle buttons and keeps their labels in sync.

const KEY = "modulo-theme";

// "dark" if <html data-theme="dark">, else "light".
function currentTheme() {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

// Keep the toggles matching the active theme. The Settings button's icon shows what
// you'll switch TO (sun while dark, moon while light) — innerHTML with a fresh
// <i data-lucide> placeholder; icons.js (re)draws it on the modulo:datachanged that
// applyTheme fires. The topbar SWITCH needs no label work — its knob position is pure
// CSS off [data-theme] — only the aria-checked state (it's role="switch") is JS's job.
function updateLabels(theme) {
  const dark = theme === "dark";
  const icon = dark ? "sun" : "moon";
  const settings = document.getElementById("themeToggle");
  if (settings) settings.innerHTML = `<i data-lucide="${icon}"></i>${dark ? "Switch to light mode" : "Switch to dark mode"}`;
  document.getElementById("themeSwitch")?.setAttribute("aria-checked", String(dark));
}

// Set the theme on <html> (CSS [data-theme="dark"] reacts), remember it, refresh labels.
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem(KEY, theme);
  updateLabels(theme);
  // Module colours are applied inline at render time (per theme), so redraw the views that
  // use them (dots/cards/timeline) to pick up the right palette immediately.
  window.dispatchEvent(new Event("modulo:datachanged"));
}

function toggleTheme() {
  applyTheme(currentTheme() === "dark" ? "light" : "dark");
}

document.getElementById("themeSwitch")?.addEventListener("click", toggleTheme);
document.getElementById("themeToggle")?.addEventListener("click", toggleTheme);
updateLabels(currentTheme());
