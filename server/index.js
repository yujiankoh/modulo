import "dotenv/config";
import express from "express";
import cors from "cors";
import { GoogleGenAI } from "@google/genai";

const app = express();
app.use(cors());                                // allow web/app (other origins) to call this
app.use(express.json({ limit: "10mb" }));       // images are big — raise the body limit

const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY,
  httpOptions: { timeout: 180000 },             // wait up to 3 min — Gemini parses can exceed the 1-min default
});
const MODEL = "gemini-3.5-flash";               // a fast vision model — verify in AI Studio

function buildPrompt(level) {
  const base = `You are reading a Singapore student's class timetable from an image.
Extract every class/lesson into structured JSON. Use 24-hour times like "14:00".
Each "day" must be one of MON, TUE, WED, THU, FRI, SAT, SUN.
For "sessionType" use one of: lecture, tutorial, lab, recitation, seminar, sectional, lesson.
Map labels: LEC->lecture, TUT->tutorial, LAB->lab, REC->recitation, SEC->sectional, SEM->seminar.
Put any class/group number (the "1" in "LEC [1]", or "31B" in "TUT [31B]") in "classNo".
For "week", capture whether a session runs every week or only on alternating weeks:
- Default to "all" (runs every week) unless the image clearly shows an odd/even split.
- If the WHOLE timetable is headed for one week (e.g. a title like "Odd Week" or "Even Week"), set "week" to "odd" or "even" for EVERY session accordingly.
- If a single cell is labelled for specific weeks: "Even Weeks" (or a list of only even week numbers) -> "even"; "Odd Weeks" (or a list of only odd week numbers, e.g. "Weeks 3, 5, 7, 9, 11") -> "odd"; a continuous range (e.g. "Weeks 3-13"), "every week", or no week label -> "all".
Set "name" only if the subject/module title is actually visible in the image; otherwise "".
Do NOT include non-academic blocks such as flag-raising, assembly, recess, break, lunch, morning reading, dismissal, or administrative periods — only capture actual lessons/classes.
Only include information actually visible in the image — never invent anything.`;

  const perLevel = {
    primary: `LEVEL: Primary school — a weekly grid of periods.
- Each entry is a SUBJECT (English, Mathematics, Mother Tongue, Science, Social Studies, Art, Music, PE, etc.).
- "code" is always "". "classNo" is always "".
- "sessionType" is "lesson", or "cca" for co-curricular activities.
- Capture "location" (the room/venue) only if shown — do NOT include teacher names or initials.`,

    secondary: `LEVEL: Secondary school — a weekly grid of periods.
- Each entry is a SUBJECT (English, E Math, A Math, Physics, Chemistry, Biology, Humanities, Mother Tongue, etc.).
- "code" is always "". "classNo" is always "".
- "sessionType" is "lesson", or "cca" for CCA.
- Capture "location" (the room/venue) only if shown — do NOT include teacher names or initials.`,

    jc: `LEVEL: Junior College — a weekly grid.
- Each entry is a SUBJECT with its level, e.g. "H2 Mathematics", "H1 Economics", "GP", "Project Work".
- "code" is always "".
- "sessionType" is "lecture" or "tutorial" (JC subjects split into both); use "lesson" if unclear.
- Capture "location" (the room/venue) and "classNo" (tutorial group) only if shown — do NOT put teacher names or initials in "location".`,

    poly: `LEVEL: Polytechnic — module-based.
- Each entry is a MODULE with a code and name.
- "sessionType" is one of "lecture", "tutorial", "lab", "practical".
- Capture "classNo" (group/class number) and "location" (venue) if shown — do NOT put teacher names in "location".`,

    university: `LEVEL: University — module-based (e.g. CS2030S).
- Each entry is a MODULE with a code and (if shown) a name.
- "sessionType" is one of "lecture", "tutorial", "lab", "recitation", "seminar".
- Capture "classNo" (the class/group number, e.g. "1", "31B") SEPARATELY — do NOT merge it into sessionType.
- Capture "location" (venue) if shown — the room only, not teacher names.`,
  };

  return `${base}\n\n${perLevel[level] || perLevel.secondary}

Return JSON in exactly this shape:
{
  "modules": [
    {
      "code": "string",
      "name": "string",
      "slots": [
        {
          "day": "MON",
          "start": "09:00",
          "end": "10:00",
          "location": "string",
          "sessionType": "string",
          "classNo": "string",
          "week": "all"
        }
      ]
    }
  ]
}`;
}

// Pull the first balanced {...} JSON object out of a string, ignoring anything
// before or after it (stray notes, repeated chunks, fences). Gemini with
// responseMimeType JSON is usually clean, but not always — especially on large outputs.
function extractJson(text) {
  const start = text.indexOf("{");
  if (start === -1) return text;            // no object found; let JSON.parse report it
  let depth = 0, inStr = false, esc = false;
  for (let i = start; i < text.length; i++) {
    const c = text[i];
    if (inStr) {
      if (esc) esc = false;
      else if (c === "\\") esc = true;
      else if (c === '"') inStr = false;
    } else if (c === '"') inStr = true;
    else if (c === "{") depth++;
    else if (c === "}" && --depth === 0) return text.slice(start, i + 1);
  }
  return text.slice(start);                 // unbalanced; let JSON.parse report it
}

app.post("/parse-timetable", async (req, res) => {
  try {
    const { image, mimeType, educationLevel } = req.body;
    if (!image || !mimeType) {
      return res.status(400).json({ error: "Missing image or mimeType" });
    }

    const response = await ai.models.generateContent({
      model: MODEL,
      contents: [
        { inlineData: { mimeType, data: image } },
        { text: buildPrompt(educationLevel) },
      ],
      config: {
        responseMimeType: "application/json"
      },
    });

    // Gemini occasionally appends stray text after the JSON; extract the JSON
    // object first so a clean parse doesn't choke on trailing characters.
    const parsed = JSON.parse(extractJson(response.text));
    res.json({ educationLevel, modules: parsed.modules || [] });
  } catch (err) {
    console.error("Parse error:", err);
    // Forward the REAL status code so the web can show a specific message instead
    // of a generic failure. @google/genai puts the upstream HTTP code on err.status
    // (e.g. 429 quota, 503 busy, 504 deadline). Our own 3-min client timeout surfaces
    // as an AbortError, which we map to 504 (also a "too slow" case).
    const status = err.name === "AbortError" ? 504 : (err.status || 500);
    res.status(status).json({ error: "Failed to parse timetable" });
  }
});

// Global error handler — the final net for anything that slips past the routes,
// most importantly errors thrown by middleware BEFORE the handler runs (e.g.
// express.json rejecting a too-large or malformed body). The 4-arg signature
// (err, req, res, next) is how Express recognizes an error handler, and it must
// be registered last so it sits at the bottom of the middleware stack.
app.use((err, req, res, next) => {
  console.error("Unhandled error:", err);
  if (res.headersSent) return next(err);   // response already started — let Express finish it
  // body-parser sets err.status/statusCode (413 too large, 400 malformed JSON).
  const status = err.status || err.statusCode || 500;
  res.status(status).json({ error: "Request could not be processed" });
});

// Render provides the port via an environment variable; fall back to 3000 locally.
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Timetable proxy running on port ${PORT}`));