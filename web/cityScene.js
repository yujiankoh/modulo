// cityScene.js — the study city RENDERER (Phase 15, generative design 2026-07-08).
// renderScene(city, tier) → an SVG string of the isometric island + git-city towers,
// generated from appState.city. Pure string-building (no DOM); cityView.js injects
// the result into #cityScene on each redraw.
//
// Isometric projection (2:1): grid plot (x, y) — centre-origin, x right-down,
// y left-down — lands on screen at
//   sx = (x − y)·W/2      sy = (x + y)·H/2        (scene origin = island centre)
// Depth = x + y: bigger is nearer the viewer, so buildings are drawn back-to-front
// sorted by it (painter's algorithm).
//
// The CAMERA is the viewBox: computed each render from the island size and the
// tallest tower, so the city always fills the card — and visibly "zooms out" as the
// land expands and the skyline rises. (An SVG viewBox is the drawing's own window;
// CSS scales whatever's inside it to the card's width.)
//
// Colours are CSS tokens only. Each building gets ONE hue token (--city-b1..b5,
// picked deterministically from its coordinates); faces are shaded by overlaying
// the same shape with translucent black/white — one token per hue, both themes.

const W = 80;        // tile width on screen
const H = 40;        // tile height (2:1 iso)
const FLOOR_H = 22;  // px per building floor ("one block")
const BW = 24;       // building footprint half-width
const BH = 12;       // building footprint half-height

