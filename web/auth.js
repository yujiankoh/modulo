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
let tokenExpiry = 0;        // timestamp (ms) when the current token goes stale
let tokenClient;
let resolveToken = null;    // used to "wait" for a token
let refreshTimer = null;    // Phase 22: proactive silent-refresh timer
let awaitingSilent = false; // true while a background (prompt:"") refresh is in flight

// Fired whenever the token's validity changes (granted, refreshed, or lapsed) so the
// UI (account chip + sync card) can reflect the TRUTH, not just the chosen mode.
function notifyAuthChanged() {
  window.dispatchEvent(new Event("modulo:authchanged"));
}

// Is there a currently-valid (unexpired) token? The chip/sync card read this so
// "Synced" only shows when Drive calls will actually work.
export function isTokenValid() {
  return !!accessToken && Date.now() < tokenExpiry;
}

// Set up Google's token client. Must run after the GIS library has loaded
// (main.js calls this from window.onload).
export function initTokenClient() {
  tokenClient = google.accounts.oauth2.initTokenClient({
    client_id: CLIENT_ID,
    scope: SCOPES,
    callback: (response) => {
      const wasSilent = awaitingSilent;
      awaitingSilent = false;
      if (response.error) {
        // A SILENT refresh (prompt:"") failing is expected sometimes — no active Google
        // session, or third-party cookies blocked. Stay quiet and let the UI fall back to
        // "Reconnect"; only a user-initiated sign-in error is worth a status message.
        if (!wasSilent) setStatus("Sign-in error: " + response.error);
        notifyAuthChanged();
        if (resolveToken) { resolveToken(false); resolveToken = null; }
        return;
      }
      accessToken = response.access_token;
      tokenExpiry = Date.now() + (response.expires_in - 60) * 1000;
      setStatus("");            // clear any earlier error
      scheduleSilentRefresh();  // keep the session alive in the background
      notifyAuthChanged();
      if (resolveToken) { resolveToken(true); resolveToken = null; }
    },
  });
}

// Ask Google for a token. `silent` = try WITHOUT any popup (prompt:""), which succeeds
// only if the user still has an active Google session + prior consent (used by the
// background refresh). Interactive (silent=false) shows the account chooser and needs a
// user gesture. Resolves true/false once the callback above runs.
export function getToken(silent = false) {
  return new Promise((resolve) => {
    resolveToken = resolve;
    awaitingSilent = silent;
    tokenClient.requestAccessToken(silent ? { prompt: "" } : undefined);
  });
}

// Proactive silent refresh: renew the token in the BACKGROUND shortly before it goes
// stale, so an active-but-idle tab keeps working without ever showing a popup. Doing it
// on a timer (NOT inside ensureToken) is deliberate — a silent-then-interactive fallback
// in one call chain would lose the user gesture and get the popup blocked. If the silent
// renew fails, the callback flips the UI to "Reconnect" and the next user action prompts.
function scheduleSilentRefresh() {
  clearTimeout(refreshTimer);
  // Fire AT the stale moment: on success the callback renews + reschedules (chip stays
  // "Synced"); on failure it fires authchanged so the chip flips to "Reconnect" exactly
  // when the token actually lapses — no window of a falsely-"Synced" chip.
  const delay = Math.max(0, tokenExpiry - Date.now());
  refreshTimer = setTimeout(() => { if (accessToken) getToken(true); }, delay);
}

// Coming back to a backgrounded tab: browsers throttle timers there, so the scheduled
// refresh may not have fired. If the token has lapsed, try a silent renew right away.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && accessToken && Date.now() >= tokenExpiry && !awaitingSilent) {
    getToken(true);
  }
});

// Guarantee a valid, unexpired token before any Drive call. Called from user-initiated
// paths (persist, loadInitialData), so the interactive popup here has a gesture.
export async function ensureToken() {
  if (isTokenValid()) return true;
  return await getToken();  // stale/missing → ask again (interactive)
}

// Build the auth header for a Drive request.
export function authHeaders(extra = {}) {
  return { Authorization: "Bearer " + accessToken, ...extra };
}
