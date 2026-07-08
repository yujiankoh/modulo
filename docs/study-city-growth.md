# Study city — growth contract (web ↔ app)

The shared rules for the gamified study motivator: a virtual **Singapore city** that
grows with **all-time study minutes**. Both the web (JavaScript, `web/logic/growth.js`)
and the app (Kotlin) must implement **exactly these rules**, or the two devices will
show different cities for the same data.

- **Fuel:** the top-level `studySessions` in `modulo-data.json` — **global**, across all
  handbooks/semesters.
- **Stored state:** exactly ONE field — top-level **`cityLevel`** (see
  `modulo-data-schema.md`). Everything else below is **derived on the fly and never
  stored**.
- Scope: stages 1–3 for MS3. **Stages 4–6 (Early HDB / Modern HDB / MBS skyline) are
  added later, on both platforms together** — never unilaterally.

---

## The mechanic in one paragraph

Studying **earns** city levels automatically; the user presses an **Upgrade** button to
**build** each earned level, one press = one level. The scene always renders the **built**
level; the progress numbers (bar, "time until next upgrade") describe the **earned**
level. `cityLevel` records the built level. The earned level is recomputed from
`studySessions` every time — it is never written anywhere.

## The growth table

Thresholds are in **MINUTES** (the unit `durationMins` is stored in — never convert to
hours inside logic, only for display). Row order = index 0–11.

| index | stage | level | name | minMins |
|---|---|---|---|---|
| 0 | 0 | 0 | Empty coastline | 0 |
| 1 | 1 | 1 | Boat houses | 30 |
| 2 | 1 | 2 | Stilt houses | 90 |
| 3 | 2 | 1 | Kampung houses | 180 |
| 4 | 2 | 2 | More kampung houses | 360 |
| 5 | 2 | 3 | Dirt road | 600 |
| 6 | 3 | 1 | Early shophouses | 900 |
| 7 | 3 | 2 | Better road + rickshaw | 1260 |
| 8 | 3 | 3 | Traditional shophouses I | 1680 |
| 9 | 3 | 4 | Traditional shophouses II | 2160 |
| 10 | 3 | 5 | Traditional shophouses III | 2700 |
| 11 | 3 | 6 | Art Deco shophouses | 3300 |

Stages: 1 = Fishing Village, 2 = Kampung, 3 = Shophouses.

## The rules (each must match bit-for-bit)

1. **Total minutes** = sum of `durationMins` over ALL `studySessions`. A missing or
   non-numeric `durationMins` counts as **0** (defensive — one bad record must not break
   the city).
2. **Earned index** = the highest row with `minMins <= totalMins`. Note **`>=` at the
   boundary**: exactly 30 total minutes DOES earn row 1.
3. **Built index** = `cityLevel`, **clamped to `[0, earnedIndex]`**. A missing, negative,
   non-integer, or too-high stored value must render as if clamped — never trust the
   stored number to be legal, it may have been written by the other client or a buggy
   save. (Also clamp before *using* it in rule 4.)
4. **Upgrade press** → new `cityLevel = min(builtIndex + 1, earnedIndex)` — exactly one
   level per press, never past what's earned. Save immediately (one write per press).
   The button is available iff `builtIndex < earnedIndex`.
5. **Next threshold** = `minMins` of row `earnedIndex + 1`; at the top row there is none
   (web returns `null` — show a "city complete" state instead of a bar).
6. **Progress percent** (toward the *next earned* level, not the whole ladder):
   `floor((totalMins - currentRow.minMins) / (nextRow.minMins - currentRow.minMins) * 100)`
   where `currentRow` = the earned row. **`floor`, not round** — the bar must only read
   100 when the level is actually earned (max 99 mid-band). At the top row: 100.
7. **`cityLevel` is GLOBAL** — like `studySessions`, it is never part of a handbook and a
   handbook switch must not change it.
8. **Display rule for the EXP bar:** while an upgrade is pending (`builtIndex <
   earnedIndex`) the bar shows **100%** — each press visually "spends" a full bar.
   The rule-6 `progressPct` is shown only when fully built (`builtIndex ==
   earnedIndex`). Also: show **no absolute time-to-next-upgrade** anywhere (decided
   2026-07-08) — thresholds are internal, the filling bar is the only signal.

## Reference (Kotlin sketch)

```kotlin
data class GrowthRow(val stage: Int, val level: Int, val name: String, val minMins: Int)

val GROWTH_STAGES = listOf(
    GrowthRow(0, 0, "Empty coastline", 0),
    GrowthRow(1, 1, "Boat houses", 30),
    GrowthRow(1, 2, "Stilt houses", 90),
    GrowthRow(2, 1, "Kampung houses", 180),
    GrowthRow(2, 2, "More kampung houses", 360),
    GrowthRow(2, 3, "Dirt road", 600),
    GrowthRow(3, 1, "Early shophouses", 900),
    GrowthRow(3, 2, "Better road + rickshaw", 1260),
    GrowthRow(3, 3, "Traditional shophouses I", 1680),
    GrowthRow(3, 4, "Traditional shophouses II", 2160),
    GrowthRow(3, 5, "Traditional shophouses III", 2700),
    GrowthRow(3, 6, "Art Deco shophouses", 3300),
)

fun totalStudyMins(sessions: List<StudySession>): Int =
    sessions.sumOf { it.durationMins }          // durationMins is non-null Int in Kotlin

fun earnedIndex(totalMins: Int): Int =
    GROWTH_STAGES.indexOfLast { totalMins >= it.minMins }

fun builtIndex(cityLevel: Int, earned: Int): Int =
    cityLevel.coerceIn(0, earned)

fun claimUpgrade(built: Int, earned: Int): Int =
    minOf(built + 1, earned)

fun progressPct(totalMins: Int, earned: Int): Int {
    if (earned == GROWTH_STAGES.lastIndex) return 100
    val bandStart = GROWTH_STAGES[earned].minMins
    val bandEnd = GROWTH_STAGES[earned + 1].minMins
    return ((totalMins - bandStart) * 100) / (bandEnd - bandStart)   // Int division = floor
}
```

Worked checks (use as test cases): 0 mins → earned 0, progress 0 · 29 mins → earned 0 ·
30 mins → earned 1 · 45 mins → earned 1, progress 25 · 89 mins → earned 1, progress 98 ·
3300 mins → earned 11, maxed · `cityLevel 5` with 30 mins → built clamps to 1, no upgrade
available · press at built 0 / earned 3 → cityLevel 1.

## Change log

| Date | Change |
|------|--------|
| 2026-07-08 | Initial contract: 12-row table (stages 1–3, MS3 scope), earn/clamp/press/floor rules, `cityLevel` field. |
