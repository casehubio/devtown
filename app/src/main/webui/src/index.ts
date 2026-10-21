import { loadSite, registerPanel } from "@casehubio/pages-runtime";
import type { SiteOptions } from "@casehubio/pages-runtime";
import { page, tabs, hostPanel } from "@casehubio/pages-ui";
import type { PermissionContext } from "@casehubio/pages-component";
import "@casehubio/mosaic-session-workbench";
import "./components/reviewer-workbench";
import "./components/contributor-workbench";
import "./components/pr-cases-workbench";
import "./components/system-workbench";
import { createDatasets } from "./datasets";
import { reviewersView } from "./views/reviewers";
import { definitionsView } from "./views/definitions";

const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;

registerPanel("session-workbench", "blocks-session-workbench");
registerPanel("reviewer-workbench", "devtown-reviewer-workbench");
registerPanel("contributor-workbench", "devtown-contributor-workbench");
registerPanel("pr-cases-workbench", "devtown-pr-cases-workbench");
registerPanel("system-workbench", "devtown-system-workbench");

interface UserInfo {
  actorId: string;
  roles: string[];
  tenancyId: string;
  authenticated: boolean;
}

function authFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  return fetch(input, init).then(r => {
    if (r.status === 401) {
      window.location.href = "/";
    }
    return r;
  });
}

function permissionsFrom(user: UserInfo): PermissionContext {
  const roleSet = new Set(user.roles);
  return {
    hasRole: (role: string) => roleSet.has(role),
    hasPermission: (permission: string) => roleSet.has(permission),
  };
}

async function start() {
  const userRes = await fetch("/api/userinfo");
  if (userRes.status === 401 || !userRes.ok) {
    window.location.href = "/api/userinfo";
    return; // browser navigation triggers OIDC redirect to GitHub login
  }
  const user: UserInfo = await userRes.json();

  const prefs = await authFetch("/api/devtown/governance/preferences")
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
      ["PR Cases", hostPanel("pr-cases-workbench", { endpoint: "/api/devtown/governance" })],
      ["Reviewers", reviewersView],
      ["Contributors", hostPanel("contributor-workbench", { endpoint: "/api/devtown/reviews" })],
      ["Workers", hostPanel("session-workbench", { endpoint: "/api/sessions" })],
      ["System", hostPanel("system-workbench", { endpoint: "/api/devtown/governance" })],
      ["Definitions", definitionsView],
    ),
    { settings: { mode: prefersDark ? "dark" : "light" }, datasets: createDatasets(prefs) },
  );

  const container = document.getElementById("app");
  if (container) {
    const siteOptions: SiteOptions = {
      permissions: permissionsFrom(user),
      fetch: authFetch,
    };
    const site = await loadSite(container, app, siteOptions);
    site.setTheme(prefersDark ? "dark" : "light");

    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
      site.setTheme(e.matches ? "dark" : "light");
    });
  }
}

start().catch(console.error);
