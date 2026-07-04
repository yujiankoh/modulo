// tests/proxy.test.js — tests for the Gemini parsing proxy (server/index.js).
// Run via `npm test` (Node's built-in runner). Covers:
//   UNIT:        buildPrompt() and extractJson() — pure functions.
//   INTEGRATION: the POST /parse-timetable route, exercised over real HTTP, checking it
//                validates input BEFORE calling Gemini (so this test spends no quota).

import { test } from "node:test";
import assert from "node:assert/strict";
import { app, buildPrompt, extractJson } from "../server/index.js";

// UNIT: buildPrompt

test("buildPrompt includes the level-specific section", () => {
  const uni = buildPrompt("university");
  assert.match(uni, /LEVEL: University/);
  assert.match(uni, /recitation/);             // a uni-only session type
  const sec = buildPrompt("secondary");
  assert.match(sec, /LEVEL: Secondary school/);
});

test("buildPrompt falls back to the secondary rules for an unknown/missing level", () => {
  // perLevel[level] || perLevel.secondary, so both resolve to the same prompt.
  assert.equal(buildPrompt("not-a-level"), buildPrompt(undefined));
});

// UNIT: extractJson (balanced-brace extraction)

test("extractJson pulls the first balanced object out of surrounding noise", () => {
  assert.equal(extractJson('blah blah {"a":1,"b":2} trailing text'), '{"a":1,"b":2}');
});

test("extractJson ignores braces that appear inside strings", () => {
  const obj = '{"name":"room {A}{B}"}';
  assert.equal(extractJson("prefix " + obj), obj);
});

// INTEGRATION: the Express route

test("POST /parse-timetable returns 400 when image/mimeType are missing", async () => {
  const server = app.listen(0);                // 0 = let the OS pick a free port
  const { port } = server.address();
  try {
    const res = await fetch(`http://localhost:${port}/parse-timetable`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ educationLevel: "university" }), // no image → must 400
    });
    assert.equal(res.status, 400);
    const body = await res.json();
    assert.match(body.error, /Missing image/);
  } finally {
    server.close();                            // always release the port
  }
});
