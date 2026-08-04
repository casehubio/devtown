import { loadSite, registerPanel } from "@casehubio/pages-runtime";
import { page, tabs, hostPanel } from "@casehubio/pages-ui";
import "@casehubio/blocks-ui-session-workbench";
import "@casehubio/blocks-ui-trust-workbench";
import "./components/reviewer-workbench";
import "@casehubio/blocks-ui-contributor-workbench";
import { createDatasets } from "./datasets";
import { operationsView } from "./views/operations";
import { reviewsView } from "./views/reviews";
import { queueView } from "./views/queue";
import { reviewersView } from "./views/reviewers";
import { contributorsView } from "./views/contributors";
import { triageView } from "./views/triage";
import { systemView } from "./views/system";
import { definitionsView } from "./views/definitions";

const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;

registerPanel("session-workbench", "blocks-session-workbench");
registerPanel("trust-workbench", "blocks-trust-workbench");
registerPanel("reviewer-workbench", "devtown-reviewer-workbench");
registerPanel("contributor-workbench", "blocks-contributor-workbench");

async function start() {
  const prefs = await fetch("/api/governance/preferences")
    .then(r => r.ok ? r.json() : {})
    .then(json => {
      const flat: Record<string, string> = {};
      for (const [section, values] of Object.entries(json)) {
        for (const [key, val] of Object.entries(values as Record<string, string>)) {
          flat[`${section}.${key}`] = val;
        }
      }
      return flat;
    })
    .catch(() => ({}));

  const app = page("DevTown",
    tabs(
      ["Operations", operationsView],
      ["Reviews", reviewsView],
      ["Merge Queue", queueView],
      ["Reviewers", reviewersView],
      ["Contributors", contributorsView],
      ["Workers", hostPanel("session-workbench", { endpoint: "/api/sessions" })],
      ["Triage", triageView],
      ["System", systemView],
      ["Definitions", definitionsView],
    ),
    { settings: { mode: prefersDark ? "dark" : "light" }, datasets: createDatasets(prefs) },
  );

  const container = document.getElementById("app");
  if (container) {
    const site = await loadSite(container, app);
    site.setTheme(prefersDark ? "dark" : "light");

    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
      site.setTheme(e.matches ? "dark" : "light");
    });
  }
}

start().catch(console.error);
