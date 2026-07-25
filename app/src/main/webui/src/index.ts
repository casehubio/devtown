import { loadSite } from "@casehubio/pages-runtime";
import { page, tabs } from "@casehubio/pages-ui";
import { datasets } from "./datasets";

import { operationsView } from "./views/operations";
import { reviewsView } from "./views/reviews";
import { queueView } from "./views/queue";
import { reviewersView } from "./views/reviewers";
import { triageView } from "./views/triage";
import { systemView } from "./views/system";
import { definitionsView } from "./views/definitions";

const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;

const app = page("DevTown",
  tabs(
    ["Operations", operationsView],
    ["Reviews", reviewsView],
    ["Merge Queue", queueView],
    ["Reviewers", reviewersView],
    ["Triage", triageView],
    ["System", systemView],
    ["Definitions", definitionsView],
  ),
  { settings: { mode: prefersDark ? "dark" : "light" }, datasets },
);

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).then(site => {
    site.setTheme(prefersDark ? "dark" : "light");

    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
      site.setTheme(e.matches ? "dark" : "light");
    });
  }).catch(console.error);
}
