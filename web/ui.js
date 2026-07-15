// ui.js — tiny shared UI helpers.
// This is a "leaf" module: it imports nothing, so anyone can import it without
// creating a dependency loop. Right now it just owns the status line.

const statusEl = document.getElementById("status");

// Write a message to the topbar status line. Since the account chip took over the
// persistent mode text (polish 2026-07-15), this is for TRANSIENT/actionable messages
// only (sign-in errors, "please reconnect") — hidden entirely while empty.
export function setStatus(text) {
  statusEl.textContent = text;
  statusEl.style.display = text ? "" : "none";
}
