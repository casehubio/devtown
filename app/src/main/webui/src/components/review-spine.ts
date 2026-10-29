import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { emitPagesEvent } from '@casehubio/pages-data';
import { synthesizeSpineNodes, type SpineNode } from './spine-node-strategy.js';
import type { UnifiedPrRow } from './pr-cases-workbench.js';

export function shouldExpand(row: { stage: string; status: string }): boolean {
  if (row.stage === 'HUMAN_GATE') return true;
  if (row.stage === 'REVIEW' && row.status !== 'APPROVED' && row.status !== 'COMPLETED') return true;
  return false;
}

export function borderColor(stage: string, status: string): string {
  if (status === 'APPROVED' || status === 'COMPLETED') return 'var(--pages-success-6, #16a34a)';
  if (stage === 'HUMAN_GATE') return 'var(--pages-danger-6, #dc2626)';
  if (stage === 'REVIEW' && status === 'ANALYSING') return 'var(--pages-info-6, #2563eb)';
  if (stage === 'REVIEW') return 'var(--pages-warning-6, #d97706)';
  return 'var(--pages-neutral-6, #737373)';
}

function statusText(stage: string, status: string): string {
  if (status === 'APPROVED' || status === 'COMPLETED') return 'approved';
  if (stage === 'HUMAN_GATE') return 'needs decision';
  if (stage === 'REVIEW' && status === 'ANALYSING') return 'analysing';
  if (stage === 'REVIEW') return 'in review';
  return status.toLowerCase();
}

interface CaseEvents {
  nodes: SpineNode[];
  loading: boolean;
}

@customElement('review-spine')
export class ReviewSpine extends LitElement {
  @property({ type: Array }) cases: UnifiedPrRow[] = [];
  @property({ type: String }) endpoint = '';
  @property({ type: String, attribute: 'selected-case-id' }) selectedCaseId = '';

  @state() private _expandedCases = new Set<string>();
  @state() private _caseEvents = new Map<string, CaseEvents>();
  @state() private _manualOverrides = new Set<string>();

  static override styles = css`
    :host { display: block; }
    .case-row {
      background: var(--pages-neutral-2, #f5f5f5);
      padding: 10px 12px;
      border-radius: 4px;
      margin-bottom: 4px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: space-between;
      transition: background 0.15s;
    }
    .case-row:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .case-row.selected { background: var(--pages-primary-2, #dbeafe); }
    .case-header-content { display: flex; align-items: center; gap: 10px; }
    .expand-icon { color: var(--pages-neutral-6, #737373); font-size: 10px; min-width: 12px; }
    .case-title { font-size: 12px; font-weight: 600; color: var(--pages-neutral-9, #171717); }
    .case-meta { font-size: 10px; color: var(--pages-neutral-7, #525252); }
    .status-indicator {
      width: 8px; height: 8px; border-radius: 50%; display: inline-block;
    }
    .events-panel {
      padding: 6px 0 6px 22px;
      border-left: 2px solid var(--pages-neutral-4, #d4d4d4);
      margin-left: 12px;
    }
    .event-node {
      font-size: 11px;
      margin: 4px 0;
      padding: 2px 6px;
      cursor: pointer;
      border-radius: 3px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      transition: background 0.1s;
    }
    .event-node:hover { background: var(--pages-neutral-3, #e5e5e5); }
    .event-node.selected { background: var(--pages-primary-2, #dbeafe); }
    .event-node.active { color: var(--pages-warning-8, #92400e); }
    .event-node.pending { color: var(--pages-neutral-6, #737373); }
    .event-node.completed { color: var(--pages-success-8, #166534); }
    .event-label { display: flex; align-items: center; gap: 6px; }
    .event-time { font-size: 10px; color: var(--pages-neutral-5, #a3a3a3); }
    .loading { font-size: 11px; color: var(--pages-neutral-6, #737373); padding: 6px 22px; margin-left: 12px; }
  `;

