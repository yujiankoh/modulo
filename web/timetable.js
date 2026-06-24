// timetable.js — pick a timetable photo, downscale it, send it to the proxy to parse.
// Depends on data.js for shared state + save + redraw.

import { appState, persist } from "./data.js";

let selectedImage = null;   // will hold { base64, mimeType } once an image is chosen

// Education level: save the choice so it syncs and the parser can use it.
const eduLevelEl = document.getElementById("eduLevel");
eduLevelEl.addEventListener("change", () => {
  appState.educationLevel = eduLevelEl.value || null;
  persist();
});

// Term-start anchor (Week 1 Monday): save it so the weekly grid can show real dates.
const termStartEl = document.getElementById("termStart");
termStartEl.addEventListener("change", () => {
  appState.termStart = termStartEl.value || null;
  persist();
});

// Term end: weeks past it are "outside term" (no week number, no classes).
const termEndEl = document.getElementById("termEnd");
termEndEl.addEventListener("change", () => {
  appState.termEnd = termEndEl.value || null;
  persist();
});

// Recess / holiday breaks: a list of { start, end } date ranges. Stored on appState.breaks;
// each is a removable chip. Weeks overlapping a range are skipped in academic numbering.
const breakStartEl = document.getElementById("breakStart");
const breakEndEl = document.getElementById("breakEnd");
const breakListEl = document.getElementById("breakList");

document.getElementById("addBreakBtn").addEventListener("click", () => {
  const start = breakStartEl.value, end = breakEndEl.value;
  if (!start || !end) { alert("Pick both a start and end date for the break."); return; }
  if (end < start) { alert("The break's end can't be before its start."); return; } // ISO strings compare chronologically
  if (!appState.breaks) appState.breaks = [];
  appState.breaks.push({ start, end });
  breakStartEl.value = "";
  breakEndEl.value = "";
  persist();
});

function renderBreakList() {
  breakListEl.innerHTML = "";
  const breaks = (appState.breaks || []).slice()
    .sort((a, b) => a.start.localeCompare(b.start)); // sort a copy, earliest first
  for (const b of breaks) {
    const chip = document.createElement("span");
    chip.className = "break-chip";
    chip.textContent = `${b.start} → ${b.end}`;
    const rm = document.createElement("button");
    rm.type = "button";
    rm.textContent = "✕";
    rm.addEventListener("click", () => {
      appState.breaks = (appState.breaks || [])
        .filter((x) => !(x.start === b.start && x.end === b.end));
      persist();
    });
    chip.append(rm);
    breakListEl.append(chip);
  }
}
// Redraw the chips whenever state changes (incl. after initial load + add/remove).
window.addEventListener("modulo:datachanged", renderBreakList);

// Read an image file, downscale it so its longest side is <= maxSide, and return a
// JPEG data URL. Phone photos are ~3500px/several MB; a timetable is just a grid of
// text, so shrinking keeps it legible while making the upload + Gemini parse lighter
// and less timeout-prone. Pipeline: FileReader -> Image (decode) -> canvas (scaled
// draw) -> toDataURL (re-encode). Wrapped in a Promise so we can await it.
function downscaleToDataURL(file, maxSide = 2000, quality = 0.9) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("Could not load that image."));
      img.onload = () => {
        // Only ever shrink, never enlarge: scale is 1 if the image is already small.
        const longest = Math.max(img.width, img.height);
        const scale = longest > maxSide ? maxSide / longest : 1;
        const w = Math.round(img.width * scale);
        const h = Math.round(img.height * scale);

        const canvas = document.createElement("canvas");
        canvas.width = w;
        canvas.height = h;
        // drawImage with explicit w/h does the actual resampling/downscale.
        canvas.getContext("2d").drawImage(img, 0, 0, w, h);

        // Re-encode the smaller canvas as JPEG (much smaller than PNG for photos).
        resolve(canvas.toDataURL("image/jpeg", quality));
      };
      img.src = reader.result;  // the original file as a data URL feeds the Image
    };
    reader.readAsDataURL(file);
  });
}

// When the user picks an image: downscale, preview it, and prep it for parsing.
const imageInput = document.getElementById("timetableImage");
const previewEl = document.getElementById("timetablePreview");

// Set by "Add another week" so the next image pick parses + appends, instead of
// just previewing and waiting for a Parse click.
let appendOnNextPick = false;
// Set by the calendar's "Re-upload timetable" button: next pick parses + replaces.
let replaceOnNextPick = false;

// Called by the calendar view's "Re-upload timetable" button.
export function startReupload() {
  replaceOnNextPick = true;
  imageInput.value = "";   // reset so re-picking the same filename still fires "change"
  imageInput.click();      // open the file dialog
}

