// citySchemes.js — user-selectable colour schemes for the study city (Phase 15).
// A scheme re-values the WHOLE scene: the five building hues (--city-b1..b5) AND
// the land/sea tokens — so picking "Ember" turns the island itself scorched, not
// just the towers. The plot→hue hash in cityScene.js is untouched: which building
// gets which palette slot never changes, only what the slots look like.
//
// Per-DEVICE preference (localStorage, like the light/dark theme) — deliberately
// NOT in modulo-data.json and NOT part of the Android contract; palettes are
// presentation, each platform may offer its own.
//
// Applying = setting inline custom properties on <html>, which override the
// stylesheet defaults; the standing SVG recolours live because it uses var(--…).

const KEY = "modulo-city-scheme";

// Each scheme: display name, an accent (the picker's arrow/name colour), and a
// full token set for day + night: b = the five building hues, then land/sea.
export const CITY_SCHEMES = {
  classic: {
    name: "Classic",
    accent: "#e8b04a",
    light: { b: ["#e8b04a", "#6f9fe8", "#8fc9cf", "#d98a7a", "#b3a1e0"],
             sea: "#a9cdf2", sand: "#ecdcae", grass: "#9ecf90", grassAlt: "#93c785",
             soil: "#b9946a", wave: "#8fb9e2" },
    dark:  { b: ["#a87f33", "#4a72b8", "#5e989e", "#a06153", "#7f6fae"],
             sea: "#17304f", sand: "#6d5f3e", grass: "#2f5c3d", grassAlt: "#2a5237",
             soil: "#4a3a28", wave: "#23446b" },
  },
  dreamland: {
    name: "Dreamland",
    accent: "#ffafcc",
    light: { b: ["#cdb4db", "#ffc8dd", "#ffafcc", "#bde0fe", "#a2d2ff"],
             sea: "#d6e4ff", sand: "#f7dfe8", grass: "#c8e7c9", grassAlt: "#bcdebd",
             soil: "#b79ec4", wave: "#b8d0f5" },
    dark:  { b: ["#9a86a8", "#c495a8", "#c07f97", "#8aa6c2", "#7d9fc4"],
             sea: "#2a3352", sand: "#8a7386", grass: "#5d7a62", grassAlt: "#52705a",
             soil: "#6b5878", wave: "#3d4a75" },
  },
  ocean: {
    name: "Ocean",
    accent: "#81c3d7",
    light: { b: ["#3a7ca5", "#81c3d7", "#d9dcd6", "#2f6690", "#16425b"],
             sea: "#bcd8e8", sand: "#d9dcd6", grass: "#7fa696", grassAlt: "#74998a",
             soil: "#5b7085", wave: "#9fc4d8" },
    dark:  { b: ["#2f6485", "#5f93a3", "#9a9d98", "#27506f", "#123448"],
             sea: "#10293a", sand: "#5c6360", grass: "#29473d", grassAlt: "#244037",
             soil: "#34495c", wave: "#1e3d54" },
  },
  sunset: {
    name: "Sunset",
    accent: "#f4d58d",
    light: { b: ["#708d81", "#f4d58d", "#bf0603", "#8d0801", "#001427"],
             sea: "#eec695", sand: "#f4d58d", grass: "#94a07a", grassAlt: "#8a966f",
             soil: "#8d5a3a", wave: "#d9a86f" },
    dark:  { b: ["#5a7168", "#c2a86b", "#8f0402", "#6b0601", "#12263d"],
             sea: "#3a2635", sand: "#8a6b4a", grass: "#4a5240", grassAlt: "#414a38",
             soil: "#4f3222", wave: "#57394d" },
  },
  ember: {
    name: "Ember",
    accent: "#ff7b1a",
    light: { b: ["#ffc22e", "#ff6f12", "#e03c08", "#a81f05", "#421002"],
             sea: "#ff9e54", sand: "#f2701e", grass: "#c2571f", grassAlt: "#b64e1a",
             soil: "#701f06", wave: "#ffc46e", window: "#fff3d0" },
    dark:  { b: ["#ffb52e", "#f56a12", "#d4380a", "#951c06", "#3a0c02"],
             sea: "#4c0f04", sand: "#b84a10", grass: "#48200e", grassAlt: "#3e1a0a",
             soil: "#160603", wave: "#ff6a1a", window: "#ffe8b0" },
  },
  // (Was "Neon"; reverted from a louder full-voltage experiment + renamed 2026-07-08.
  // The key stays "neon" so a saved preference keeps working.)
  neon: {
    name: "Midnight",
    accent: "#f72585",
    light: { b: ["#f72585", "#b5179e", "#7209b7", "#3f37c9", "#4cc9f0"],
             sea: "#c9c3f5", sand: "#e3d9fa", grass: "#a3aee0", grassAlt: "#97a2d6",
             soil: "#6b5f9e", wave: "#a99ae8" },
    dark:  { b: ["#c21d69", "#8d127b", "#58078e", "#302b9c", "#3a9dbd"],
             sea: "#12102b", sand: "#3a2f5e", grass: "#232a52", grassAlt: "#1e244a",
             soil: "#2c2350", wave: "#241f45" },
  },
};

export function getSavedScheme() {
  const saved = localStorage.getItem(KEY);
  return CITY_SCHEMES[saved] ? saved : "classic";   // unknown/stale value → default
}

export function setSavedScheme(key) {
  localStorage.setItem(KEY, key);
}

// (Re)apply the saved scheme for the CURRENT theme. Called on every city render —
// cheap, and it makes theme toggles just work (theme.js fires modulo:datachanged
// after flipping data-theme).
export function applyScheme() {
  const set = CITY_SCHEMES[getSavedScheme()][
    document.documentElement.dataset.theme === "dark" ? "dark" : "light"
  ];
  const root = document.documentElement.style;
  set.b.forEach((hex, i) => root.setProperty(`--city-b${i + 1}`, hex));
  root.setProperty("--city-sea", set.sea);
  root.setProperty("--city-sand", set.sand);
  root.setProperty("--city-grass", set.grass);
  root.setProperty("--city-grass-alt", set.grassAlt);
  root.setProperty("--city-soil", set.soil);
  root.setProperty("--city-wave", set.wave);
  // Optional per-scheme window colour (e.g. Ember's white-hot glass). Schemes
  // without one fall back to the stylesheet's theme default — the removeProperty
  // matters, or a previous scheme's override would stick.
  if (set.window) root.setProperty("--city-window", set.window);
  else root.removeProperty("--city-window");
}
