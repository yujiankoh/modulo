# Study city — growth contract (web ↔ app)

The shared rules for the gamified study motivator: a **generative isometric city** that
grows automatically with **all-time study minutes** (Forest + git-city style; this
REPLACES the earlier fixed-stage contract of 2026-07-08 — the stage table is dead).
Both the web (`web/logic/growth.js`) and the app (Kotlin) must implement **exactly
these rules**, or the two devices will grow different cities from the same data.

- **Fuel:** the top-level `studySessions` in `modulo-data.json` — **global**, across all
  handbooks/semesters.
- **Stored state:** exactly ONE field — top-level **`city: { buildings: [{ x, y,
  floors }] }`** (see `modulo-data-schema.md`). Stored because upgrade *placement* is
  random and cannot be re-derived. Every count is derived.
- **No UI reveals numbers** (thresholds, formulas, time-to-next) — the growing city is
  the only signal.

---

## The game in one paragraph

Studying earns **upgrade events** on a pace that slowly stretches. Each event picks one
plot on a centre-weighted grid: an empty plot gets a 1-floor building, an occupied one
grows a floor. Upgrades apply **automatically** (no user action): whenever a client
notices earned > applied, it rolls the pending events, updates `city`, and saves. The
land itself expands at study milestones. Result: a skyline that rises from the centre
of a growing island.

## Rule 1 — total minutes

`totalMins` = sum of `durationMins` over ALL `studySessions`. Missing/non-numeric
`durationMins` counts as 0.

## Rule 2 — earned upgrades (pacing)

The (x+1)-th upgrade costs `2x + 10` minutes, so **n upgrades need `n² + 9n` total
minutes** (thresholds 10, 22, 36, 52, 70, …). Earned upgrades for a given total:

```
n = floor((-9 + sqrt(81 + 4 * totalMins)) / 2)
```

…then correct for float error until `n²+9n ≤ totalMins < (n+1)²+9(n+1)` holds exactly
(the web nudges n up/down in a loop; integer arithmetic must be exact at boundaries).

## Rule 3 — land tiers

| totalMins | land | floor cap |
|---|---|---|
| 0 | 5×5 | 5 |
| ≥ 1200 (20 h) | 7×7 | 8 |
| ≥ 6000 (100 h) | 9×9 | 12 |

Plot coordinates are **centre-origin** integers (x, y ∈ −R…R, R = (size−1)/2), so
expansion widens the range without re-mapping stored buildings. *(Floor caps may be
re-tuned for visuals before MS3 ships — treat this table as the single source.)*

## Rule 4 — applied / pending upgrades

`applied` = Σ `floors` over `city.buildings` (every event adds exactly one floor).
`pending` = max(earned − applied, 0).

## Rule 5 — applying an upgrade event

For each pending event, on the CURRENT tier:

1. **Founding special case:** if the city has no buildings, build at **(0, 0)**. Done.
2. **Candidates** = every plot whose building (if any) is below the floor cap.
   If none: stop — remaining events stay banked until the next expansion.
3. **Weighted pick** among candidates: plot weight = `(R − d + 1)³` with
   `d = max(|x|, |y|)` (Chebyshev ring distance). Roll `r = random() × totalWeight`,
   walk candidates in any fixed order subtracting weights; first to push r below 0
   wins. (Excluding capped plots from candidates ≡ "re-roll until valid", exactly.)
   *(Cubed on 2026-07-08 — was squared — for a stronger downtown.)*
4. Picked plot empty → append `{ x, y, floors: 1 }`; occupied → `floors += 1`.

Randomness: each client uses its own RNG — outcomes are **persisted**, so devices
render the same stored city. Simultaneous offline growth on two devices resolves by
the file's existing last-write-wins rule (acceptable; the city is decorative).

## Rule 6 — reconcile automatically

On load and after any save that changes `studySessions`: if `pending > 0`, apply that
many events (Rule 5), write the new `city`, persist ONCE. Never persist when pending
is 0 (loop guard).

