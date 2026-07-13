package com.example.modulo.helpers

import com.example.modulo.Building
import com.example.modulo.City
import com.example.modulo.StudySession
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

object CityLogicHelper {
    data class GridTier(val minMins: Int, val size: Int, val floorCap: Int)

    data class CityStatus(
        val totalMins: Int,
        val earned: Int,
        val applied: Int,
        val pending: Int,
        val tier: GridTier
    )

    fun totalStudyMins(sessions: List<StudySession>): Int {
        return sessions.sumOf { it.durationMins }
    }

    fun earnedUpgrades(totalMins: Int): Int {
        if (totalMins < 10) return 0
        var n = ((-9 + sqrt(81.0 + 4.0 * totalMins)) / 2).toInt()
        while ((n + 1) * (n + 1) + 9 * (n + 1) <= totalMins) n++ // sqrt rounded low
        while (n > 0 && n * n + 9 * n > totalMins) n-- // sqrt rounded high
        return n
    }

    val GRID_TIERS = listOf(
        GridTier(minMins = 0, size = 5, floorCap = 5),
        GridTier(minMins = 1200, size = 7, floorCap = 8), // 20 h
        GridTier(minMins = 6000, size = 9, floorCap = 12),  // 100 h
    )

    fun gridTier(totalMins: Int): GridTier {
        var tier = GRID_TIERS[0]
        for (t in GRID_TIERS) if (totalMins >= t.minMins) tier = t
        return tier
    }

    fun plotWeight(x: Int, y: Int, size: Int): Int {
        val r = (size - 1) / 2
        val d = max(abs(x), abs(y))
        val w = r - d + 1
        return w * w * w
    }

    fun applyUpgrades(city: City, count: Int, tier: GridTier, rng: () -> Double): City {
        val buildings = city.buildings.map { it.copy() }.toMutableList()
        val byKey = HashMap<Long, Int>() // "x,y" coordinates to building index

        fun key(x: Int, y: Int) = (x.toLong() shl 32) xor (y.toLong() and 0xFFFFFFFFL)

        buildings.forEachIndexed { i, b -> byKey[key(b.x, b.y)] = i }
        val r = (tier.size - 1) / 2

        fun place(x: Int, y: Int) {
            val k = key(x, y)
            val idx = byKey[k]
            if (idx != null) {
                buildings[idx] = buildings[idx].copy(floors = buildings[idx].floors + 1)
            } else {
                byKey[k] = buildings.size
                buildings.add(Building(x, y, 1))
            }
        }

        repeat(count) {
            if (byKey.isEmpty()) {
                place(0, 0) // the starting building
                return@repeat
            }

            // every plot still below the cap, with its weight.
            data class Candidate(val x: Int, val y: Int, val w: Int)
            val candidates = ArrayList<Candidate>()
            var totalWeight = 0
            for (x in -r..r) {
                for (y in -r..r) {
                    val idx = byKey[key(x, y)]
                    if (idx != null && buildings[idx].floors >= tier.floorCap) continue
                    val w = plotWeight(x, y, tier.size)
                    candidates.add(Candidate(x, y, w))
                    totalWeight += w
                }
            }
            if (candidates.isEmpty()) return@repeat // fully maxed, bank the rest

            var roll = rng() * totalWeight
            var picked = candidates[candidates.size - 1]
            for (c in candidates) {
                roll -= c.w
                if (roll < 0) { picked = c; break }
            }
            place(picked.x, picked.y)
        }
        return City(buildings)
    }

    fun appliedUpgrades(city: City): Int = city.buildings.sumOf { it.floors }

    fun cityState(sessions: List<StudySession>, city: City): CityStatus {
        val totalMins = totalStudyMins(sessions)
        val earned = earnedUpgrades(totalMins)
        val applied = appliedUpgrades(city)
        return CityStatus(
            totalMins = totalMins,
            earned = earned,
            applied = applied,
            pending = max(earned - applied, 0), // applied can exceed earned only on bad data
            tier = gridTier(totalMins),
        )
    }

    fun reconcile(
        city: City,
        sessions: List<StudySession>,
        rng: () -> Double = { Random.nextDouble() }
    ): City {
        val state = cityState(sessions, city)
        if (state.pending == 0) return city
        return applyUpgrades(city, state.pending, state.tier, rng)
    }
}