  override willUpdate(changed: Map<string, unknown>): void {
    if (changed.has('cases')) {
      for (const c of this.cases) {
        if (!this._manualOverrides.has(c.caseId)) {
          if (shouldExpand(c)) {
            this._expandedCases.add(c.caseId);
          } else {
            this._expandedCases.delete(c.caseId);
          }
        }
      }
      this.requestUpdate();
    }
  }

  private _toggleExpand(caseId: string): void {
    this._manualOverrides.add(caseId);
    if (this._expandedCases.has(caseId)) {
      this._expandedCases.delete(caseId);
    } else {
      this._expandedCases.add(caseId);
      this._fetchEvents(caseId);
    }
    this.requestUpdate();
  }

  private async _fetchEvents(caseId: string): Promise<void> {
    if (this._caseEvents.has(caseId) && !this._caseEvents.get(caseId)!.loading) return;
    this._caseEvents.set(caseId, { nodes: [], loading: true });
    this.requestUpdate();
    try {
      const res = await fetch(`${this.endpoint}/${caseId}`);
      if (res.ok) {
        const detail = await res.json();
        const nodes = synthesizeSpineNodes(detail.timeline ?? []);
        this._caseEvents.set(caseId, { nodes, loading: false });
      } else {
        this._caseEvents.set(caseId, { nodes: [], loading: false });
      }
    } catch {
      this._caseEvents.set(caseId, { nodes: [], loading: false });
    }
    this.requestUpdate();
  }

  private _selectCase(caseId: string): void {
    emitPagesEvent(document, 'review:selected', { caseId, nodeType: 'case-header', nodeData: { caseId } });
  }

  private _selectNode(caseId: string, node: SpineNode): void {
    emitPagesEvent(document, 'review:selected', { caseId, nodeType: node.nodeType, nodeData: node.nodeData });
  }

  private _formatTime(ts: string): string {
    if (!ts) return '';
    const diff = Date.now() - new Date(ts).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  }

  override render() {
    return html`
      ${this.cases.map(c => this._renderCase(c))}
    `;
  }

  private _renderCase(c: UnifiedPrRow) {
    const expanded = this._expandedCases.has(c.caseId);
    const events = this._caseEvents.get(c.caseId);
    const border = borderColor(c.stage, c.status);
    const isSelected = c.caseId === this.selectedCaseId;

    if (expanded && !events) {
      this._fetchEvents(c.caseId);
    }

    return html`
      <div>
        <div class="case-row ${isSelected ? 'selected' : ''}"
             style="border-left: 3px solid ${border}"
             @click=${() => { this._toggleExpand(c.caseId); this._selectCase(c.caseId); }}>
          <div class="case-header-content">
            <span class="expand-icon">${expanded ? '▼' : '▶'}</span>
            <div>
              <div class="case-title">PR #${c.prNumber} <span style="font-weight:400;color:var(--pages-neutral-7,#525252)">${c.author}, ${c.repo.split('/').pop()}, ${c.linesChanged} lines</span></div>
              ${!expanded ? html`<div class="case-meta">${statusText(c.stage, c.status)}</div>` : nothing}
            </div>
          </div>
          ${!expanded ? html`<span class="status-indicator" style="background:${border}"></span>` : nothing}
        </div>
        ${expanded ? html`
          ${events?.loading ? html`<div class="loading">Loading timeline...</div>` : nothing}
          ${events && !events.loading && events.nodes.length > 0 ? html`
            <div class="events-panel">
              ${events.nodes.map(n => html`
                <div class="event-node ${n.status}"
                     @click=${(e: Event) => { e.stopPropagation(); this._selectNode(c.caseId, n); }}>
                  <span class="event-label">
                    <span>${n.icon}</span>
                    <span>${n.label}</span>
                  </span>
                  <span class="event-time">${this._formatTime(n.timestamp)}</span>
                </div>
              `)}
            </div>
          ` : nothing}
        ` : nothing}
      </div>
    `;
  }
}