// Screen position of a plot (island centre = origin).
function iso(x, y) {
  return { sx: ((x - y) * W) / 2, sy: ((x + y) * H) / 2 };
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

// One tower: left/right faces + roof in the plot's hue (shade overlays), two window
// columns per floor per face (reference style), light edge highlights that make the
// silhouette crisp, an antenna on tall towers.
function building(b) {
  const { sx, sy } = iso(b.x, b.y);
  const ht = b.floors * FLOOR_H;
  const fill = `var(${hueToken(b.x, b.y)})`;

  const left = `${sx - BW},${sy - ht} ${sx},${sy - ht + BH} ${sx},${sy + BH} ${sx - BW},${sy}`;
  const right = `${sx},${sy - ht + BH} ${sx + BW},${sy - ht} ${sx + BW},${sy} ${sx},${sy + BH}`;
  const roof = diamond(sx, sy - ht, BW, BH);

  const parts = [
    `<polygon points="${left}" fill="${fill}" />`,
    `<polygon points="${left}" fill="#000" opacity="0.18" />`,   // shade left face
    `<polygon points="${right}" fill="${fill}" />`,
    `<polygon points="${right}" fill="#000" opacity="0.32" />`,  // shade right face darker
    `<polygon points="${roof}" fill="${fill}" />`,
    `<polygon points="${roof}" fill="#fff" opacity="0.3" />`,    // lit roof
    // Edge highlights (the reference look): front roof edges + the centre corner.
    `<polyline points="${sx - BW},${sy - ht} ${sx},${sy - ht + BH} ${sx + BW},${sy - ht}" ` +
    `fill="none" stroke="#fff" opacity="0.45" stroke-width="1.2" />`,
    `<line x1="${sx}" y1="${sy - ht + BH}" x2="${sx}" y2="${sy + BH}" ` +
    `stroke="#fff" opacity="0.25" stroke-width="1.2" />`,
  ];

  // Windows: two columns per face, one row per floor, drawn as parallelograms that
  // follow each face's slope. t walks the face edge (0 = outer corner, 1 = centre).
  const winH = 5.5;
  const cols = [[0.18, 0.42], [0.58, 0.82]];
  for (let i = 0; i < b.floors; i++) {
    const mid = sy - i * FLOOR_H - FLOOR_H / 2;   // vertical middle of this storey
    const lx = (t) => sx - BW + t * BW, ly = (t) => mid + t * BH;      // left face edge
    const rx = (t) => sx + t * BW,      ry = (t) => mid + BH - t * BH; // right face edge
    for (const [t1, t2] of cols) {
      parts.push(
        `<polygon points="${lx(t1)},${ly(t1) - winH} ${lx(t2)},${ly(t2) - winH} ` +
        `${lx(t2)},${ly(t2) + winH} ${lx(t1)},${ly(t1) + winH}" fill="var(--city-window)" />`,
        `<polygon points="${rx(t1)},${ry(t1) - winH} ${rx(t2)},${ry(t2) - winH} ` +
        `${rx(t2)},${ry(t2) + winH} ${rx(t1)},${ry(t1) + winH}" fill="var(--city-window)" />`
      );
    }
  }

  // Antenna on tall towers (5+ floors): a mast and a beacon dot.
  if (b.floors >= 5) {
    const topY = sy - ht - BH;
    parts.push(
      `<line x1="${sx}" y1="${topY + 5}" x2="${sx}" y2="${topY - 11}" ` +
      `stroke="var(--text-muted)" stroke-width="1.6" />`,
      `<circle cx="${sx}" cy="${topY - 13}" r="2" fill="var(--danger)" />`
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
  const soilDepth = 26;
  const sandE = W / 2 + W * 0.35;         // beach horizontal overhang
  const soilLeft =
    `${leftC.sx - sandE},${leftC.sy} ${bottom.sx},${bottom.sy + H / 2 + H * 0.35} ` +
    `${bottom.sx},${bottom.sy + H / 2 + H * 0.35 + soilDepth} ${leftC.sx - sandE},${leftC.sy + soilDepth}`;
  const soilRight =
    `${rightC.sx + sandE},${rightC.sy} ${bottom.sx},${bottom.sy + H / 2 + H * 0.35} ` +
    `${bottom.sx},${bottom.sy + H / 2 + H * 0.35 + soilDepth} ${rightC.sx + sandE},${rightC.sy + soilDepth}`;

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
  const valid = ((city && city.buildings) || [])
    .filter((b) => Number.isInteger(b?.floors) && b.floors > 0);
  const towers = valid
    .slice()
    .sort((a, b) => (a.x + a.y) - (b.x + b.y))
    .map(building);

  // Decorative waves off the west/east/north water (tier-relative, so they follow
  // the coast as the land expands).
  const wave = (wx, wy) =>
    `<path d="M ${wx} ${wy} q 12 7 24 0 t 24 0" fill="none" ` +
    `stroke="var(--city-wave)" stroke-width="3" stroke-linecap="round" />`;
  const waves = [
    wave(leftC.sx - W - 50, leftC.sy + 30),
    wave(leftC.sx - W + 4, leftC.sy - 52),
    wave(rightC.sx + W / 2 + 30, rightC.sy + 40),
    wave(rightC.sx + W / 2 + 70, rightC.sy - 36),
    wave(top.sx + 70, top.sy - H - 54),
  ];

  // The camera: a viewBox hugging the island + the tallest tower (min 3 floors of
  // headroom so a young city isn't glued to the frame). Margins keep the waves in.
  const maxFloors = Math.max(3, ...valid.map((b) => b.floors));
  const skyline = maxFloors * FLOOR_H + BH + 18;      // tower + roof + antenna room
  const mLeft = leftC.sx - sandE - 90;
  const mRight = rightC.sx + sandE + 90;
  const mTop = top.sy - H / 2 - H * 0.35 - skyline - 24;
  const mBottom = bottom.sy + H / 2 + H * 0.35 + soilDepth + 30;
  const viewBox = `${mLeft} ${mTop} ${mRight - mLeft} ${mBottom - mTop}`;

  return `
<svg class="city-svg" viewBox="${viewBox}" role="img" aria-label="Your study city">
  <rect x="${mLeft}" y="${mTop}" width="${mRight - mLeft}" height="${mBottom - mTop}" fill="var(--city-sea)" />
  ${waves.join("\n  ")}
  <polygon points="${soilLeft}" fill="var(--city-soil)" />
  <polygon points="${soilRight}" fill="var(--city-soil)" opacity="0.75" />
  <polygon points="${sandPts}" fill="var(--city-sand)" />
  <polygon points="${landPts}" fill="var(--city-grass)" />
  ${tiles.join("\n  ")}
  ${towers.join("\n  ")}
</svg>`;
}
