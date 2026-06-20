// timetable.js — pick a timetable photo, downscale it, send it to the proxy to parse.
// Depends on data.js for shared state + save + redraw.

import { appState, persist, render } from "./data.js";

let selectedImage = null;   // will hold { base64, mimeType } once an image is chosen

// Education level: save the choice so it syncs and the parser can use it.
const eduLevelEl = document.getElementById("eduLevel");
eduLevelEl.addEventListener("change", () => {
  appState.educationLevel = eduLevelEl.value || null;
  persist();
});

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

imageInput.addEventListener("change", async () => {
  const file = imageInput.files[0];
  if (!file) return;

  const dataUrl = await downscaleToDataURL(file);   // shrink before preview + parse
  previewEl.src = dataUrl;             // a data URL works directly as an <img> source
  previewEl.style.display = "block";

  // Gemini wants the mime type and the raw base64 separately, so split the data URL:
  // "data:image/jpeg;base64,AAAA..."  ->  meta="data:image/jpeg;base64"  data="AAAA..."
  const [meta, base64] = dataUrl.split(",");
  const mimeType = meta.match(/data:(.*);base64/)[1];
  selectedImage = { base64, mimeType };

  document.getElementById("parseOutput").textContent =
    `Image ready (downscaled ${mimeType}, ~${Math.round(base64.length / 1024)} KB). Parsing is the next step.`;
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

document.getElementById("parseBtn").addEventListener("click", async () => {
  if (!appState.educationLevel) { alert("Choose your education level first."); return; }
  if (!selectedImage) { alert("Choose a timetable image first."); return; }

  const out = document.getElementById("parseOutput");
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
    // fetch() does NOT throw on HTTP errors (429/503/504/...), so check res.ok
    // and map the status to a clear message.
    if (!res.ok) {
      out.textContent = parseErrorMessage(res.status);
      return;
    }
    const data = await res.json();

    // store the parsed result in state (same v2 shape the manual editor saves)
    appState.timetable = { educationLevel: appState.educationLevel || "", modules: data.modules };
    await persist();                                  // save it (Drive or local)
    render();
    out.textContent = `Parsed ${data.modules.length} module(s):\n` +
      JSON.stringify(data.modules, null, 2);
  } catch (err) {
    // Only reached on a network-level failure (server unreachable, offline, CORS) —
    // HTTP error statuses are handled above, not here.
    out.textContent = "Can't reach the server — check your connection and try again.";
  }
});
