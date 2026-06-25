// router.js — the SPA "router". It shows ONE view at a time based on the URL hash and
// highlights the matching nav link. Side-effect imported by main.js.
//
// Hash routing in one breath: the bit of the URL after "#" (e.g. .../index.html#tasks)
// is the "hash". Changing it does NOT reload the page — the browser only fires a
// "hashchange" event — so our in-memory appState and the Google token SURVIVE navigation
// (that's the whole reason we went SPA). Back/forward/refresh work because the hash is
// part of the browser history/URL.

const DEFAULT_ROUTE = "dashboard";

// Read the page's views + nav links once. Using the DOM as the source of truth means we
// never hard-code a list of routes — add a <section class="view"> + a .nav-link and it
// just works.
const views = document.querySelectorAll(".view");
const navLinks = document.querySelectorAll(".nav-link");

// Show the view for `route`, hide the rest, and mark the active nav link. If the hash
// doesn't match a real view (stale/typo'd URL), fall back to the dashboard.
function showView(route) {
  const exists = document.getElementById(`view-${route}`);
  const active = exists ? route : DEFAULT_ROUTE;

  // classList.toggle(name, condition): add the class when condition is true, remove it
  // otherwise. CSS shows only `.view.is-active` and styles the active `.nav-link`.
  for (const view of views) {
    view.classList.toggle("is-active", view.id === `view-${active}`);
  }
  for (const link of navLinks) {
    link.classList.toggle("is-active", link.hash === `#${active}`); // link.hash = "#tasks"
  }
}

// Turn the current URL hash into a route and render it. "#tasks" -> "tasks"; ""-> default.
function route() {
  showView(location.hash.slice(1) || DEFAULT_ROUTE);
}

// React to hash changes (nav clicks, back/forward), and render once now on load.
window.addEventListener("hashchange", route);
route();
