// cityScene.js — the study city RENDERER (Phase 15, generative design 2026-07-08).
// renderScene(city, tier) → an SVG string of the isometric island + git-city towers,
// generated from appState.city. Pure string-building (no DOM); cityView.js injects
// the result into #cityScene on each redraw.
//
// Isometric projection (2:1): grid plot (x, y) — centre-origin, x right-down,
// y left-down — lands on screen at
//   sx = CX + (x − y)·W/2      sy = CY + (x + y)·H/2
// Depth = x + y: bigger is nearer the viewer, so buildings are drawn back-to-front
// sorted by it (painter's algorithm).
//
// Colours are CSS tokens only. Each building gets ONE hue token (--city-b1..b5,
// picked deterministically from its coordinates); its three faces are shaded by
// overlaying the same shape with translucent black/white — one token per hue
// covers every face in both themes.

const W = 64;        // tile width on screen
const H = 32;        // tile height (2:1 iso)
const FLOOR_H = 12;  // px per building floor
const CX = 400;      // island centre in the viewBox
const CY = 290;
const VIEW = "0 0 800 500";

// Screen position of a plot.
function iso(x, y) {
  return { sx: CX + ((x - y) * W) / 2, sy: CY + ((x + y) * H) / 2 };
}

// A diamond's points attribute, centred (sx, sy), half-width w2, half-height h2.
function diamond(sx, sy, w2, h2) {
  return `${sx},${sy - h2} ${sx + w2},${sy} ${sx},${sy + h2} ${sx - w2},${sy}`;
}

// Deterministic hue per plot — same plot, same colour, forever, on every device
// (no stored colour needed). The +25 keeps the modulo positive for negative coords.
function hueToken(x, y) {
  return `--city-b${(((x * 31 + y * 17) % 5) + 25) % 5 + 1}`;
}

// One git-city tower: stacked box of `floors` floors — left face, right face, roof,
// all in the plot's hue token, shaded by translucent overlays; thin seam lines mark
// the floors.
function building(b) {
  const { sx, sy } = iso(b.x, b.y);
  const w2 = 20, h2 = 10;                 // footprint: a bit smaller than the tile
  const ht = b.floors * FLOOR_H;          // total height
  const fill = `var(${hueToken(b.x, b.y)})`;

  const left = `${sx - w2},${sy - ht} ${sx},${sy - ht + h2} ${sx},${sy + h2} ${sx - w2},${sy}`;
  const right = `${sx},${sy - ht + h2} ${sx + w2},${sy - ht} ${sx + w2},${sy} ${sx},${sy + h2}`;
  const roof = diamond(sx, sy - ht, w2, h2);

  const parts = [
    `<polygon points="${left}" fill="${fill}" />`,
    `<polygon points="${left}" fill="#000" opacity="0.18" />`,   // shade left face
    `<polygon points="${right}" fill="${fill}" />`,
    `<polygon points="${right}" fill="#000" opacity="0.32" />`,  // shade right face darker
    `<polygon points="${roof}" fill="${fill}" />`,
    `<polygon points="${roof}" fill="#fff" opacity="0.3" />`,    // lit roof
  ];
  // Floor seams: one chevron line per storey boundary, across both faces.
  for (let i = 1; i < b.floors; i++) {
    const y0 = sy - i * FLOOR_H;
    parts.push(
      `<polyline points="${sx - w2},${y0} ${sx},${y0 + h2} ${sx + w2},${y0}" ` +
      `fill="none" stroke="#000" opacity="0.12" stroke-width="1" />`
    );
  }
  return parts.join("\n    ");
}

// The whole scene for a given stored city + land tier.
export function renderScene(city, tier) {
  const R = (tier.size - 1) / 2;

  // Island outline corners (screen): the four extreme plots, pushed out half a tile.
  const top = iso(-R, -R), rightC = iso(R, -R), bottom = iso(R, R), leftC = iso(-R, R);
  const landPts =
    `${top.sx},${top.sy - H / 2} ${rightC.sx + W / 2},${rightC.sy} ` +
    `${bottom.sx},${bottom.sy + H / 2} ${leftC.sx - W / 2},${leftC.sy}`;
  // Beach ring: the same diamond, grown by ~a third of a tile.
  const sandPts =
    `${top.sx},${top.sy - H / 2 - H * 0.35} ${rightC.sx + W / 2 + W * 0.35},${rightC.sy} ` +
    `${bottom.sx},${bottom.sy + H / 2 + H * 0.35} ${leftC.sx - W / 2 - W * 0.35},${leftC.sy}`;
  // Island thickness: soil faces hanging below the beach's south edges.
  const soilDepth = 22;
  const soilLeft =
    `${leftC.sx - W / 2 - W * 0.35},${leftC.sy} ${bottom.sx},${bottom.sy + H / 2 + H * 0.35} ` +
    `${bottom.sx},${bottom.sy + H / 2 + H * 0.35 + soilDepth} ${leftC.sx - W / 2 - W * 0.35},${leftC.sy + soilDepth}`;
  const soilRight =
    `${rightC.sx + W / 2 + W * 0.35},${rightC.sy} ${bottom.sx},${bottom.sy + H / 2 + H * 0.35} ` +
    `${bottom.sx},${bottom.sy + H / 2 + H * 0.35 + soilDepth} ${rightC.sx + W / 2 + W * 0.35},${rightC.sy + soilDepth}`;

  // Grass tiles, checkered with two tones so the grid reads without hard lines.
  const tiles = [];
  for (let x = -R; x <= R; x++) {
    for (let y = -R; y <= R; y++) {
      const { sx, sy } = iso(x, y);
      const tone = (x + y) % 2 === 0 ? "--city-grass" : "--city-grass-alt";
      tiles.push(`<polygon points="${diamond(sx, sy, W / 2, H / 2)}" fill="var(${tone})" />`);
    }
  }

  // Buildings back-to-front (depth = x + y; ties don't overlap in 2:1 iso).
  const towers = ((city && city.buildings) || [])
    .filter((b) => Number.isInteger(b?.floors) && b.floors > 0)
    .slice()
    .sort((a, b) => (a.x + a.y) - (b.x + b.y))
    .map(building);

  return `
<svg class="city-svg" viewBox="${VIEW}" role="img" aria-label="Your study city">
  <rect x="0" y="0" width="800" height="500" fill="var(--city-sea)" />
  <polygon points="${soilLeft}" fill="var(--city-soil)" />
  <polygon points="${soilRight}" fill="var(--city-soil)" opacity="0.75" />
  <polygon points="${sandPts}" fill="var(--city-sand)" />
  <polygon points="${landPts}" fill="var(--city-grass)" />
  ${tiles.join("\n  ")}
  ${towers.join("\n  ")}
</svg>`;
}
