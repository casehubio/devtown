import { html } from 'lit';
import type { TemplateResult } from 'lit';

export interface VitalEntry {
  label: string;
  value: string | number;
}

export function renderVitals(entries: VitalEntry[]): TemplateResult {
  return html`
    <div class="vitals" style="grid-template-columns: repeat(${Math.min(entries.length, 6)}, 1fr)">
      ${entries.map(e => html`
        <div class="vital">
          <div class="vital-value">${e.value}</div>
          <div class="vital-label">${e.label}</div>
        </div>
      `)}
    </div>
  `;
}
