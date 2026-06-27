// theme.js — light/dark theme toggle, persisted per device in localStorage.
// The saved theme is applied BEFORE paint by a tiny inline script in index.html (so there's
// no flash); this module just wires the toggle buttons and keeps their labels in sync.

const KEY = "modulo-theme";

// "dark" if <html data-theme="dark">, else "light".
function currentTheme() {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

// Keep both toggles' labels matching the active theme.
function updateLabels(theme) {
  const dark = theme === "dark";
  const side = document.getElementById("themeToggleSide");
  const settings = document.getElementById("themeToggle");
  if (side) side.textContent = dark ? "☀ Light mode" : "🌙 Dark mode";
  if (settings) settings.textContent = dark ? "Switch to light mode" : "Switch to dark mode";
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

document.getElementById("themeToggleSide")?.addEventListener("click", toggleTheme);
document.getElementById("themeToggle")?.addEventListener("click", toggleTheme);
updateLabels(currentTheme());
