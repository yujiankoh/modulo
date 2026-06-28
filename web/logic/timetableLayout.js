// logic/timetableLayout.js — PURE side-by-side lane assignment for overlapping calendar
// events (so clashing classes sit beside each other instead of stacking). Used by
// timetableView.js; unit-tested in tests/timetableLayout.test.js.

// Greedy lane assignment (Google-calendar style): sort by start; events that overlap form
// a "cluster"; within a cluster each event takes the first free lane, and they all share
// the cluster's lane count. Mutates + returns the events, each annotated with { col, cols }.
export function layoutColumns(events) {
  events.sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);
  let cluster = [];      // events that overlap transitively
  let clusterEnd = -1;   // latest end time seen in the current cluster

  function flush() {
    const laneEnds = [];                 // end time of the last event placed in each lane
    for (const ev of cluster) {
      let lane = laneEnds.findIndex((end) => end <= ev.startMin); // first free lane
      if (lane === -1) { lane = laneEnds.length; laneEnds.push(ev.endMin); }
      else laneEnds[lane] = ev.endMin;
      ev.col = lane;
    }
    for (const ev of cluster) ev.cols = laneEnds.length; // everyone shares the lane count
    cluster = [];
    clusterEnd = -1;
  }

  for (const ev of events) {
    if (cluster.length && ev.startMin >= clusterEnd) flush(); // no overlap → new cluster
    cluster.push(ev);
    clusterEnd = Math.max(clusterEnd, ev.endMin);
  }
  flush();
  return events;
}
