import "@casehubio/mosaic-evolution-workbench";

export function mountEvolutionPage(
  container: HTMLElement,
  tenancyId: string,
  prefersDark: boolean,
): void {
  const nav = document.createElement("nav");
  nav.style.cssText = "padding: 8px 16px; border-bottom: 1px solid var(--border-color, #e0e0e0);";
  const link = document.createElement("a");
  link.href = "/";
  link.textContent = "← DevTown Review";
  link.style.cssText = "color: var(--link-color, #0066cc); text-decoration: none; font-size: 14px;";
  nav.appendChild(link);

  const workbench = document.createElement("agency-evolution-workbench");
  workbench.setAttribute("endpoint", "/api/devtown/evolution");
  workbench.setAttribute("tenancy-id", tenancyId);
  workbench.style.cssText = "display: block; height: calc(100vh - 41px);";

  if (prefersDark) {
    workbench.setAttribute("theme", "dark");
  }

  container.appendChild(nav);
  container.appendChild(workbench);
}
