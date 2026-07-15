// auth.js — Google sign-in + access tokens.
// Everything about the token is PRIVATE to this module; other code reaches it
// only through the exported functions below. That's the "state behind functions"
// idea: you can't import and reassign `accessToken` from elsewhere, so the token
// can only change in here, where the rules live.

import { setStatus } from "./ui.js";

// App's PUBLIC client ID, same as the Android app.
const CLIENT_ID = "332114614658-87cqh1e2u8luh9b5q15sf22sb30i3nda.apps.googleusercontent.com";
// Asking for access to the app's private Drive folder.
const SCOPES = "https://www.googleapis.com/auth/drive.appdata";

let accessToken = null;
let tokenExpiry = 0;       // timestamp (ms) when the current token goes stale
let tokenClient;
let resolveToken = null;   // used to "wait" for a token

// Set up Google's token client. Must run after the GIS library has loaded
// (main.js calls this from window.onload).
export function initTokenClient() {
  tokenClient = google.accounts.oauth2.initTokenClient({
    client_id: CLIENT_ID,
    scope: SCOPES,
    callback: (response) => {
      if (response.error) {
        setStatus("Sign-in error: " + response.error);
        if (resolveToken) { resolveToken(false); resolveToken = null; }
        return;
      }
      accessToken = response.access_token;
      tokenExpiry = Date.now() + (response.expires_in - 60) * 1000;
      // Success needs no status line — the account chip flips to "Google Drive ·
      // Synced" on the datachanged that follows (polish 2026-07-15). A previous
      // error message shouldn't linger, though:
      setStatus("");
      if (resolveToken) { resolveToken(true); resolveToken = null; }
    },
  });
}

// Ask Google for a token. Resolves true/false once the callback above runs.
export function getToken() {
  return new Promise((resolve) => {
    resolveToken = resolve;             // promise is saved here
    tokenClient.requestAccessToken();   // triggers Google; the callback finishes it
  });
}

// Guarantee a valid, unexpired token before any Drive call.
export async function ensureToken() {
  if (accessToken && Date.now() < tokenExpiry) return true;
  return await getToken();                                  // stale/missing → ask again
}

// Build the auth header for a Drive request.
export function authHeaders(extra = {}) {
  return { Authorization: "Bearer " + accessToken, ...extra };
}
