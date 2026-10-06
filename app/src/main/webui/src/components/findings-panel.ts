import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

interface FindingEntry {
  severity: string;
  category: string;
  filePath: string;
  message: string;
  confidence: number;
  startLine: number | null;
  endLine: number | null;
}

const SEVERITY_COLORS: Record<string, string> = {
  CRITICAL: '#dc2626',
  HIGH: '#ea580c',
  MEDIUM: '#d97706',
  LOW: '#2563eb',
  INFO: '#6b7280',
};

function severityOrder(s: string): number {
  const order: Record<string, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3, INFO: 4 };
  return order[s] ?? 5;
}

@customElement('devtown-findings-panel')
export class FindingsPanel extends LitElement {
  @property({ attribute: false }) findings: Record<string, FindingEntry[]> = {};
  @state() private _collapsed = new Set<string>();

  static override styles = css`
    :host { display: block; margin-bottom: 16px; }
    .header { font-size: 13px; font-weight: 600; margin-bottom: 8px; color: var(--pages-neutral-11, #171717); }
    .group {
      border: 1px solid var(--pages-neutral-4, #d4d4d4);
      border-radius: 6px; margin-bottom: 8px; overflow: hidden;
    }
    .group-header {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 12px; cursor: pointer; font-size: 13px; font-weight: 500;
      background: var(--pages-neutral-2, #f5f5f5);
      border-bottom: 1px solid var(--pages-neutral-4, #d4d4d4);
    }
    .group-header:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .count-badge {
      font-size: 11px; padding: 1px 6px; border-radius: 8px;
      background: var(--pages-neutral-5, #a3a3a3); color: white; font-weight: 600;
    }
    .finding {
      padding: 8px 12px; border-bottom: 1px solid var(--pages-neutral-3, #e5e5e5);
      font-size: 12px; display: flex; gap: 8px; align-items: flex-start;
    }
    .finding:last-child { border-bottom: none; }
    .severity-badge {
      font-size: 10px; font-weight: 700; padding: 2px 6px;
      border-radius: 3px; color: white; white-space: nowrap; flex-shrink: 0;
    }
    .file-ref {
      font-family: monospace; font-size: 11px;
      color: var(--pages-accent-9, #2563eb); flex-shrink: 0;
    }
    .message { color: var(--pages-neutral-9, #404040); flex: 1; }
    .empty { font-size: 12px; color: var(--pages-neutral-7, #525252); padding: 8px 0; }
  `;

  private _toggle(cap: string) {
    const next = new Set(this._collapsed);
    if (next.has(cap)) next.delete(cap); else next.add(cap);
    this._collapsed = next;
  }

  override render() {
    const entries = Object.entries(this.findings);
    if (entries.length === 0) return html`<div class="empty">No review findings</div>`;

    const totalCount = entries.reduce((sum, [, fs]) => sum + fs.length, 0);

    return html`
      <div class="header">Review Findings (${totalCount})</div>
      ${entries.map(([cap, fs]) => {
        const collapsed = this._collapsed.has(cap);
        const sorted = [...fs].sort((a, b) => severityOrder(a.severity) - severityOrder(b.severity));
        return html`
          <div class="group">
            <div class="group-header" @click=${() => this._toggle(cap)}>
              <span>${collapsed ? '▸' : '▾'}</span>
              <span>${cap}</span>
              <span class="count-badge">${fs.length}</span>
            </div>
            ${collapsed ? nothing : sorted.map(f => html`
              <div class="finding">
                <span class="severity-badge" style="background:${SEVERITY_COLORS[f.severity] ?? '#6b7280'}">${f.severity}</span>
                <span class="file-ref">${f.filePath}${f.startLine != null ? ':' + f.startLine : ''}</span>
                <span class="message">${f.message}</span>
              </div>
            `)}
          </div>
        `;
      })}
    `;
  }
}
