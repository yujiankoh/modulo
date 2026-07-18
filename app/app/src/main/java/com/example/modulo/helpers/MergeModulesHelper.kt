package com.example.modulo.helpers

import com.example.modulo.Module
import com.example.modulo.Slot

/**
 * Pure timetable merge for "Add another week", combines an odd-week and an even-week upload into one timetable.
 */
object MergeModulesHelper {
    fun sameSlot(a: Slot, b: Slot): Boolean =
        a.day == b.day && a.start == b.start && a.end == b.end &&
            a.location == b.location && a.sessionType == b.sessionType &&
            a.classNo == b.classNo && a.week == b.week

    fun mergeModules(existing: List<Module>, incoming: List<Module>): List<Module> {
        val result = existing.toMutableList()
        for (inc in incoming) {
            val idx = result.indexOfFirst { it.code == inc.code && it.name == inc.name }
            if (idx == -1) {
                result.add(inc)
            } else {
                val current = result[idx]
                val newSlots = inc.slots.filter { slot -> current.slots.none { sameSlot(it, slot) } }
                result[idx] = current.copy(slots = current.slots + newSlots)
            }
        }
        return result
    }
}