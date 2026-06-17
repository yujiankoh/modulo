// ui.js — tiny shared UI helpers.
// This is a "leaf" module: it imports nothing, so anyone can import it without
// creating a dependency loop. Right now it just owns the status line.

const statusEl = document.getElementById("status");

// Write a message to the status line under the heading.
export function setStatus(text) {
  statusEl.textContent = text;
}
