// theme.js — light/dark theme toggle, persisted per device in localStorage.
// The saved theme is applied BEFORE paint by a tiny inline script in index.html (so there's
// no flash); this module just wires the toggle buttons and keeps their labels in sync.

const KEY = "modulo-theme";

// "dark" if <html data-theme="dark">, else "light".
function currentTheme() {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

// The ONE theme control is the Settings switch (2026-07-15; the old labelled buttons
// are gone). Its knob position and icons are pure CSS off [data-theme] — the only
// JS-owned state is aria-checked (it's role="switch", announced on/off).
function updateSwitch(theme) {
  document.getElementById("themeSwitch")?.setAttribute("aria-checked", String(theme === "dark"));
}

// Set the theme on <html> (CSS [data-theme="dark"] reacts) and remember it.
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem(KEY, theme);
  updateSwitch(theme);
  // Module colours are applied inline at render time (per theme), so redraw the views that
  // use them (dots/cards/timeline) to pick up the right palette immediately.
  window.dispatchEvent(new Event("modulo:datachanged"));
}

function toggleTheme() {
  applyTheme(currentTheme() === "dark" ? "light" : "dark");
}

document.getElementById("themeSwitch")?.addEventListener("click", toggleTheme);
updateSwitch(currentTheme());