## Rule 7 — global

`city` is GLOBAL like `studySessions`: never inside a handbook, untouched by handbook
switches.

## Reference (Kotlin sketch)

```kotlin
fun earnedUpgrades(totalMins: Int): Int {
    if (totalMins < 10) return 0
    var n = ((-9 + Math.sqrt(81.0 + 4.0 * totalMins)) / 2).toInt()
    while ((n + 1) * (n + 1) + 9 * (n + 1) <= totalMins) n++
    while (n > 0 && n * n + 9 * n > totalMins) n--
    return n
}

fun plotWeight(x: Int, y: Int, size: Int): Int {
    val r = (size - 1) / 2
    val w = r - maxOf(Math.abs(x), Math.abs(y)) + 1
    return w * w * w
}
// applyUpgrades: mirror Rule 5 with kotlin.random.Random; persist the result.
```

## Test vectors (same numbers as the web unit tests)

- `earnedUpgrades`: 9→0 · 10→1 · 21→1 · 22→2 · 36→3 · 52→4 · 1200→30.
- Tiers: 1199→5×5 · 1200→7×7 · 5999→7×7 · 6000→9×9.
- `plotWeight` on 5×5: (0,0)→27 · (1,0)→8 · (2,2)→1; on 7×7: (0,0)→64.
- Empty city + 1 event → exactly `[{x:0, y:0, floors:1}]` regardless of RNG.
- 5×5 fully at cap (25×5 floors) + 10 events → unchanged (banked).

## Appendix — visual palettes (reference, NOT contract)

Rendering is per-platform: only the stored `city` grid and the rules above must match.
The web draws an isometric scene (2:1 projection: `sx=(x−y)·W/2, sy=(x+y)·H/2`, depth
sort by `x+y`) and assigns each building a palette slot by the **coordinate hash
`(x·31 + y·17) mod 5`** — implement the same hash and the two cities' colour *layouts*
match with nothing stored. The user picks a colour scheme **per device** (web:
localStorage, like the theme) — copy these values for visual parity if wanted
(`web/citySchemes.js` is the source of truth; five building hues + land, day/night):

| Scheme | Building hues (day) | Land day (sea/sand/grass/soil) | Notes |
|---|---|---|---|
| Classic | `#e8b04a #6f9fe8 #8fc9cf #d98a7a #b3a1e0` | `#a9cdf2 #ecdcae #9ecf90 #b9946a` | default |
| Dreamland | `#cdb4db #ffc8dd #ffafcc #bde0fe #a2d2ff` | `#d6e4ff #f7dfe8 #c8e7c9 #b79ec4` | pastels |
| Ocean | `#3a7ca5 #81c3d7 #d9dcd6 #2f6690 #16425b` | `#bcd8e8 #d9dcd6 #7fa696 #5b7085` | cool blues |
| Sunset | `#708d81 #f4d58d #bf0603 #8d0801 #001427` | `#eec695 #f4d58d #94a07a #8d5a3a` | dusk |
| Ember | `#ffc22e #ff6f12 #e03c08 #a81f05 #421002` | `#ff9e54 #f2701e #c2571f #701f06` | + white-hot windows |
| Midnight | `#f72585 #b5179e #7209b7 #3f37c9 #4cc9f0` | `#c9c3f5 #e3d9fa #a3aee0 #6b5f9e` | violet night |

Night (dark-theme) variants + grass-alt/wave/window values: see `web/citySchemes.js`.
Windows default to warm yellow at night (`#f4cf6d`); Ember overrides them white-hot.

## Change log

| Date | Change |
|------|--------|
| 2026-07-08 | Initial contract (fixed 12-stage table + `cityLevel` + Upgrade button). **Superseded same day** — never implemented on Android. |
| 2026-07-08 | **Rewrite: generative grid city.** `city.buildings` stored; pacing `n²+9n`; centre-weighted pick; land tiers 5/7/9; automatic upgrades (no button). |
