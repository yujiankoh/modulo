package com.example.modulo

import com.example.modulo.helpers.CityLogicHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the generative study-city logic ([CityLogicHelper])
 */
class CityLogicHelperTest {

    /** An rng that returns the given values in order, then repeats the last one. */
    private fun scriptedRng(vararg values: Double): () -> Double {
        var i = 0
        return { values[minOf(i++, values.size - 1)] }
    }

    private val tier5 = CityLogicHelper.GRID_TIERS[0] // size 5, floorCap 5

    private fun sessions(vararg mins: Int) =
        mins.map { StudySession(start = "s", end = "e", durationMins = it) }

    @Test
    fun `totalStudyMins sums durationMins`() {
        assertEquals(0, CityLogicHelper.totalStudyMins(emptyList()))
        assertEquals(30, CityLogicHelper.totalStudyMins(sessions(25, 5)))
    }

    @Test
    fun `earnedUpgrades inverts the n squared plus 9n pacing exactly at boundaries`() {
        // Gaps 10, 12, 14, … so cumulative thresholds are 10, 22, 36, 52, 70.
        assertEquals(0, CityLogicHelper.earnedUpgrades(0))
        assertEquals(0, CityLogicHelper.earnedUpgrades(9))
        assertEquals(1, CityLogicHelper.earnedUpgrades(10))
        assertEquals(1, CityLogicHelper.earnedUpgrades(21))
        assertEquals(2, CityLogicHelper.earnedUpgrades(22))
        assertEquals(3, CityLogicHelper.earnedUpgrades(36))
        assertEquals(3, CityLogicHelper.earnedUpgrades(51))
        assertEquals(4, CityLogicHelper.earnedUpgrades(52))
        assertEquals(30, CityLogicHelper.earnedUpgrades(1200))
    }

    @Test
    fun `gridTier switches land size exactly at 20h and 100h`() {
        assertEquals(5, CityLogicHelper.gridTier(0).size)
        assertEquals(5, CityLogicHelper.gridTier(1199).size)
        assertEquals(7, CityLogicHelper.gridTier(1200).size)
        assertEquals(7, CityLogicHelper.gridTier(5999).size)
        assertEquals(9, CityLogicHelper.gridTier(6000).size)
        assertEquals(5, CityLogicHelper.gridTier(0).floorCap)
        assertEquals(12, CityLogicHelper.gridTier(6000).floorCap)
    }

    @Test
    fun `plotWeight is centre-heavy 27 8 1 on a 5x5 by ring (cubed)`() {
        assertEquals(27, CityLogicHelper.plotWeight(0, 0, 5))  // centre
        assertEquals(8, CityLogicHelper.plotWeight(1, 0, 5))   // inner ring
        assertEquals(8, CityLogicHelper.plotWeight(-1, 1, 5))  // ring by Chebyshev distance
        assertEquals(1, CityLogicHelper.plotWeight(2, 2, 5))   // corner
        assertEquals(64, CityLogicHelper.plotWeight(0, 0, 7))  // bigger land, stronger centre
    }

    @Test
    fun `the founding building always lands dead centre`() {
        val city = CityLogicHelper.applyUpgrades(City(), 1, tier5, scriptedRng(0.99))
        assertEquals(listOf(Building(0, 0, 1)), city.buildings)
    }

    @Test
    fun `occupied pick grows a floor, empty pick spawns (scripted rolls)`() {
        val one = CityLogicHelper.applyUpgrades(
            City(listOf(Building(0, 0, 1))), 1, tier5, scriptedRng(0.5)
        )
        assertEquals(listOf(Building(0, 0, 2)), one.buildings)

        val two = CityLogicHelper.applyUpgrades(
            City(listOf(Building(0, 0, 1))), 1, tier5, scriptedRng(0.0)
        )
        assertEquals(2, two.buildings.size)
        assertEquals(Building(-2, -2, 1), two.buildings[1])
    }

    @Test
    fun `capped plots are excluded from the pick`() {
        val next = CityLogicHelper.applyUpgrades(
            City(listOf(Building(0, 0, 5))), 1, tier5, scriptedRng(0.5)
        )
        val centre = next.buildings.first { it.x == 0 && it.y == 0 }
        assertEquals(5, centre.floors)
        assertEquals(6, CityLogicHelper.appliedUpgrades(next))
    }

    @Test
    fun `a fully maxed grid banks the remaining upgrades`() {
        val full = buildList {
            for (x in -2..2) for (y in -2..2) add(Building(x, y, 5))
        }
        val after = CityLogicHelper.applyUpgrades(City(full), 10, tier5, scriptedRng(0.5))
        assertEquals(125, CityLogicHelper.appliedUpgrades(after)) // unchanged — nothing to apply
    }

    @Test
    fun `applyUpgrades applies count events and never mutates the input`() {
        val input = City(listOf(Building(0, 0, 1)))
        val after = CityLogicHelper.applyUpgrades(input, 5, tier5, scriptedRng(0.5, 0.1, 0.9, 0.3, 0.7))
        assertEquals(listOf(Building(0, 0, 1)), input.buildings) // input untouched
        assertEquals(6, CityLogicHelper.appliedUpgrades(after))           // 1 existing + 5 applied
    }

    @Test
    fun `appliedUpgrades equals total floors`() {
        assertEquals(0, CityLogicHelper.appliedUpgrades(City()))
        assertEquals(4, CityLogicHelper.appliedUpgrades(City(listOf(Building(0, 0, 3), Building(1, 0, 1)))))
    }

    @Test
    fun `progression invariant each tier expands long before its land can fill`() {
        for (i in 0 until CityLogicHelper.GRID_TIERS.size - 1) {
            val tier = CityLogicHelper.GRID_TIERS[i]
            val capacity = tier.size * tier.size * tier.floorCap
            val maxEarnedInTier = CityLogicHelper.earnedUpgrades(CityLogicHelper.GRID_TIERS[i + 1].minMins - 1)
            assertTrue(
                "tier ${tier.size}x${tier.size} can earn $maxEarnedInTier but only holds $capacity",
                maxEarnedInTier < capacity
            )
        }
    }

    @Test
    fun `cityState derives pending as earned minus applied, never negative`() {
        val s = CityLogicHelper.cityState(sessions(36), City(listOf(Building(0, 0, 1))))
        assertEquals(3, s.earned)
        assertEquals(1, s.applied)
        assertEquals(2, s.pending)
        assertEquals(5, s.tier.size)

        val bad = CityLogicHelper.cityState(emptyList(), City(listOf(Building(0, 0, 7))))
        assertEquals(0, bad.pending)
    }

    @Test
    fun `reconcile returns the same city when nothing is pending`() {
        val city = City(listOf(Building(0, 0, 1)))
        assertTrue(CityLogicHelper.reconcile(city, sessions(10)) === city)
    }

    @Test
    fun `reconcile applies all pending events in one pass`() {
        val reconciled = CityLogicHelper.reconcile(City(), sessions(36), scriptedRng(0.5, 0.5, 0.5))
        assertEquals(3, CityLogicHelper.appliedUpgrades(reconciled))
    }
}
