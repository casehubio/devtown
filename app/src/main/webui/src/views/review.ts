import { page, tabs, hostPanel } from "@casehubio/pages-ui";
import type { SiteOptions } from "@casehubio/pages-runtime";
import { loadSite } from "@casehubio/pages-runtime";
import { reviewersView } from "./reviewers";
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
      ["Review Workbench", hostPanel("pr-cases-workbench", {
        endpoint: "/api/devtown/governance",
        ...(initialCaseId ? { initialCaseId } : {}),
      })],
      ["Operations", hostPanel("operations-workbench", { endpoint: "/api/devtown/governance" })],
      ["Merge Queue", hostPanel("merge-queue-workbench", { endpoint: "/api/devtown/governance" })],
      ["Reviewers", reviewersView],
      ["Contributors", hostPanel("contributor-workbench", { endpoint: "/api/devtown/reviews" })],
      ["System", hostPanel("system-workbench", { endpoint: "/api/devtown/governance" })],
    ),
    { settings: { mode: prefersDark ? "dark" : "light" }, datasets: createDatasets(prefs) },
  );

  const site = await loadSite(container, app, siteOptions);
  site.setTheme(prefersDark ? "dark" : "light");

  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
    site.setTheme(e.matches ? "dark" : "light");
  });
}