imageInput.addEventListener("change", async () => {
  const file = imageInput.files[0];
  if (!file) { appendOnNextPick = false; return; }

  const dataUrl = await downscaleToDataURL(file);   // shrink before preview + parse
  previewEl.src = dataUrl;             // a data URL works directly as an <img> source
  previewEl.style.display = "block";

  // Gemini wants the mime type and the raw base64 separately, so split the data URL:
  // "data:image/jpeg;base64,AAAA..."  ->  meta="data:image/jpeg;base64"  data="AAAA..."
  const [meta, base64] = dataUrl.split(",");
  const mimeType = meta.match(/data:(.*);base64/)[1];
  selectedImage = { base64, mimeType };

  document.getElementById("parseOutput").textContent =
    `Image ready (downscaled ${mimeType}, ~${Math.round(base64.length / 1024)} KB).`;

  // If a button started this pick, parse straight away: append or replace.
  if (appendOnNextPick) {
    appendOnNextPick = false;
    const modules = await parseSelectedImage();
    if (!modules) return;
    const merged = mergeModules(appState.timetable?.modules || [], modules);
    await saveTimetableModules(merged, `Added another week — ${merged.length} module(s) total:`);
  } else if (replaceOnNextPick) {
    replaceOnNextPick = false;
    const modules = await parseSelectedImage();
    if (!modules) return;
    await saveTimetableModules(modules, `Parsed ${modules.length} module(s):`);
  }
});

// Parse button: sends the image to the deployed proxy, which calls Gemini and
// returns parsed modules.
const PROXY_URL = "https://modulo-proxy.onrender.com/parse-timetable";

// Map a proxy/Gemini HTTP status to a clear, student-friendly message.
function parseErrorMessage(status) {
  switch (status) {
    case 400: return "Couldn't read that image — pick a clearer timetable photo.";
    case 413: return "That image is too large — try a smaller, clearer photo.";
    case 429: return "Parsing limit reached for today — please try again later.";
    case 503: return "The parser is busy right now — try again in a moment.";
    case 504: return "That took too long — try again (a smaller/clearer photo helps).";
    default:  return "Something went wrong reading the timetable — try again or another photo.";
  }
}

// Send the selected image to the proxy. On success returns the parsed modules
// array; on any failure it shows the message itself and returns null. Shared by
// both the Parse (replace) and Add-another-week (append) buttons.
async function parseSelectedImage() {
  const out = document.getElementById("parseOutput");
  if (!appState.educationLevel) { alert("Choose your education level first."); return null; }
  if (!selectedImage) { alert("Choose a timetable image first."); return null; }

  out.textContent = "Parsing… (can take a few seconds)";
  try {
    const res = await fetch(PROXY_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        image: selectedImage.base64,
        mimeType: selectedImage.mimeType,
        educationLevel: appState.educationLevel,
      }),
    });
    // fetch() does NOT throw on HTTP errors (429/503/504/...), so check res.ok.
    if (!res.ok) {
      out.textContent = parseErrorMessage(res.status) +
        "\nYou can still enter your timetable manually below.";
      return null;
    }
    const data = await res.json();
    return data.modules || [];
  } catch (err) {
    // Only reached on a network-level failure (server unreachable, offline, CORS).
    out.textContent = "Can't reach the server — check your connection and try again." +
      "\nYou can still enter your timetable manually below.";
    return null;
  }
}

// Two slots are the same if every field matches (incl. week). Used to skip
// duplicates when the same timetable is uploaded twice by accident.
function sameSlot(a, b) {
  return a.day === b.day && a.start === b.start && a.end === b.end &&
    a.location === b.location && a.sessionType === b.sessionType &&
    a.classNo === b.classNo && a.week === b.week;
}

// Merge incoming modules into existing ones, matching by code+name so the same
// subject across odd/even images becomes ONE module holding both weeks' slots.
// Exact-duplicate slots are skipped (re-uploading the same image adds nothing).
function mergeModules(existing, incoming) {
  const result = existing.map((m) => ({ ...m, slots: [...m.slots] })); // clone, don't mutate state
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

async function saveTimetableModules(modules, message) {
  appState.timetable = { educationLevel: appState.educationLevel || "", modules };
  await persist();   // fires modulo:datachanged → the timetable views redraw themselves
  document.getElementById("parseOutput").textContent =
    `${message}\n` + JSON.stringify(modules, null, 2);
}

// Parse → REPLACE the timetable (the normal, single-image flow).
document.getElementById("parseBtn").addEventListener("click", async () => {
  const modules = await parseSelectedImage();
  if (!modules) return;
  await saveTimetableModules(modules, `Parsed ${modules.length} module(s):`);
});

// Add another week → prompt for a NEW image; the picker's change handler then
// parses + appends it. (It does NOT re-parse the already-selected image.)
document.getElementById("addWeekBtn").addEventListener("click", () => {
  appendOnNextPick = true;
  imageInput.value = "";   // reset so re-picking even the same filename still fires "change"
  imageInput.click();      // open the file dialog
});
