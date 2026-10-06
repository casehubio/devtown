import { LitElement, html, css, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { reviewTimelineStrategy } from './review-timeline-strategy.js';
import './routing-summary.js';
import './findings-panel.js';
import '@casehubio/blocks-ui-blocks-timeline';

interface ReviewDetailData {
  caseId: string;
  pr: { repo: string; prNumber: number; contributor: string; linesChanged: number; headSha: string };
  timeline: Array<{ timestamp: string; category: string; eventType: string; actor: string; summary: string; metadata: unknown }>;
  capabilities: Array<{ name: string; status: string; outcome: string | null; completedAt: string }>;
  routing: { decisions: Array<{ capability: string; reason: string; confidence: number; bindingName: string }>; featureVector: string | null };
  findings: Record<string, Array<{ severity: string; category: string; filePath: string; message: string; confidence: number; startLine: number | null; endLine: number | null }>>;
}

@customElement('devtown-review-detail')
export class ReviewDetail extends LitElement {
  @property({ type: String, attribute: 'case-id' }) caseId = '';
  @property({ type: String }) endpoint = '';

  @state() private _data: ReviewDetailData | null = null;
  @state() private _loading = false;
  @state() private _error = '';
  @state() private _actionResult = '';

  static override styles = css`
    :host { display: block; height: 100%; overflow-y: auto; padding: 16px; }
    .header { font-size: 18px; font-weight: 600; margin-bottom: 12px; }
    .meta { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; margin-bottom: 16px; font-size: 13px; }
    .meta dt { font-weight: 600; color: var(--pages-neutral-8, #404040); }
    .meta dd { margin: 0; }
    .section-title { font-size: 14px; font-weight: 600; margin: 16px 0 8px; }
    .actions { display: flex; gap: 8px; margin: 12px 0; }
    .actions button {
      padding: 6px 14px; border-radius: 4px; font-size: 13px; font-weight: 500;
      cursor: pointer; border: 1px solid var(--pages-neutral-5, #a3a3a3);
      background: white; color: var(--pages-neutral-9, #171717);
    }
    .actions button:hover { background: var(--pages-neutral-2, #f5f5f5); }
    .actions button.primary {
      background: var(--pages-primary-9, #1d4ed8); color: white;
      border-color: var(--pages-primary-9, #1d4ed8);
    }
    .actions button.primary:hover { background: var(--pages-primary-10, #1e40af); }
    .action-result { font-size: 12px; padding: 6px 10px; margin: 4px 0 8px; background: var(--pages-neutral-2, #f5f5f5); border-radius: 3px; }
    .empty { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--pages-neutral-7, #525252); font-size: 13px; }
    .error { color: var(--pages-danger-9, #dc2626); padding: 16px; }
  `;

  override willUpdate(changed: Map<PropertyKey, unknown>): void {
    if (changed.has('caseId') && this.caseId) {
      this._fetchDetail();
    }
  }

  private async _fetchDetail(): Promise<void> {
    if (!this.caseId) return;
    this._loading = true;
    this._error = '';
    try {
      const res = await fetch(`${this.endpoint}/${this.caseId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this._data = await res.json();
    } catch (err) {
      this._error = `Failed to load: ${err}`;
    } finally {
      this._loading = false;
    }
  }

  private async _doAction(action: string): Promise<void> {
    if (!this._data) return;
    try {
      const res = await fetch(`/api/actions/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          repo: this._data.pr.repo,
          prNumber: this._data.pr.prNumber,
          contributor: this._data.pr.contributor,
          headSha: this._data.pr.headSha,
        }),
      });
      const json = await res.json();
      this._actionResult = `${json.action}: ${json.result}`;
      this._fetchDetail();
    } catch (err) {
      this._actionResult = `Error: ${err}`;
    }
  }

  override render() {
    if (!this.caseId) return html`<div class="empty">Select a review to see details</div>`;
    if (this._loading && !this._data) return html`<div class="empty">Loading...</div>`;
    if (this._error) return html`<div class="error">${this._error}</div>`;
    if (!this._data) return html`<div class="empty">No data</div>`;

    const d = this._data;
    return html`
      <div class="header">PR #${d.pr.prNumber} — ${d.pr.repo}</div>
      <dl class="meta">
        <dt>Author</dt><dd>${d.pr.contributor}</dd>
        <dt>Lines Changed</dt><dd>${d.pr.linesChanged}</dd>
        <dt>Case ID</dt><dd style="font-size:11px">${d.caseId}</dd>
      </dl>

      <devtown-routing-summary .routing=${d.routing}></devtown-routing-summary>
      <devtown-findings-panel .findings=${d.findings}></devtown-findings-panel>

      <div class="actions">
        <button @click=${() => this._doAction('approve')}>Approve</button>
        <button @click=${() => this._doAction('request-changes')}>Request Changes</button>
        <button class="primary" @click=${() => this._doAction('enqueue')}>Add to Merge Queue</button>
      </div>
      ${this._actionResult ? html`<div class="action-result">${this._actionResult}</div>` : nothing}

      <div class="section-title">Event Timeline</div>
      <blocks-timeline
        .strategy=${reviewTimelineStrategy}
        .data=${d.timeline}
        layout="vertical"
      ></blocks-timeline>
    `;
  }
}
