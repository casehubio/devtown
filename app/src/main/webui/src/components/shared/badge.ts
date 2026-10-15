import { html, type TemplateResult } from 'lit';

export type BadgeVariant = 'fast-track' | 'standard' | 'triage' | 'active' | 'emerging' | 'bootstrap' | 'normal' | 'high' | 'critical';

export function renderBadge(label: string, variant: BadgeVariant): TemplateResult {
  return html`<span class="badge ${variant}">${label}</span>`;
}
