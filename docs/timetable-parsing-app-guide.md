# MODULO — Timetable Parsing: App-Side Guide (for Ling Song)

The parsing proxy is **deployed and live**. This is everything you need to call it from
the Android app and turn a timetable photo into our shared `timetable` data.

- **Live endpoint:** `https://modulo-proxy.onrender.com/parse-timetable`
- **Method:** `POST` · **Content-Type:** `application/json`
- Hosted on Render (free tier). It holds the Gemini key server-side — the app never sees it.
- Data contract for the result: [`docs/modulo-data-schema_v2.md`](modulo-data-schema_v2.md).

---

## 1. What you send (request body)

A JSON object with exactly these three fields:

```json
{
  "image": "<base64 of the image, RAW — no 'data:image/...;base64,' prefix>",
  "mimeType": "image/jpeg",
  "educationLevel": "secondary"
}
```

| Field            | Notes |
|------------------|-------|
| `image`          | The photo encoded as **raw base64** (Android `Base64.encodeToString(bytes, Base64.NO_WRAP)`). **Do not** include the `data:` URI prefix — the server passes the string straight to Gemini. |
| `mimeType`       | The image's real type: `image/jpeg`, `image/png`, or `image/webp`. |
| `educationLevel` | One of: `primary`, `secondary`, `jc`, `poly`, `university`. This picks the per-level prompt, so it must be correct. |

---

## 2. What you get back (response body)

```json
{
  "educationLevel": "secondary",
  "modules": [
    {
      "code": "",
      "name": "HMT/MT1",
      "slots": [
        { "day": "MON", "start": "08:00", "end": "09:00", "location": "ML Rm 1", "sessionType": "lesson", "classNo": "" }
      ]
    }
  ]
}
```

**This response IS our `timetable` object.** Drop it straight into `ModuloData.timetable`
(it already has the `educationLevel` + `modules` shape the schema expects).

On error, you get a non-200 status with `{ "error": "..." }` — see Gotchas below.

---

## 3. How it maps to our data classes

The response decodes directly into the **schema v2** classes already in the schema doc —
no new types needed. Decode with **`Json { ignoreUnknownKeys = true }`** so future fields
don't crash old code:

```kotlin
val json = Json { ignoreUnknownKeys = true }
val timetable = json.decodeFromString<Timetable>(responseBodyString)
// then: moduloData = moduloData.copy(timetable = timetable, ...)
```

`Timetable` / `Module` / `Slot` are defined in [`modulo-data-schema_v2.md`](modulo-data-schema_v2.md).
Because every `Slot` field except `day/start/end` has a default, a partial response still
decodes cleanly. (Note: the proxy doesn't emit `teacher` yet, so it'll be `""` for now.)

---

## 4. Your follow-up steps (checklist)

1. [ ] **Pick/take an image** in the app and read its bytes.
2. [ ] **Base64-encode** the bytes (`Base64.NO_WRAP`) and detect the `mimeType`.
3. [ ] **POST** the JSON body (§1) to the endpoint using your HTTP client.
4. [ ] **Decode** the response into `Timetable` (§3).
5. [ ] **Store** it: `moduloData.timetable = <decoded>`, set `educationLevel`, update `updatedAt`.
6. [ ] **Sync** the updated `ModuloData` to Drive — *depends on your Drive write* (the
       `// TODO` in `AuthenticationHelper.kt`). Until that's done, you can store locally.

### Kotlin sketch (adapt to your actual HTTP stack)

```kotlin
// Using OkHttp + kotlinx.serialization — swap for Ktor/Retrofit if that's what the app uses.
val body = json.encodeToString(
    ParseRequest(image = base64Image, mimeType = "image/jpeg", educationLevel = level)
).toRequestBody("application/json".toMediaType())

val request = Request.Builder()
    .url("https://modulo-proxy.onrender.com/parse-timetable")
    .post(body)
    .build()

// Use a GENEROUS timeout — see cold start below.
client.newCall(request).execute().use { resp ->
    if (!resp.isSuccessful) { /* handle 429 / 504 / other — see Gotchas */ }
    val timetable = json.decodeFromString<Timetable>(resp.body!!.string())
}

@Serializable
data class ParseRequest(val image: String, val mimeType: String, val educationLevel: String)
```

---

## 5. Gotchas (please read — these will bite otherwise)

- **Cold start.** The free Render instance sleeps after ~15 min idle; the first request
  then takes **30–50s to wake up**, *before* parsing. Set your client timeout **generous
  (120s+)** and show a spinner. Hit the endpoint once to warm it up before a demo.

- **⚠️ Shared daily quota — 20 parses/day for the WHOLE project.** Gemini's free tier
  allows only **20 parses per day**, and it's shared across web *and* app (same Google
  project). Your testing and YJ's testing draw from the same 20. **Be sparing while
  testing**, and coordinate. (YJ is looking at enabling billing before the 29 Jun eval to
  lift this — until then, 20/day total.)

- **Error handling.** Map the failure to a friendly message:
  | Status | Meaning | Show the user |
  |--------|---------|---------------|
  | `429`  | Daily quota exhausted | "Parsing limit reached for today, try again later." |
  | `504` / timeout | Gemini was slow (usually transient) | "Taking too long — please retry." |
  | `400`  | Missing `image`/`mimeType` | (your bug — fix the request) |
  | other 5xx | Server/parse error | "Couldn't read the timetable, try another photo." |

- **`teacher` is `""` for now.** The proxy doesn't emit it yet; don't rely on it.

- **`educationLevel` must be one of the five exact strings** — a wrong/empty value makes
  the parser fall back to the secondary prompt.

---

## 6. Quick test plan

1. Warm the endpoint (one throwaway request, expect ~30–50s the first time).
2. Send one real timetable photo at the correct `educationLevel`.
3. Confirm the JSON decodes into `Timetable` with sensible `modules`/`slots`.
4. Store + (once Drive write works) sync, then verify the web app sees the same timetable.

Questions or a field that won't decode → ping YJ. Keep an eye on the shared 20/day quota.
```
