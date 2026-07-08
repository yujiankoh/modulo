// cityScene.js — the study city ART (Phase 15, step 4+). One layered inline SVG:
// the base 2.5D island scene is always visible; every unlockable sits in its own
// <g data-min-index="N"> group, revealed by cityView.js when the BUILT level
// reaches N (indexes = rows of GROWTH_STAGES in logic/growth.js).
//
// HOW TO ADD ART (step 5): draw inside the matching <g> below — each level ADDS
// buildings to the same scene, nothing is redrawn. Colours must be CSS tokens
// (var(--city-*), defined in style.css for light AND dark) — never hardcoded.
//
// Coordinate system: viewBox 0 0 800 440. The island is a diamond (isometric look)
// centred at (400, 230): sea all around, a sand coastline ring, grass on top.

export const SCENE_SVG = `
<svg class="city-svg" viewBox="0 0 800 440" role="img" aria-label="Your study city">
  <!-- ============ BASE SCENE (always visible): sea, island, coastline ============ -->
  <!-- The sea fills the whole stage. -->
  <rect x="0" y="0" width="800" height="440" fill="var(--city-sea)" />

  <!-- Island "thickness": two darker side faces peeking below the land diamond —
       this is what sells the 2.5D look. Drawn first so the land sits on top. -->
  <polygon points="60,230 400,400 400,428 60,258" fill="var(--city-soil)" />
  <polygon points="740,230 400,400 400,428 740,258" fill="var(--city-soil)" opacity="0.75" />

  <!-- Sand coastline diamond (the empty-coastline start state IS this beach). -->
  <polygon points="400,60 740,230 400,400 60,230" fill="var(--city-sand)" />

  <!-- Grass top, inset from the sand so a beach ring stays visible all round. -->
  <polygon points="400,95 670,230 400,365 130,230" fill="var(--city-grass)" />

  <!-- ============ UNLOCKABLES (revealed at data-min-index <= builtIndex) ==========
       Step 4: simple placeholder shapes for the first levels, to prove the engine.
       Step 5 replaces them with real art and fills in the remaining groups. -->

  <!-- 1 · S1L1 Boat houses (on the sea, off the south-west beach) -->
  <g data-min-index="1">
    <rect x="150" y="298" width="54" height="26" rx="4" fill="var(--primary)" />
    <polygon points="150,298 177,282 204,298" fill="var(--primary)" opacity="0.7" />
  </g>

  <!-- 2 · S1L2 Stilt houses (over the water's edge, south-east) -->
  <g data-min-index="2">
    <line x1="580" y1="330" x2="580" y2="352" stroke="var(--primary)" stroke-width="4" />
    <line x1="614" y1="330" x2="614" y2="352" stroke="var(--primary)" stroke-width="4" />
    <rect x="568" y="304" width="58" height="28" rx="4" fill="var(--primary)" />
    <polygon points="568,304 597,288 626,304" fill="var(--primary)" opacity="0.7" />
  </g>

  <!-- 3 · S2L1 Kampung houses (on the grass, north side) -->
  <g data-min-index="3">
    <rect x="368" y="160" width="64" height="34" rx="4" fill="var(--primary)" />
    <polygon points="368,160 400,138 432,160" fill="var(--primary)" opacity="0.7" />
  </g>

  <!-- 4 · S2L2 More kampung houses -->
  <g data-min-index="4"></g>

  <!-- 5 · S2L3 Dirt road -->
  <g data-min-index="5"></g>

  <!-- 6 · S3L1 Early shophouses -->
  <g data-min-index="6"></g>

  <!-- 7 · S3L2 Better road + rickshaw -->
  <g data-min-index="7"></g>

  <!-- 8 · S3L3 Traditional shophouses I -->
  <g data-min-index="8"></g>

  <!-- 9 · S3L4 Traditional shophouses II -->
  <g data-min-index="9"></g>

  <!-- 10 · S3L5 Traditional shophouses III -->
  <g data-min-index="10"></g>

  <!-- 11 · S3L6 Art Deco shophouses -->
  <g data-min-index="11"></g>
</svg>
`;
