import { loadSite } from "@casehubio/pages-runtime";
import { page, tabs } from "@casehubio/pages-ui";
import { datasets } from "./datasets";
import { themes, ThemeMode } from "./theme";
import { operationsView } from "./views/operations";
import { reviewsView } from "./views/reviews";
import { queueView } from "./views/queue";
import { reviewersView } from "./views/reviewers";
import { triageView } from "./views/triage";
import { systemView } from "./views/system";
import { definitionsView } from "./views/definitions";

const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
const initialMode: ThemeMode = prefersDark ? "dark" : "light";

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
  { settings: { mode: initialMode }, datasets },
);

const container = document.getElementById("app");
if (container) {
  loadSite(container, app).then(site => {
    site.setTheme(themes[initialMode]);

    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
      const mode: ThemeMode = e.matches ? "dark" : "light";
      site.setTheme(themes[mode]);
    });
  }).catch(console.error);
}
