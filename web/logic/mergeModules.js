// logic/mergeModules.js — PURE timetable merge (used when "Add another week" combines an
// odd-week and an even-week upload into one timetable). No DOM/appState; unit-tested in
// tests/mergeModules.test.js. Used by timetable.js.

// Two slots are the same if every field matches (incl. week). Used to skip duplicates
// when the same timetable is uploaded twice by accident.
export function sameSlot(a, b) {
  return a.day === b.day && a.start === b.start && a.end === b.end &&
    a.location === b.location && a.sessionType === b.sessionType &&
    a.classNo === b.classNo && a.week === b.week;
}

// Merge incoming modules into existing ones, matching by code+name so the same subject
// across odd/even images becomes ONE module holding both weeks' slots. Exact-duplicate
// slots are skipped (re-uploading the same image adds nothing). Does not mutate inputs.
export function mergeModules(existing, incoming) {
  const result = existing.map((m) => ({ ...m, slots: [...m.slots] })); // clone, don't mutate
  for (const inc of incoming) {
    let match = result.find((m) => m.code === inc.code && m.name === inc.name);
    if (!match) {
      match = { ...inc, slots: [] };
      result.push(match);
    }
    for (const slot of inc.slots) {
      if (!match.slots.some((s) => sameSlot(s, slot))) {
        match.slots.push(slot);   // add only if not already present
      }
    }
  }
  return result;
}
