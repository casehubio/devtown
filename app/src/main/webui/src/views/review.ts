import { page, tabs, hostPanel } from "@casehubio/pages-ui";
import type { SiteOptions } from "@casehubio/pages-runtime";
import { loadSite } from "@casehubio/pages-runtime";
import { reviewersView } from "./reviewers";
import { definitionsView } from "./definitions";
import { createDatasets } from "../datasets";

export async function mountReviewDashboard(
  container: HTMLElement,
  siteOptions: SiteOptions,
  prefs: Record<string, string>,
  prefersDark: boolean,
  initialCaseId: string | null,
): Promise<void> {
  const app = page("DevTown",
    tabs(
      ["PR Cases", hostPanel("pr-cases-workbench", {
        endpoint: "/api/devtown/governance",
        ...(initialCaseId ? { initialCaseId } : {}),
      })],
      ["Reviewers", reviewersView],
      ["Contributors", hostPanel("contributor-workbench", { endpoint: "/api/devtown/reviews" })],
      ["Workers", hostPanel("session-workbench", { endpoint: "/api/sessions" })],
      ["System", hostPanel("system-workbench", { endpoint: "/api/devtown/governance" })],
      ["Definitions", definitionsView],
    ),
    { settings: { mode: prefersDark ? "dark" : "light" }, datasets: createDatasets(prefs) },
  );

  const site = await loadSite(container, app, siteOptions);
  site.setTheme(prefersDark ? "dark" : "light");

  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
    site.setTheme(e.matches ? "dark" : "light");
  });
}
