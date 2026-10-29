import { registerPanel } from "@casehubio/pages-runtime";
import type { PermissionContext } from "@casehubio/pages-component";
import "./components/reviewer-workbench";
import "./components/contributor-workbench";
import "./components/pr-cases-workbench";
import "./components/system-workbench";
import "./components/operations-workbench";
import "./components/review-spine";
import "./components/intelligence-pane";
import "./components/merge-queue-workbench";

const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;

registerPanel("reviewer-workbench", "devtown-reviewer-workbench");
registerPanel("contributor-workbench", "devtown-contributor-workbench");
registerPanel("pr-cases-workbench", "devtown-pr-cases-workbench");
registerPanel("system-workbench", "devtown-system-workbench");
registerPanel("operations-workbench", "devtown-operations-workbench");
registerPanel("merge-queue-workbench", "devtown-merge-queue-workbench");

interface UserInfo {
  actorId: string;
  roles: string[];
  tenancyId: string;
  authenticated: boolean;
}

function authFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  return fetch(input, init).then(r => {
    if (r.status === 401) {
      window.location.href = "/api/userinfo";
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
    return;
  }
  const user: UserInfo = await userRes.json();

  const container = document.getElementById("app");
  if (!container) return;

  if (window.location.pathname.startsWith("/evolution")) {
    const { mountEvolutionPage } = await import("./views/evolution");
    mountEvolutionPage(container, user.tenancyId, prefersDark);
  } else {
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

    const initialCaseId = new URLSearchParams(window.location.search).get("caseId");

    const { mountReviewDashboard } = await import("./views/review");
    await mountReviewDashboard(container, {
      permissions: permissionsFrom(user),
      fetch: authFetch,
    }, prefs, prefersDark, initialCaseId);
  }
}

start().catch(console.error);
